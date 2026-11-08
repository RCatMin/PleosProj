package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import kotlin.math.cos
import kotlin.math.sin

/**
 * 후보 경로 고르기. OSRM 공개 서버는 시내 짧은 거리에서 대안 경로를 거의 주지 않아서,
 * 출발지·목적지를 잇는 선의 양옆에 경유 지점을 찍어 서로 다른 길을 더 받아 온다.
 */
object Candidates {

    /** 출발지와 목적지 중간에서 진행 방향 왼쪽·오른쪽으로 떨어진 경유 지점 두 개 */
    fun sideVias(from: LatLng, to: LatLng): List<LatLng> {
        val d = RouteGeometry.distanceMeters(from, to)
        // 너무 가까우면 돌아가는 길이 의미 없고, 너무 멀면 엉뚱한 동네로 간다
        val offset = (d * 0.25).coerceIn(600.0, 3000.0)
        val mid = LatLng((from.lat + to.lat) / 2, (from.lng + to.lng) / 2)
        val bearing = RouteGeometry.bearing(from, to)
        return listOf(bearing - 90, bearing + 90).map { move(mid, it, offset) }
    }

    /** [point]를 피해 가도록 그 지점 양옆으로 떨어진 경유 지점들 */
    fun avoidVias(from: LatLng, point: LatLng, distances: List<Double> = listOf(500.0, 1000.0)): List<LatLng> {
        val bearing = RouteGeometry.bearing(from, point)
        return distances.flatMap { d -> listOf(bearing - 90, bearing + 90).map { move(point, it, d) } }
    }

    /** [b] 경로선의 몇 %가 [a] 경로선 [toleranceMeters] 안에 있는지 (0~1) */
    fun overlap(a: RoutePlan, b: RoutePlan, toleranceMeters: Double = 40.0): Double {
        val length = RouteGeometry.lengthMeters(b.line)
        if (length <= 0) return 1.0
        val step = (length / 60).coerceAtLeast(50.0)
        var inside = 0
        var total = 0
        var m = 0.0
        while (m <= length) {
            val p = RouteGeometry.pointAt(b.line, m)
            if (RouteGeometry.project(a.line, p, stepMeters = 40.0).second <= toleranceMeters) inside++
            total++
            m += step
        }
        return inside.toDouble() / total
    }

    /**
     * 빠른 순으로 보면서, 이미 고른 경로와 [maxOverlap] 넘게 겹치거나 가장 빠른 경로보다
     * [maxDetourRatio]배 넘게 오래 걸리는 경로는 버린다. OSRM은 시내를 막힘 없이 달린다고 보고 계산해서
     * 큰길 직진이 지나치게 빠르게 나오므로 비율을 넉넉히 둔다. 추천은 따로 ADR 0003의 +20% 규칙을 따른다
     */
    fun pickDistinct(
        routes: List<RoutePlan>, maxCount: Int = 3, maxOverlap: Double = 0.7, maxDetourRatio: Double = 2.2,
    ): List<RoutePlan> {
        val sorted = routes.sortedBy { it.durationSeconds }
        val fastest = sorted.firstOrNull() ?: return emptyList()
        val kept = mutableListOf(fastest)
        for (r in sorted.drop(1)) {
            if (kept.size >= maxCount) break
            if (r.durationSeconds > fastest.durationSeconds * maxDetourRatio) continue
            if (kept.any { overlap(it, r) > maxOverlap }) continue
            kept += r
        }
        return kept
    }

    /** 경로가 [point]에서 [radiusMeters] 넘게 떨어져 지나가는지 */
    fun avoids(route: RoutePlan, point: LatLng, radiusMeters: Double = 150.0) =
        RouteGeometry.project(route.line, point, stepMeters = 20.0).second > radiusMeters

    private fun move(from: LatLng, bearingDeg: Double, meters: Double): LatLng {
        val r = 6_371_000.0
        val d = meters / r
        val b = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.lat)
        val lng1 = Math.toRadians(from.lng)
        val lat2 = kotlin.math.asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lng2 = lng1 + kotlin.math.atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /**
     * 후보 경로마다 다른 경로와 구별되는 길 이름을 붙인다. 그 경로에서 많이 달리면서 다른 경로에서는
     * 덜 달리는 길을 고른다. 예: 셋 다 테헤란로를 지나도 "봉은사로 경유", "도곡로 경유"처럼 나온다
     */
    fun nameDistinct(routes: List<RoutePlan>): List<RoutePlan> = routes.map { r ->
        val others = routes.filter { it !== r }
        val best = r.roads.maxByOrNull { (road, meters) -> meters - (others.maxOfOrNull { it.roads[road] ?: 0.0 } ?: 0.0) }
        if (best == null || routes.size == 1) r else r.copy(name = "${best.key} 경유")
    }

    /**
     * 후보 경로에 성격으로 이름을 붙인다 (예시 앱과 같은 방식). 첫 경로(길찾기 추천)가 가장 빠르면 "추천 길",
     * 그 밖에 가장 빠른 길·가장 짧은 길을 표시하고, 나머지는 "빠른 순서 N위"로 쓴다
     */
    fun nameByRole(routes: List<RoutePlan>): List<RoutePlan> {
        if (routes.size <= 1) return routes.map { it.copy(name = "추천 길") }
        val fastest = routes.minByOrNull { it.durationSeconds }
        val shortest = routes.minByOrNull { it.distanceMeters }
        val byTime = routes.sortedBy { it.durationSeconds }
        return routes.mapIndexed { i, r ->
            val name = when {
                r === fastest -> if (i == 0) "추천 길" else "가장 빠른 길"
                r === shortest -> "가장 짧은 길"
                i == 0 -> "추천 길"
                else -> "빠른 순서 ${byTime.indexOf(r) + 1}위"
            }
            r.copy(name = name)
        }
    }
}
