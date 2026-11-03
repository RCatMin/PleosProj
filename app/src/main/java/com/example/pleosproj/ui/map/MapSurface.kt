package com.example.pleosproj.ui.map

import android.content.Context
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// 지도 엔진의 타입과 생명주기는 이 부품 밖으로 내보내지 않는다.
class MapSurface(context: Context, savedState: Bundle?) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var showRoutes = true
    private var destroyed = false
    var isDriverView by mutableStateOf(true)
        private set
    var loadMessage: String? by mutableStateOf("서울 지도를 불러오는 중이에요")
        private set

    init {
        MapLibre.getInstance(context)
        view = MapView(context)
        isDriverView = savedState?.getBoolean(VIEWPOINT_KEY, true) ?: true
        view.onCreate(savedState?.getBundle(STATE_KEY))
        view.addOnDidFailLoadingMapListener { error ->
            if (!destroyed) loadMessage = "지도를 불러오지 못했어요. 네트워크를 확인해 주세요."
            android.util.Log.w("MapSurface", error)
        }
        view.getMapAsync { loadedMap ->
            if (!destroyed) {
                map = loadedMap
                loadedMap.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                }
                if (savedState?.getBundle(STATE_KEY) == null) resetCamera()
                reload()
            }
        }
    }

    fun reload() {
        loadMessage = "서울 지도를 불러오는 중이에요"
        map?.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/positron")) { style ->
            if (!destroyed) {
                localizeLabels(style)
                MapBuildingVisibility.addTo(style)
                addPreviewLayers(style)
                setRoutesVisible(showRoutes)
                loadMessage = null
            }
        }
    }

    fun setRoutesVisible(visible: Boolean) {
        showRoutes = visible
        for (index in MapPreviewData.routes.indices) {
            for (suffix in listOf("outline", "line")) {
                map?.style?.getLayer("route-$index-$suffix")?.setProperties(
                    visibility(if (visible) Property.VISIBLE else Property.NONE),
                )
            }
        }
        updateBuildings()
    }

    fun showDriverView(driverView: Boolean) {
        isDriverView = driverView
        updateBuildings()
        resetCamera()
    }

    private fun updateBuildings() {
        map?.style?.let { style ->
            val routes = if (showRoutes) MapPreviewData.routes.map { coordinates ->
                coordinates.map { (lat, lon) -> Point.fromLngLat(lon, lat) }
            } else emptyList()
            val points = (listOf(MapPreviewData.start, MapPreviewData.destination) +
                MapPreviewData.hazards.map { it.first }).map { (lat, lon) -> Point.fromLngLat(lon, lat) }
            MapBuildingVisibility.update(style, routes, points, isDriverView)
        }
    }

    fun resetCamera() {
        val camera = if (isDriverView) {
            // 예시 경로의 진행 방향을 바라보며 내 위치와 다음 구간을 함께 보여 준다.
            CameraPosition.Builder().target(LatLng(37.5148, 127.0421))
                .zoom(15.8).tilt(45.0).bearing(156.0).build()
        } else {
            CameraPosition.Builder().target(LatLng(37.5155, 127.0490))
                .zoom(14.3).tilt(0.0).bearing(0.0).build()
        }
        map?.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
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

    private fun addPreviewLayers(style: Style) {
        // 대안 경로를 먼저 그려 선택 경로와 표식이 항상 위에 보이도록 한다.
        for (index in MapPreviewData.routes.indices.reversed()) {
            val points = MapPreviewData.routes[index].map { (lat, lon) -> Point.fromLngLat(lon, lat) }
            val source = "route-$index"
            style.addSource(GeoJsonSource(source, Feature.fromGeometry(LineString.fromLngLats(points))))
            style.addLayer(LineLayer("$source-outline", source).withProperties(
                lineColor("#FFFFFF"), lineWidth(if (index == 0) 12f else 10f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ))
            style.addLayer(LineLayer("$source-line", source).withProperties(
                lineColor(if (index == 0) "#7145D6" else "#B6A5D8"),
                lineWidth(if (index == 0) 7f else 5f),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ))
        }
        val hazards = MapPreviewData.hazards.mapIndexed { index, (coordinate, label, color) ->
            Feature.fromGeometry(Point.fromLngLat(coordinate.second, coordinate.first)).apply {
                addStringProperty("label", "${index + 1} $label")
                addStringProperty("color", color)
            }
        }
        style.addSource(GeoJsonSource("hazards", FeatureCollection.fromFeatures(hazards)))
        style.addLayer(CircleLayer("hazard-points", "hazards").withProperties(
            circleRadius(13f), circleColor(Expression.get("color")),
            circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
        ))
        style.addLayer(SymbolLayer("hazard-labels", "hazards").withProperties(
            textFont(arrayOf("Noto Sans Regular")),
            textField(Expression.get("label")), textSize(19f), textColor("#242127"),
            textHaloColor("#FFFFFF"), textHaloWidth(3f),
            textOffset(arrayOf(0f, 1.7f)), textAllowOverlap(true), textIgnorePlacement(true),
        ))
        val locations = listOf(MapPreviewData.start to "내 위치", MapPreviewData.destination to "목적지")
            .map { (coordinate, label) ->
                Feature.fromGeometry(Point.fromLngLat(coordinate.second, coordinate.first)).apply {
                    addStringProperty("label", label)
                }
            }
        style.addSource(GeoJsonSource("locations", FeatureCollection.fromFeatures(locations)))
        style.addLayer(CircleLayer("location-points", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "목적지")).withProperties(
            circleRadius(10f), circleColor("#242127"),
            circleStrokeColor("#FFFFFF"), circleStrokeWidth(4f),
        ))
        style.addImage("current-position-arrow", currentPositionArrow())
        style.addLayer(SymbolLayer("current-position", "locations")
            .withFilter(Expression.eq(Expression.get("label"), "내 위치")).withProperties(
                iconImage("current-position-arrow"), iconSize(0.75f), iconRotate(156f),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                iconAllowOverlap(true), iconIgnorePlacement(true),
            ))
        style.addLayer(SymbolLayer("location-labels", "locations").withProperties(
            textFont(arrayOf("Noto Sans Regular")),
            textField(Expression.get("label")), textSize(18f), textColor("#242127"),
            textHaloColor("#FFFFFF"), textHaloWidth(3f), textOffset(arrayOf(0f, -1.8f)),
            textAllowOverlap(true), textIgnorePlacement(true),
        ))
    }

    private fun currentPositionArrow(): Bitmap {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val arrow = Path().apply {
            moveTo(32f, 5f); lineTo(55f, 55f); lineTo(32f, 44f); lineTo(9f, 55f); close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.strokeJoin = Paint.Join.ROUND
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
    fun onDestroy() {
        destroyed = true
        view.onDestroy()
    }

    private companion object {
        const val STATE_KEY = "pleos-map"
        const val VIEWPOINT_KEY = "pleos-map-driver-view"
    }
}
