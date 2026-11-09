package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.hazardColor
import com.example.pleosproj.ui.state.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.*

internal data class HazardRouteLine(val kind: HazardKind, val points: List<GeoPoint>)

// 대설은 기본 경로보다 진한 파랑으로 구분한다. 범례도 이 색을 함께 쓴다.
internal fun hazardLineColor(kind: HazardKind): Int =
    if (kind == HazardKind.HEAVY_SNOW) 0xFF1653B5.toInt() else hazardColor(kind)

internal fun List<HazardRouteLine>.toFeatures(): FeatureCollection = FeatureCollection.fromFeatures(map { line ->
    Feature.fromGeometry(LineString.fromLngLats(line.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
        addStringProperty("color", "#%06X".format(hazardLineColor(line.kind) and 0xFFFFFF))
    }
})

internal fun hazardRouteLines(
    route: List<GeoPoint>,
    hazards: List<Hazard>,
    segments: List<ProgressSegment> = emptyList(),
    hazardsOnRoute: Boolean = false,
): List<HazardRouteLine> {
    val path = RoutePath.create(route) ?: return emptyList()
    val ranges = segments.filter {
        it.kind == SegmentKind.HAZARD && it.hazardKind != null &&
            it.startRatio.isFinite() && it.endRatio.isFinite() &&
            it.endRatio.coerceIn(0.0, 1.0) > it.startRatio.coerceIn(0.0, 1.0)
    }
    return buildList {
        ranges.forEach { range ->
            add(HazardRouteLine(range.hazardKind!!, path.clip(
                range.startRatio.coerceIn(0.0, 1.0) * path.length,
                range.endRatio.coerceIn(0.0, 1.0) * path.length,
            )))
        }
        hazards.distinctBy { it.id }.forEach { hazard ->
            // 비교 후보와 유효한 경로 비율은 연결부가 이미 경로 영향 여부를 판단한 값이다.
            val assigned = hazardsOnRoute || hazard.ratioOnRoute?.let { it.isFinite() && it in 0.0..1.0 } == true
            val along = path.project(hazard.position, assigned) ?: return@forEach
            if (ranges.none { it.hazardKind == hazard.kind &&
                    along / path.length in it.startRatio.coerceIn(0.0, 1.0)..it.endRatio.coerceIn(0.0, 1.0) }) {
                // 지점만 넘어오면 실제 위험 범위를 추정하지 않고 지점 앞뒤 80m를 강조한다.
                // 비교 경로마다 좌표를 투영한다. 다른 경로 기준의 ratioOnRoute는 위치 계산에 사용하지 않는다.
                add(HazardRouteLine(hazard.kind, path.clip(max(0.0, along - 80.0), min(path.length, along + 80.0))))
            }
        }
    }.sortedBy { if (it.kind == HazardKind.CLOSURE || it.kind == HazardKind.ACCIDENT) 1 else 0 }
}

private class RoutePath(private val points: List<GeoPoint>, private val distances: List<Double>) {
    val length = distances.last()

    fun clip(start: Double, end: Double): List<GeoPoint> = buildList {
        add(at(start))
        points.indices.filter { distances[it] > start && distances[it] < end }.forEach { add(points[it]) }
        add(at(end))
    }

    private fun at(distance: Double): GeoPoint {
        val end = distances.indexOfFirst { it >= distance }.coerceAtLeast(1)
        val start = end - 1
        val ratio = (distance - distances[start]) / (distances[end] - distances[start])
        return GeoPoint(points[start].lat + (points[end].lat - points[start].lat) * ratio,
            points[start].lng + (points[end].lng - points[start].lng) * ratio)
    }

    fun project(point: GeoPoint, assigned: Boolean): Double? {
        if (!point.valid()) return null
        val xScale = 111_195.0 * cos(Math.toRadians(point.lat))
        var nearest = Double.POSITIVE_INFINITY
        var along = 0.0
        for (i in 1 until points.size) {
            val ax = (points[i - 1].lng - point.lng) * xScale
            val ay = (points[i - 1].lat - point.lat) * 111_195.0
            val dx = (points[i].lng - points[i - 1].lng) * xScale
            val dy = (points[i].lat - points[i - 1].lat) * 111_195.0
            val t = (-(ax * dx + ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            val separation = hypot(ax + t * dx, ay + t * dy)
            if (separation < nearest) {
                nearest = separation
                along = distances[i - 1] + (distances[i] - distances[i - 1]) * t
            }
        }
        // 연결부의 경로 배정을 우선한다. 미배정 지점만 기본 주변 거리 밖에서 제외한다.
        return along.takeIf { assigned || nearest <= 150.0 }
    }

    companion object {
        fun create(route: List<GeoPoint>): RoutePath? {
            if (route.any { !it.valid() }) return null
            val points = route.fold(mutableListOf<GeoPoint>()) { result, p ->
                if (result.lastOrNull() != p) result.add(p)
                result
            }
            if (points.size < 2) return null
            val distances = mutableListOf(0.0)
            points.zipWithNext().forEach { (a, b) ->
                val lat = Math.toRadians(b.lat - a.lat)
                val lng = Math.toRadians(b.lng - a.lng)
                val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(lng / 2).pow(2)
                distances.add(distances.last() + 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0))))
            }
            return RoutePath(points, distances).takeIf { it.length > 0.0 }
        }
    }
}

private fun GeoPoint.valid() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
