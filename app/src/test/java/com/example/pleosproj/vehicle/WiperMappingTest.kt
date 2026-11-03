package com.example.pleosproj.vehicle

import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.example.pleosproj.risk.WiperLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WiperMappingTest {

    @Test
    fun 꺼져_있으면_스위치와_상관없이_OFF() {
        assertEquals(WiperLevel.OFF, WiperMapping.level(WiperState.OFF, WiperSwitch.CONTINUOUS_LEVEL_5))
    }

    @Test
    fun 스위치_단계를_네_단계로_줄인다() {
        assertEquals(WiperLevel.INTERMITTENT, WiperMapping.level(WiperState.ON, WiperSwitch.INTERMITTENT_LEVEL_3))
        assertEquals(WiperLevel.LOW, WiperMapping.level(WiperState.ON, WiperSwitch.CONTINUOUS_LEVEL_2))
        assertEquals(WiperLevel.HIGH, WiperMapping.level(WiperState.ON, WiperSwitch.CONTINUOUS_LEVEL_3))
        assertEquals(WiperLevel.HIGH, WiperMapping.level(WiperState.ON, WiperSwitch.CONTINUOUS_LEVEL_5))
    }

    @Test
    fun 자동이면_켜져_있을_때_LOW로_본다() {
        assertEquals(WiperLevel.LOW, WiperMapping.level(WiperState.ON, WiperSwitch.AUTO))
    }

    @Test
    fun 스위치를_못_읽으면_상태로만_판단한다() {
        assertEquals(WiperLevel.LOW, WiperMapping.level(WiperState.ON, null))
        assertNull(WiperMapping.level(null, null))
    }
}
