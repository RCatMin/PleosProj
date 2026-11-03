package com.example.pleosproj.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {

    // 강남역 → 삼성역 근처, 직선 약 2.9km
    private val line = listOf(LatLng(37.4979, 127.0276), LatLng(37.5045, 127.0490), LatLng(37.5088, 127.0631))

    @Test
    fun 두_지점_거리를_미터로_구한다() {
        // 위도 0.01도는 약 1.11km
        assertEquals(1112.0, RouteGeometry.distanceMeters(LatLng(37.0, 127.0), LatLng(37.01, 127.0)), 5.0)
    }

    @Test
    fun 경로를_1km_구간으로_나누면_길이_합이_전체와_같다() {
        val total = RouteGeometry.lengthMeters(line)
        val slices = RouteGeometry.slice(line, 1000.0)
        assertEquals(kotlin.math.ceil(total / 1000.0).toInt(), slices.size)
        assertEquals(total, slices.sumOf { it.lengthMeters }, 1.0)
        assertTrue(slices.dropLast(1).all { kotlin.math.abs(it.lengthMeters - 1000.0) < 1.0 })
    }

    @Test
    fun 구간_비율은_0에서_1까지_빈틈없이_이어진다() {
        val slices = RouteGeometry.slice(line, 1000.0)
        assertEquals(0.0, slices.first().startRatio, 1e-9)
        assertEquals(1.0, slices.last().endRatio, 1e-9)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.endRatio, b.startRatio, 1e-9) }
        assertEquals(line.last(), slices.last().end)
    }
}
