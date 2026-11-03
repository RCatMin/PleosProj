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

// SDK 타입은 ui/map/ 안에서만 쓰고, 화면에서는 공유 상태 모델만 넘긴다.
class MapSurface(context: Context, savedState: Bundle?) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var guidance: GuidanceState? = null
    private var destroyed = false
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

    fun render(state: GuidanceState) {
        val previous = guidance
        guidance = state
        val positionChanged = previous?.myPosition != state.myPosition || previous?.heading != state.heading
        if (previous == null || previous.mode != state.mode || (isDriverView && positionChanged) ||
            (!isDriverView && previous.routeLine != state.routeLine)) cameraPending = true
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
                localizeLabels(style)
                MapBuildingVisibility.addTo(style)
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
        val state = guidance ?: return
        val data = GuidanceMapData(state)
        style.getSourceAs<GeoJsonSource>("guidance-route")?.setGeoJson(data.routeFeatures)
        style.getSourceAs<GeoJsonSource>("hazards")?.setGeoJson(data.hazardFeatures)
        style.getSourceAs<GeoJsonSource>("locations")?.setGeoJson(data.locationFeatures)
        style.getLayer("current-position")?.setProperties(iconRotate(state.heading.toFloat()))
        MapBuildingVisibility.update(style, data.routes, data.protectedPoints, isDriverView)
    }

    fun resetCamera() {
        val state = guidance ?: return
        val loadedMap = map ?: return
        if (isDriverView) {
            loadedMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(state.myPosition.lat, state.myPosition.lng))
                    .zoom(15.8).tilt(45.0).bearing(state.heading)
                    .padding(insets.map { it.toDouble() }.toDoubleArray()).build(),
            ))
        } else {
            val coordinates = state.routeLine.orEmpty() + state.hazards.map { it.position } + state.myPosition
            if (coordinates.distinct().size >= 2) {
                val bounds = LatLngBounds.Builder().includes(coordinates.map { LatLng(it.lat, it.lng) }).build()
                loadedMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0.0, 0.0,
                    insets[0] + 48, insets[1] + 48, insets[2] + 48, insets[3] + 48))
            } else {
                loadedMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(state.myPosition.lat, state.myPosition.lng))
                        .zoom(15.0).tilt(0.0).bearing(0.0).build(),
                ))
            }
        }
        cameraPending = false
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
    fun onResume() = view.onResume()
    fun onPause() = view.onPause()
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
