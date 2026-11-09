package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.state.GuidanceState
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// 상태가 바뀔 때 빈 경로·빈 위험 목록도 새로 만들어 이전 상태의 표식이 남지 않게 한다.
internal class GuidanceMapData(state: GuidanceState) {
    private val route = state.routeLine.orEmpty().map { Point.fromLngLat(it.lng, it.lat) }
    val routes: List<List<Point>> = if (route.size >= 2) listOf(route) else emptyList()
    val routeFeatures: FeatureCollection = FeatureCollection.fromFeatures(
        routes.map { Feature.fromGeometry(LineString.fromLngLats(it)) },
    )
    private val hazardLines = hazardRouteLines(state.routeLine.orEmpty(), state.hazards, state.progress.segments)
    val hazardKinds = hazardLines.map { it.kind }.distinct()
    val hazardFeatures = hazardLines.toFeatures()
    private val current = Point.fromLngLat(state.myPosition.lng, state.myPosition.lat)
    val locationFeatures: FeatureCollection = FeatureCollection.fromFeatures(buildList {
        add(Feature.fromGeometry(current).apply { addStringProperty("label", "내 위치") })
        // 실제 주행 상태에는 목적지 좌표가 없으므로 경로선이 있을 때만 끝점을 목적지로 그린다.
        if (route.isNotEmpty()) add(Feature.fromGeometry(route.last()).apply { addStringProperty("label", "목적지") })
    })
    val protectedPoints: List<Point> = listOf(current) +
        state.hazards.map { Point.fromLngLat(it.position.lng, it.position.lat) } +
        if (route.isNotEmpty()) listOf(route.last()) else emptyList()
}
