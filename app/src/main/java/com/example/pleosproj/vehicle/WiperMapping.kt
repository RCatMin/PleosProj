package com.example.pleosproj.vehicle

import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import com.example.pleosproj.risk.WiperLevel

/**
 * Vehicle SDK의 와이퍼 값을 위험도 계산용 네 단계로 줄인다.
 * 와이퍼 상태(WiperState)는 켜짐/꺼짐뿐이라 단계는 스위치(WiperSwitch)로 판단한다.
 * 자동(AUTO)은 실제 속도를 알 수 없어서 켜져 있으면 중간(LOW)으로 본다.
 */
object WiperMapping {
    fun level(state: WiperState?, switch: WiperSwitch?): WiperLevel? {
        if (state == WiperState.OFF) return WiperLevel.OFF
        return when (switch) {
            null -> if (state == WiperState.ON) WiperLevel.LOW else null
            WiperSwitch.OFF -> WiperLevel.OFF
            WiperSwitch.MIST,
            WiperSwitch.INTERMITTENT_LEVEL_1, WiperSwitch.INTERMITTENT_LEVEL_2, WiperSwitch.INTERMITTENT_LEVEL_3,
            WiperSwitch.INTERMITTENT_LEVEL_4, WiperSwitch.INTERMITTENT_LEVEL_5 -> WiperLevel.INTERMITTENT
            WiperSwitch.CONTINUOUS_LEVEL_1, WiperSwitch.CONTINUOUS_LEVEL_2 -> WiperLevel.LOW
            WiperSwitch.CONTINUOUS_LEVEL_3, WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperLevel.HIGH
            WiperSwitch.AUTO -> if (state == WiperState.ON) WiperLevel.LOW else WiperLevel.OFF
            WiperSwitch.SERVICE, WiperSwitch.OTHER -> null
        }
    }
}
