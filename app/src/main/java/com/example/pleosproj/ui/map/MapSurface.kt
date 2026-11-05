package com.example.pleosproj.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.RouteCompareState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// SDK 타입은 ui/map/ 안에서만 쓰고, 화면에서는 공유 상태 모델만 넘긴다.
class MapSurface(context: Context, savedState: Bundle?) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var guidance: GuidanceState? = null
    private var comparison: RouteCompareState? = null
    private var destroyed = false
    private var resumed = false
    private var continuousMotion = false
    private var motion: MapMotion? = null
    private var renderedGuidance: GuidanceState? = null
    private var renderedComparison: RouteCompareState? = null
    private var renderedDriverView: Boolean? = null
    private var cameraPending = savedState?.getBundle(STATE_KEY) == null
    private var insets = intArrayOf(0, 0, 0, 0)
    var isDriverView by mutableStateOf(savedState?.getBoolean(VIEWPOINT_KEY, true) ?: true)
        private set
    var loadMessage: String? by mutableStateOf("지도를 불러오는 중이에요")
        private set

    init {
        MapLibre.getInstance(context)
        view = MapView(context)
        view.onCreate(savedState?.getBundle(STATE_KEY))
        view.addOnDidFailLoadingMapListener { error ->
            if (!destroyed) loadMessage = "지도를 불러오지 못했어요. 네트워크를 확인해 주세요."
            android.util.Log.w("MapSurface", error)
        }
        view.getMapAsync { loaded ->
            if (!destroyed) {
                map = loaded
                loaded.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                applyInsets()
                reload()
            }
        }
    }

    fun render(state: GuidanceState, continuous: Boolean = false) {
        val previous = guidance
        comparison = null
        guidance = state
        continuousMotion = continuous
        if (!continuous || previous?.routeLine != state.routeLine ||
            state.progress.currentRatio < (previous?.progress?.currentRatio ?: 0.0)) motion = null
        val positionChanged = previous?.myPosition != state.myPosition || previous?.heading != state.heading
        if (previous == null || previous.mode != state.mode || (isDriverView && positionChanged && !continuous) ||
            (!isDriverView && previous.routeLine != state.routeLine)) cameraPending = true
        map?.style?.let(::updateSources)
        if (cameraPending && map != null) resetCamera()
    }

    internal fun renderMotion(frame: MapMotion) {
        if (!resumed || !continuousMotion || guidance == null || motion == frame) return
        motion = frame
        map?.style?.let { updatePosition(it, frame) }
        if (isDriverView) followPosition(frame)
    }

    fun render(state: RouteCompareState) {
        continuousMotion = false
        motion = null
        if (comparison?.candidates != state.candidates) cameraPending = true
        guidance = null
        comparison = state
        map?.style?.let(::updateSources)
        if (cameraPending && map != null) resetCamera()
    }

    fun setViewportInsets(left: Int, top: Int, right: Int, bottom: Int) {
        val next = intArrayOf(left, top, right, bottom)
        if (!insets.contentEquals(next)) {
            insets = next
            applyInsets()
            resetCamera()
        }
    }

    private fun applyInsets() {
        map?.moveCamera(CameraUpdateFactory.paddingTo(insets.map { it.toDouble() }.toDoubleArray()))
    }

    fun reload() {
        loadMessage = "지도를 불러오는 중이에요"
        map?.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/positron")) { style ->
            if (!destroyed) {
                renderedGuidance = null
                renderedComparison = null
                renderedDriverView = null
                localizeLabels(style)
                MapBuildingVisibility.addTo(style)
                addComparisonLayers(style)
                addGuidanceLayers(style)
                updateSources(style)
                if (cameraPending) resetCamera()
                loadMessage = null
            }
        }
    }

    fun showDriverView(driverView: Boolean) {
        isDriverView = driverView
        map?.style?.let(::updateSources)
        resetCamera()
    }

    private fun updateSources(style: Style) {
        val empty = FeatureCollection.fromFeatures(emptyArray<Feature>())
        comparison?.let { state ->
            if (renderedComparison == state) return
            val data = RouteCompareMapData(state)
            style.getSourceAs<GeoJsonSource>("guidance-route")?.setGeoJson(empty)
            style.getSourceAs<GeoJsonSource>("compare-routes")?.setGeoJson(data.routeFeatures)
            style.getSourceAs<GeoJsonSource>("compare-labels")?.setGeoJson(data.labelFeatures)
            style.getSourceAs<GeoJsonSource>("hazards")?.setGeoJson(data.hazardFeatures)
            style.getSourceAs<GeoJsonSource>("locations")?.setGeoJson(data.locationFeatures)
            MapBuildingVisibility.update(style, data.routes, emptyList(), false)
            renderedComparison = state
            renderedGuidance = null
            renderedDriverView = null
            return
        }
        val state = guidance ?: return
        val previous = renderedGuidance
        val geometryChanged = previous == null || previous.routeLine != state.routeLine || previous.hazards != state.hazards
        // 경로·위험 지점은 데이터가 바뀔 때만 갱신하고, 매 프레임의 위치 갱신과 분리한다.
        if (geometryChanged) {
            val data = GuidanceMapData(state)
            if (previous == null) {
                style.getSourceAs<GeoJsonSource>("compare-routes")?.setGeoJson(empty)
                style.getSourceAs<GeoJsonSource>("compare-labels")?.setGeoJson(empty)
            }
            if (previous == null || previous.routeLine != state.routeLine)
                style.getSourceAs<GeoJsonSource>("guidance-route")?.setGeoJson(data.routeFeatures)
            if (previous == null || previous.hazards != state.hazards)
                style.getSourceAs<GeoJsonSource>("hazards")?.setGeoJson(data.hazardFeatures)
        }
        if (geometryChanged || renderedDriverView != isDriverView ||
            (state.routeLine.isNullOrEmpty() && previous?.myPosition != state.myPosition)) {
            val data = GuidanceMapData(state)
            // 경로가 있으면 내 위치 주변도 이미 보호되므로 건물 표현식을 매 프레임 바꾸지 않는다.
            val protected = if (data.routes.isNotEmpty()) data.protectedPoints.drop(1) else data.protectedPoints
            MapBuildingVisibility.update(style, data.routes, protected, isDriverView)
        }
        val position = motion ?: MapMotion(state.myPosition, state.heading)
        if (previous == null || previous.routeLine != state.routeLine || !continuousMotion) updatePosition(style, position)
        renderedGuidance = state
        renderedComparison = null
        renderedDriverView = isDriverView
    }

    private fun updatePosition(style: Style, frame: MapMotion) {
        val points = buildList {
            add(Feature.fromGeometry(Point.fromLngLat(frame.position.lng, frame.position.lat)).apply {
                addStringProperty("label", "내 위치")
            })
            guidance?.routeLine?.lastOrNull()?.let { point ->
                add(Feature.fromGeometry(Point.fromLngLat(point.lng, point.lat)).apply {
                    addStringProperty("label", "목적지")
                })
            }
        }
        style.getSourceAs<GeoJsonSource>("locations")?.setGeoJson(FeatureCollection.fromFeatures(points))
        style.getLayer("current-position")?.setProperties(iconRotate(frame.heading.toFloat()))
    }

    private fun followPosition(frame: MapMotion) {
        map?.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(frame.position.lat, frame.position.lng))
                .zoom(15.8).tilt(45.0).bearing(frame.heading)
                .padding(insets.map { it.toDouble() }.toDoubleArray()).build(),
        ))
    }

    fun resetCamera() {
        val loadedMap = map ?: return
        comparison?.let { state ->
            fitCoordinates(loadedMap, state.candidates.flatMap { it.routeLine } +
                state.candidates.flatMap { it.hazards }.map { it.position })
            cameraPending = false
            return
        }
        val state = guidance ?: return
        if (isDriverView) {
            followPosition(motion ?: MapMotion(state.myPosition, state.heading))
        } else {
            fitCoordinates(loadedMap, state.routeLine.orEmpty() + state.hazards.map { it.position } + state.myPosition)
        }
        cameraPending = false
    }

    private fun fitCoordinates(loadedMap: MapLibreMap, coordinates: List<GeoPoint>) {
        if (coordinates.distinct().size >= 2) {
            val bounds = LatLngBounds.Builder().includes(coordinates.map { LatLng(it.lat, it.lng) }).build()
            loadedMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0.0, 0.0,
                insets[0] + 48, insets[1] + 48, insets[2] + 48, insets[3] + 48))
        } else if (coordinates.isNotEmpty()) {
            val point = coordinates.first()
            loadedMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(point.lat, point.lng)).zoom(15.0)
                    .tilt(0.0).bearing(0.0).padding(insets.map { it.toDouble() }.toDoubleArray()).build(),
            ))
        }
    }

    private fun addComparisonLayers(style: Style) {
        for (source in listOf("compare-routes", "compare-labels")) {
            style.addSource(GeoJsonSource(source, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        }
        val excluded = Expression.eq(Expression.get("excluded"), true)
        val selected = Expression.eq(Expression.get("selected"), true)
        val alternative = Expression.all(Expression.eq(Expression.get("selected"), false),
            Expression.eq(Expression.get("excluded"), false))
        style.addLayer(LineLayer("compare-outline", "compare-routes").withProperties(
            lineColor("#FFFFFF"), lineWidth(14f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("compare-alternative", "compare-routes").withFilter(alternative).withProperties(
            lineColor("#B5A6D4"), lineWidth(7f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("compare-excluded", "compare-routes").withFilter(excluded).withProperties(
            lineColor("#9C2864"), lineWidth(5f), lineDasharray(arrayOf(1.5f, 1.5f)),
        ))
        // 선택선을 마지막에 그려 겹치는 출발·도착 구간에서도 선택이 보이게 한다.
        style.addLayer(LineLayer("compare-selected-outline", "compare-routes").withFilter(selected).withProperties(
            lineColor("#FFFFFF"), lineWidth(15f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("compare-selected", "compare-routes").withFilter(selected).withProperties(
            lineColor("#7145D6"), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(SymbolLayer("compare-route-labels", "compare-labels").withProperties(
            textFont(arrayOf("Noto Sans Regular")), textField(Expression.get("label")), textSize(22f),
            textColor("#40344F"), textHaloColor("#FFFFFF"), textHaloWidth(4f),
            textOffset(arrayOf(0f, -2.2f)), textAllowOverlap(false),
        ))
    }

    private fun localizeLabels(style: Style) {
        style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
            val field = layer.textField
            if (field.isExpression && field.expression.toString().contains("name")) {
                layer.setProperties(textField(Expression.coalesce(
                    Expression.get("name:ko"), Expression.get("name"), Expression.get("name:en"),
                )))
            }
        }
    }

    private fun addGuidanceLayers(style: Style) {
        for (source in listOf("guidance-route", "hazards", "locations")) {
            style.addSource(GeoJsonSource(source, FeatureCollection.fromFeatures(emptyArray<Feature>())))
        }
        style.addLayer(LineLayer("route-outline", "guidance-route").withProperties(
            lineColor("#FFFFFF"), lineWidth(14f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("route-line", "guidance-route").withProperties(
            lineColor("#7145D6"), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(CircleLayer("hazard-points", "hazards").withProperties(
            circleRadius(15f), circleColor(Expression.get("color")), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
        ))
        style.addLayer(SymbolLayer("hazard-labels", "hazards").withProperties(
            textFont(arrayOf("Noto Sans Regular")), textField(Expression.get("label")), textSize(21f),
            textColor("#242127"), textHaloColor("#FFFFFF"), textHaloWidth(3f),
            textOffset(arrayOf(0f, 1.7f)), textAllowOverlap(true), textIgnorePlacement(true),
        ))
        style.addLayer(CircleLayer("destination", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "목적지")).withProperties(
                circleRadius(10f), circleColor("#242127"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(4f),
            ))
        style.addLayer(CircleLayer("departure", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "출발")).withProperties(
                circleRadius(10f), circleColor("#FFFFFF"), circleStrokeColor("#7145D6"), circleStrokeWidth(4f),
            ))
        style.addLayer(SymbolLayer("endpoint-labels", "locations")
            .withFilter(Expression.neq(Expression.get("label"), "내 위치")).withProperties(
                textFont(arrayOf("Noto Sans Regular")), textField(Expression.get("label")), textSize(20f),
                textColor("#242127"), textHaloColor("#FFFFFF"), textHaloWidth(3f),
                textOffset(arrayOf(0f, 1.2f)),
            ))
        style.addImage("current-position-arrow", currentPositionArrow())
        style.addLayer(SymbolLayer("current-position", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "내 위치")).withProperties(
                iconImage("current-position-arrow"), iconSize(0.9f), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT), iconAllowOverlap(true), iconIgnorePlacement(true),
            ))
    }

    private fun currentPositionArrow(): Bitmap {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val arrow = Path().apply {
            moveTo(32f, 5f)
            lineTo(55f, 55f)
            lineTo(32f, 44f)
            lineTo(9f, 55f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(arrow, paint)
        paint.color = android.graphics.Color.parseColor("#7145D6")
        paint.style = Paint.Style.FILL
        canvas.drawPath(arrow, paint)
        return bitmap
    }

    fun onStart() = view.onStart()
    fun onResume() { resumed = true; view.onResume() }
    fun onPause() { resumed = false; view.onPause() }
    fun onStop() = view.onStop()
    fun onLowMemory() = view.onLowMemory()
    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(VIEWPOINT_KEY, isDriverView)
        outState.putBundle(STATE_KEY, Bundle().also(view::onSaveInstanceState))
    }
    fun onDestroy() { destroyed = true; view.onDestroy() }

    private companion object {
        const val STATE_KEY = "pleos-map"
        const val VIEWPOINT_KEY = "pleos-map-driver-view"
    }
}
