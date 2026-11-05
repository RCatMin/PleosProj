package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.state.GeoPoint
import kotlin.math.*

internal data class MapMotion(val position: GeoPoint, val heading: Double)

// 경로 길이는 한 번만 계산한다. 화면 보간은 꼭짓점을 건너뛰어 교차로를 가로지르지 않는다.
internal class RouteMotion(private val line: List<GeoPoint>) {
    private val distances = DoubleArray(line.size)
    init {
        require(line.size >= 2)
        for (i in 1 until line.size) distances[i] = distances[i - 1] + distance(line[i - 1], line[i])
    }
    private val length get() = distances.last()

    fun at(ratio: Double): MapMotion {
        val meters = ratio.coerceIn(0.0, 1.0) * length
        val here = pointAt(meters)
        // 회전 직전의 방향도 조금씩 바뀌게 해 카메라가 한 프레임에 꺾이지 않게 한다.
        val ahead = pointAt(meters + 20.0)
        val heading = if (here != ahead) bearing(here, ahead) else {
            val behind = pointAt((meters - 20.0).coerceAtLeast(0.0))
            if (here == behind) 0.0 else bearing(behind, here)
        }
        return MapMotion(here, heading)
    }

    private fun pointAt(meters: Double): GeoPoint {
        if (meters <= 0.0 || length == 0.0) return line.first()
        if (meters >= length) return line.last()
        var low = 1
        var high = distances.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (distances[middle] < meters) low = middle + 1 else high = middle
        }
        val a = line[low - 1]
        val b = line[low]
        val fraction = (meters - distances[low - 1]) / (distances[low] - distances[low - 1])
        return GeoPoint(a.lat + (b.lat - a.lat) * fraction, a.lng + (b.lng - a.lng) * fraction)
    }

    private fun distance(a: GeoPoint, b: GeoPoint): Double {
        val latitude = Math.toRadians(b.lat - a.lat)
        val longitude = Math.toRadians(b.lng - a.lng)
        val h = sin(latitude / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(longitude / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val latitude = Math.toRadians(a.lat)
        val nextLatitude = Math.toRadians(b.lat)
        val longitude = Math.toRadians(b.lng - a.lng)
        return (Math.toDegrees(atan2(sin(longitude) * cos(nextLatitude),
            cos(latitude) * sin(nextLatitude) - sin(latitude) * cos(nextLatitude) * cos(longitude))) + 360) % 360
    }
}
