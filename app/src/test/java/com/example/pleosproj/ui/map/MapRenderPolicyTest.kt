package com.example.pleosproj.ui.map

import org.junit.Assert.*
import org.junit.Test

class MapRenderPolicyTest {
    @Test fun differentDisplayRatesKeepThirtyUpdatesPerSecond() {
        for (hz in listOf(60, 90, 120)) {
            val pacer = MapFramePacer()
            val accepted = (0 until hz * 10).count { frame ->
                pacer.shouldRender(frame * 1_000_000_000L / hz)
            }
            assertEquals("${hz}Hz에서도 10초에 300회만 갱신한다", 300, accepted)
        }
    }

    @Test fun smallVsyncJitterDoesNotReduceMotionToTwentyFps() {
        val pacer = MapFramePacer()
        val accepted = (0 until 600).filter { frame ->
            val jitter = if (frame % 3 == 0) 200_000L else -200_000L
            pacer.shouldRender(1_000_000L + frame * 1_000_000_000L / 60 + jitter)
        }
        assertEquals(300, accepted.size)
        assertTrue("한 프레임과 세 프레임 간격이 번갈아 생기지 않는다", accepted.zipWithNext().all { (a, b) -> b - a == 2 })
    }

    @Test fun resumingAfterAStallRendersCurrentTimeOnceWithoutACatchUpQueue() {
        val pacer = MapFramePacer()
        assertTrue(pacer.shouldRender(0))
        assertFalse(pacer.shouldRender(16_666_666))
        assertTrue(pacer.shouldRender(2_000_000_000))
        assertFalse(pacer.shouldRender(2_000_000_000))
        assertFalse(pacer.shouldRender(2_000_000_001))
        assertTrue(pacer.shouldRender(2_033_333_333))
    }

    @Test fun buildingsReturnOnlyForCloseDriverGuidanceAfterGestureEnds() {
        fun show(guidance: Boolean = true, driver: Boolean = true, zoom: Double = 15.8, gesture: Boolean = false) =
            MapRenderPolicy.showBuildings(guidance, driver, zoom, gesture)
        assertTrue(show())
        assertFalse(show(gesture = true))
        assertTrue(show(gesture = false))
        assertFalse(show(zoom = 14.99))
        assertTrue(show(zoom = 15.0))
        assertFalse(show(driver = false))
        assertFalse(show(guidance = false))
    }
}
