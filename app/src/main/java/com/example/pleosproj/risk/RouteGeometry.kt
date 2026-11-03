package com.example.pleosproj.risk

import kotlin.math.asin
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
}
