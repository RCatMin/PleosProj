package com.example.pleosproj.routing

import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.weather.WeatherWarning

/** 기상특보 목록을 위험도 계산의 특보 단계로 바꾼다 */
object Warnings {

    /** 호우·대설 특보가 발표 중이면 그 단계. 경보가 하나라도 있으면 경보로 본다 */
    fun level(warnings: List<WeatherWarning>): WarningLevel {
        val active = warnings.filter { it.active && (it.kind == "호우" || it.kind == "대설") }
        return when {
            active.any { it.level == "경보" } -> WarningLevel.WARNING
            active.isNotEmpty() -> WarningLevel.ADVISORY
            else -> WarningLevel.NONE
        }
    }
}
