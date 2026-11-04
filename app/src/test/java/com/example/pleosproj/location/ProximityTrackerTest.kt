package com.example.pleosproj.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityTrackerTest {

    // 선정릉역 부근. 위도 0.001도 ≈ 111m
    private val lat = 37.5102
    private val lng = 127.0436

    @Test
    fun `반경에 들어가면 한 번만 알린다`() {
        val t = ProximityTracker()
        t.add("water", lat, lng, 300f)
        assertTrue(t.update(lat - 0.005, lng).isEmpty())
        assertEquals(listOf("water" to ProximityChange.ENTER), t.update(lat - 0.002, lng))
        assertTrue(t.update(lat - 0.001, lng).isEmpty())
    }

    @Test
    fun `경계에서 흔들려도 바로 나감으로 보지 않는다`() {
        val t = ProximityTracker(exitMarginMeters = 20.0)
        t.add("water", lat, lng, 300f)
        t.update(lat, lng)
        // 반경보다 약 11m 밖: 여유 거리 안이라 아직 안에 있는 것으로 본다
        assertTrue(t.update(lat + 0.00280, lng).isEmpty())
        assertEquals(listOf("water" to ProximityChange.EXIT), t.update(lat + 0.0035, lng))
    }

    @Test
    fun `지운 지점은 알리지 않는다`() {
        val t = ProximityTracker()
        t.add("water", lat, lng, 300f)
        t.remove("water")
        assertTrue(t.update(lat, lng).isEmpty())
    }
}
