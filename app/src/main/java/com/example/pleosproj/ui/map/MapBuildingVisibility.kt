package com.example.pleosproj.ui.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// 45도 시점에서 건물이 안내 요소 앞으로 튀어나오지 않도록 높이와 주변 여백을 함께 제한한다.
internal object MapBuildingVisibility {
    const val LAYER_ID = "navigation-buildings"
    private const val CLEARANCE_METERS = 90.0
    private const val MAX_HEIGHT_METERS = 60.0

    fun addTo(style: Style) {
        // 공개 스타일이 3D 건물을 추가해도 보호 정책을 거치지 않은 건물은 중복 표시하지 않는다.
        style.layers.filterIsInstance<FillExtrusionLayer>().forEach {
            it.setProperties(visibility(Property.NONE))
        }
        style.addLayer(FillExtrusionLayer(LAYER_ID, "openmaptiles")
            .withSourceLayer("building")
            .withProperties(
                fillExtrusionColor("#B9B5B0"), fillExtrusionOpacity(0f),
                fillExtrusionBase(0f), fillExtrusionVerticalGradient(true),
            ).apply {
                minZoom = MapRenderPolicy.BUILDINGS_MIN_ZOOM.toFloat()
                fillExtrusionOpacityTransition = TransitionOptions(0, 0)
            })
    }

    fun update(style: Style, routes: List<List<Point>>, protectedPoints: List<Point>) {
        val geometry = FeatureCollection.fromFeatures(
            routes.filter { it.size >= 2 }.map { Feature.fromGeometry(LineString.fromLngLats(it)) } +
                protectedPoints.map { Feature.fromGeometry(it) },
        )
        val originalHeight = min(
            literal(MAX_HEIGHT_METERS),
            max(literal(0), toNumber(coalesce(get("render_height"), get("height"), literal(8)))),
        )
        val height = if (geometry.features().isNullOrEmpty()) originalHeight else switchCase(
            lt(distance(geometry), CLEARANCE_METERS), literal(0), originalHeight,
        )
        style.getLayerAs<FillExtrusionLayer>(LAYER_ID)?.setProperties(
            fillExtrusionHeight(height),
        )
    }

    fun show(style: Style, visible: Boolean) {
        // 레이아웃 visibility를 토글하면 보호 거리 표현식을 다시 계산하므로 paint만 바꾼다.
        // opacity 0인 돌출 레이어는 렌더러가 그리기를 생략한다.
        style.getLayerAs<FillExtrusionLayer>(LAYER_ID)?.setProperties(
            fillExtrusionOpacity(if (visible) 0.45f else 0f),
        )
    }
}
