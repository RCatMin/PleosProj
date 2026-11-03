package com.example.pleosproj.traffic

import java.time.LocalDateTime

/** ITS 돌발상황 한 건. */
data class TrafficEvent(
    /** 예: "고속도로", "국도" */
    val roadType: String,
    /** 예: "공사", "사고", "기상", "통제", "기타돌발" */
    val eventType: String,
    val detailType: String,
    val roadName: String,
    val lat: Double,
    val lng: Double,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val message: String,
    /** 차로가 막혔는지(전면 통제 포함) */
    val lanesBlocked: Boolean,
) {
    /** 경로에서 빼야 할 정도의 통제인지. 위험도 계산(ADR 0003 추천 규칙 1)에서 쓴다 */
    val isClosure: Boolean
        get() = eventType.contains("통제") || detailType.contains("통제") || message.contains("전면통제") ||
            message.contains("전면 통제")
}
