package com.example.pleosproj.ui.state

import java.time.LocalTime

/** 출발 전 브리핑 화면(PLE-21) 상태. 정차 중에만 보여 준다. */
data class BriefingState(
    val destinationName: String,
    val arrivalTime: LocalTime,
    val durationSeconds: Int,
    /** 위험 요약 문장들. 비어 있으면 "오늘 경로에 위험 없음"을 보여 준다 */
    val riskLines: List<String>,
    val overallLevel: RiskLevel,
    val weather: List<WeatherCard>,
    /** 지금 음성으로 브리핑을 읽는 중인지 */
    val speaking: Boolean,
)
