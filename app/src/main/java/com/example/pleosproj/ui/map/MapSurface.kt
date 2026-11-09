package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.theme.NavigationBlueHex
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
import com.example.pleosproj.ui.state.SegmentKind
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
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

enum class MapViewpoint(val label: String) {
    OVERHEAD("위에서 보기"), NORTH_UP("북쪽이 위"), DRIVER("운전석에서 보기")
}
enum class MapAppearance(val label: String, val uri: String) {
    SIMPLE("기본 지도", "https://tiles.openfreemap.org/styles/positron"),
    DETAILED("상세 지도", "https://tiles.openfreemap.org/styles/liberty")
}

// SDK 타입은 ui/map/ 안에서만 쓰고, 화면에서는 공유 상태 모델만 넘긴다.
class MapSurface(context: Context, savedState: Bundle?) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var guidance: GuidanceState? = null
    private var comparison: RouteCompareState? = null
    private var destroyed = false
    private var resumed = false
    private var continuousMotion = false
    private var cameraGesture = false
    private var followingPosition = true
    private var motion: MapMotion? = null
    private var renderedGuidance: GuidanceState? = null
    private var renderedComparison: RouteCompareState? = null
    private var renderedBuildingsVisible: Boolean? = null
    private var cameraPending = savedState?.getBundle(STATE_KEY) == null
    private var insets = intArrayOf(0, 0, 0, 0)
    var viewpoint by mutableStateOf(MapViewpoint.entries.getOrElse(savedState?.getInt("map-viewpoint", 0) ?: 0) { MapViewpoint.DRIVER })
        private set
    val isDriverView get() = viewpoint != MapViewpoint.NORTH_UP
    var appearance by mutableStateOf(MapAppearance.entries.getOrElse(savedState?.getInt("map-appearance", 0) ?: 0) { MapAppearance.SIMPLE })
        private set
    var night by mutableStateOf(savedState?.getBoolean("map-night", false) ?: false)
        private set
    private var zoomAdjustment = 0.0
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
                view.setMaximumFps(MapRenderPolicy.MAX_FPS)
                loaded.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                loaded.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        cameraGesture = true
                        // 지도를 둘러보는 동안 자동 추적이 손동작을 덮어쓰지 않는다.
                        followingPosition = false
                        updateBuildingVisibility()
                    }
                }
                loaded.addOnCameraMoveListener { updateBuildingVisibility() }
                loaded.addOnCameraIdleListener {
                    cameraGesture = false
                    updateBuildingVisibility()
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
        if (previous == null || previous.mode != state.mode || (followingPosition && positionChanged && !continuous) ||
            (!isDriverView && previous.routeLine != state.routeLine)) cameraPending = true
        map?.style?.let(::updateSources)
        if (cameraPending && map != null) resetCamera()
    }

    internal fun renderMotion(frame: MapMotion) {
        if (!resumed || !continuousMotion || guidance == null || motion == frame) return
        motion = frame
        map?.style?.let { updatePosition(it, frame) }
        if (followingPosition && !cameraGesture) followPosition(frame)
    }

    fun render(state: RouteCompareState) {
        continuousMotion = false
        followingPosition = false
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
        map?.setStyle(Style.Builder().fromUri(appearance.uri)) { style ->
            if (!destroyed) {
                renderedGuidance = null
                renderedComparison = null
                renderedBuildingsVisible = null
                localizeLabels(style)
                if (night) applyNightColors(style)
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
        showViewpoint(if (driverView) MapViewpoint.DRIVER else MapViewpoint.OVERHEAD)
    }

    fun showViewpoint(value: MapViewpoint) {
        // 비교 중에는 시점 변경을 막고 안내에서 선택했던 시점은 보존한다.
        if (comparison != null) return
        viewpoint = value
        zoomAdjustment = 0.0
        resetCamera()
    }

    fun zoomBy(amount: Double) {
        val loaded = map ?: return
        zoomAdjustment = (zoomAdjustment + amount).coerceIn(-5.0, 3.0)
        loaded.animateCamera(CameraUpdateFactory.zoomBy(amount), 220)
    }

    fun showAppearance(value: MapAppearance) {
        if (appearance == value) return
        appearance = value
        reload()
    }

    fun toggleNight() { night = !night; reload() }

    fun fitRoute() {
        if (comparison != null) { resetCamera(); return }
        val loaded = map ?: return
        followingPosition = false
        val points = comparison?.candidates?.flatMap { it.routeLine }
            ?: guidance?.let { it.routeLine.orEmpty() + it.myPosition }.orEmpty()
        fitCoordinates(loaded, points)
    }

    private fun applyNightColors(style: Style) {
        // 배경 지도만 바꾼다. 경로와 위험 색은 이후에 더해 낮·밤 모두 같은 의미를 유지한다.
        style.layers.forEach { layer ->
            when (layer) {
                is BackgroundLayer -> layer.setProperties(backgroundColor("#161D29"))
                is FillLayer -> layer.setProperties(fillColor(if (layer.id.contains("water")) "#172D40" else "#202A39"))
                is LineLayer -> layer.setProperties(lineColor(if (layer.id.contains("motorway")) "#65738B" else "#3B485E"))
                is SymbolLayer -> layer.setProperties(textColor("#C3CCDB"), textHaloColor("#192231"))
            }
        }
    }

    private fun updateSources(style: Style) {
        val empty = FeatureCollection.fromFeatures(emptyArray<Feature>())
        comparison?.let { state ->
            if (renderedComparison == state) return
            val data = RouteCompareMapData(state)
            style.getSourceAs<GeoJsonSource>("guidance-route")?.setGeoJson(empty)
            style.getSourceAs<GeoJsonSource>("compare-routes")?.setGeoJson(data.routeFeatures)
            style.getSourceAs<GeoJsonSource>("hazards")?.setGeoJson(data.hazardFeatures)
            style.getSourceAs<GeoJsonSource>("locations")?.setGeoJson(data.locationFeatures)
            MapBuildingVisibility.update(style, data.routes, emptyList())
            updateBuildingVisibility()
            renderedComparison = state
            renderedGuidance = null
            return
        }
        val state = guidance ?: return
        val previous = renderedGuidance
        val geometryChanged = previous == null || previous.routeLine != state.routeLine || previous.hazards != state.hazards ||
            previous.progress.segments.filter { it.kind == SegmentKind.HAZARD } !=
            state.progress.segments.filter { it.kind == SegmentKind.HAZARD }
        // 경로·위험 구간은 데이터가 바뀔 때만 갱신하고, 매 프레임의 위치 갱신과 분리한다.
        if (geometryChanged) {
            val data = GuidanceMapData(state)
            if (previous == null) {
                style.getSourceAs<GeoJsonSource>("compare-routes")?.setGeoJson(empty)
            }
            if (previous == null || previous.routeLine != state.routeLine)
                style.getSourceAs<GeoJsonSource>("guidance-route")?.setGeoJson(data.routeFeatures)
            style.getSourceAs<GeoJsonSource>("hazards")?.setGeoJson(data.hazardFeatures)
        }
        if (geometryChanged ||
            (state.routeLine.isNullOrEmpty() && previous?.myPosition != state.myPosition)) {
            val data = GuidanceMapData(state)
            // 경로가 있으면 내 위치 주변도 이미 보호되므로 건물 표현식을 매 프레임 바꾸지 않는다.
            val protected = if (data.routes.isNotEmpty()) data.protectedPoints.drop(1) else data.protectedPoints
            MapBuildingVisibility.update(style, data.routes, protected)
        }
        updateBuildingVisibility()
        val position = motion ?: MapMotion(state.myPosition, state.heading)
        if (previous == null || previous.routeLine != state.routeLine || !continuousMotion) updatePosition(style, position)
        renderedGuidance = state
        renderedComparison = null
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
                .zoom((when (viewpoint) {
                    MapViewpoint.OVERHEAD -> 15.8
                    MapViewpoint.NORTH_UP -> 15.5
                    MapViewpoint.DRIVER -> 17.0
                } + zoomAdjustment).coerceIn(10.0, 20.0))
                .tilt(when (viewpoint) { MapViewpoint.OVERHEAD -> 30.0; MapViewpoint.NORTH_UP -> 0.0; MapViewpoint.DRIVER -> 55.0 })
                .bearing(if (viewpoint == MapViewpoint.NORTH_UP) 0.0 else frame.heading)
                .padding(insets.map { it.toDouble() }.toDoubleArray()).build(),
        ))
    }

    private fun updateBuildingVisibility() {
        val loadedMap = map ?: return
        val style = loadedMap.style ?: return
        val visible = MapRenderPolicy.showBuildings(
            guidance = guidance != null,
            driverView = isDriverView,
            zoom = loadedMap.cameraPosition.zoom,
            gesturing = cameraGesture,
        )
        // 카메라는 매 프레임 움직여도 레이어 속성은 경계를 넘을 때만 바꾼다.
        if (renderedBuildingsVisible != visible) {
            MapBuildingVisibility.show(style, visible)
            renderedBuildingsVisible = visible
        }
    }

    fun resetCamera() {
        val loadedMap = map ?: return
        comparison?.let { state ->
            fitCoordinates(loadedMap, state.candidates.flatMap { it.routeLine } +
                state.candidates.flatMap { it.hazards }.map { it.position })
            val camera = loadedMap.cameraPosition
            // 안내 시점과 별개로 비교는 항상 북쪽이 위인 평면 전체 경로로 표시한다.
            loadedMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera)
                .tilt(0.0).bearing(0.0).build()))
            cameraPending = false
            return
        }
        val state = guidance ?: return
        followingPosition = true
        followPosition(motion ?: MapMotion(state.myPosition, state.heading))
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
        style.addSource(GeoJsonSource("compare-routes", FeatureCollection.fromFeatures(emptyArray<Feature>())))
        val excluded = Expression.eq(Expression.get("excluded"), true)
        val selected = Expression.eq(Expression.get("selected"), true)
        val alternative = Expression.all(Expression.eq(Expression.get("selected"), false),
            Expression.eq(Expression.get("excluded"), false))
        style.addLayer(LineLayer("compare-outline", "compare-routes").withProperties(
            lineColor("#FFFFFF"), lineWidth(14f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("compare-alternative", "compare-routes").withFilter(alternative).withProperties(
            lineColor("#A7C3FA"), lineWidth(7f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("compare-excluded", "compare-routes").withFilter(excluded).withProperties(
            lineColor("#9C2864"), lineWidth(5f), lineDasharray(arrayOf(1.5f, 1.5f)),
        ))
        // 선택선을 마지막에 그려 겹치는 출발·도착 구간에서도 선택이 보이게 한다.
        style.addLayer(LineLayer("compare-selected-outline", "compare-routes").withFilter(selected).withProperties(
            lineColor("#FFFFFF"), lineWidth(15f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("compare-selected", "compare-routes").withFilter(selected).withProperties(
            lineColor(NavigationBlueHex), lineWidth(9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
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
            lineColor(NavigationBlueHex), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("hazard-outline", "hazards").withProperties(
            lineColor("#FFFFFF"), lineWidth(16f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer("hazard-line", "hazards").withProperties(
            lineColor(Expression.get("color")), lineWidth(11f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(CircleLayer("destination", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "목적지")).withProperties(
                circleRadius(10f), circleColor("#242127"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(4f),
            ))
        style.addLayer(CircleLayer("departure", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "출발")).withProperties(
                circleRadius(10f), circleColor("#FFFFFF"), circleStrokeColor(NavigationBlueHex), circleStrokeWidth(4f),
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
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val blue = android.graphics.Color.parseColor(NavigationBlueHex)
        paint.color = (blue and 0x00FFFFFF) or (50 shl 24)
        canvas.drawCircle(40f, 40f, 39f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(40f, 40f, 28f, paint)
        paint.color = blue
        canvas.drawCircle(40f, 40f, 24f, paint)
        val arrow = Path().apply {
            moveTo(40f, 24f)
            lineTo(52f, 53f)
            lineTo(40f, 47f)
            lineTo(28f, 53f)
            close()
        }
        paint.color = android.graphics.Color.WHITE
        canvas.drawPath(arrow, paint)
        return bitmap
    }

    fun onStart() = view.onStart()
    fun onResume() { resumed = true; view.onResume() }
    fun onPause() { resumed = false; view.onPause() }
    fun onStop() = view.onStop()
    fun onLowMemory() = view.onLowMemory()
    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("map-viewpoint", viewpoint.ordinal)
        outState.putInt("map-appearance", appearance.ordinal)
        outState.putBoolean("map-night", night)
        outState.putBundle(STATE_KEY, Bundle().also(view::onSaveInstanceState))
    }
    fun onDestroy() { destroyed = true; view.onDestroy() }

    private companion object {
        const val STATE_KEY = "pleos-map"
    }
}
