package com.example.pleosproj.risk

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도·경도. 화면 모델(ui/state)과 섞지 않으려고 risk 안에 따로 둔다. */
data class LatLng(val lat: Double, val lng: Double)

/** 경로선을 일정 길이 구간으로 나눈 결과 한 칸. */
data class SegmentSlice(
    val start: LatLng,
    val end: LatLng,
    val lengthMeters: Double,
    /** 전체 경로에서 이 구간의 시작·끝 위치(0~1). 진행 막대와 맞춘다 */
    val startRatio: Double,
    val endRatio: Double,
) {
    val middle get() = LatLng((start.lat + end.lat) / 2, (start.lng + end.lng) / 2)
}

object RouteGeometry {

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun lengthMeters(line: List<LatLng>): Double = line.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

    /** 경로선을 약 [sliceMeters] 길이로 자른다. 마지막 칸은 짧을 수 있다. */
    fun slice(line: List<LatLng>, sliceMeters: Double = 1000.0): List<SegmentSlice> {
        require(line.size >= 2) { "경로선에는 점이 두 개 이상 있어야 한다" }
        val total = lengthMeters(line)
        if (total == 0.0) return emptyList()

        val slices = mutableListOf<SegmentSlice>()
        var sliceStart = line.first()
        var sliceStartDist = 0.0
        var walked = 0.0
        var nextCut = sliceMeters

        for ((a, b) in line.zipWithNext()) {
            val legLen = distanceMeters(a, b)
            var legPos = 0.0
            while (walked + (legLen - legPos) >= nextCut && nextCut < total) {
                val t = (legPos + (nextCut - walked)) / legLen
                val cut = LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
                slices += SegmentSlice(sliceStart, cut, nextCut - sliceStartDist, sliceStartDist / total, nextCut / total)
                legPos += nextCut - walked
                walked = nextCut
                sliceStart = cut
                sliceStartDist = nextCut
                nextCut += sliceMeters
            }
            walked += legLen - legPos
        }
        slices += SegmentSlice(sliceStart, line.last(), total - sliceStartDist, sliceStartDist / total, 1.0)
        return slices
    }

    /** 경로선 위에서 출발점부터 [meters]만큼 간 지점. 경로 길이를 넘으면 끝점이다. */
    fun pointAt(line: List<LatLng>, meters: Double): LatLng {
        if (meters <= 0.0) return line.first()
        var walked = 0.0
        for ((a, b) in line.zipWithNext()) {
            val leg = distanceMeters(a, b)
            if (walked + leg >= meters && leg > 0.0) {
                val t = (meters - walked) / leg
                return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
            }
            walked += leg
        }
        return line.last()
    }

    /** a에서 b로 가는 방향(도, 북쪽 0, 시계 방향). */
    fun bearing(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** [meters] 지점에서 경로가 향하는 방향. */
    fun bearingAt(line: List<LatLng>, meters: Double): Double {
        val here = pointAt(line, meters)
        val ahead = pointAt(line, meters + 20.0)
        return if (here == ahead) bearing(line[line.size - 2], line.last()) else bearing(here, ahead)
    }

    /**
     * 점을 경로선에 가장 가깝게 붙였을 때, 출발점부터의 거리와 경로까지의 거리를 돌려준다.
     * 경로선 꼭짓점 사이를 20m 간격으로 훑는 단순한 방식이라 시나리오 크기에서만 쓴다.
     */
    fun project(line: List<LatLng>, point: LatLng, stepMeters: Double = 20.0): Pair<Double, Double> {
        val total = lengthMeters(line)
        var best = 0.0
        var bestDist = Double.MAX_VALUE
        var m = 0.0
        while (m <= total) {
            val d = distanceMeters(pointAt(line, m), point)
            if (d < bestDist) { bestDist = d; best = m }
            m += stepMeters
        }
        return best to bestDist
    }
}
