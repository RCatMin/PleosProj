package com.example.pleosproj.risk

/**
 * 위험도 계산의 가중치와 기준값. 아직 근거 없는 임시값이라(ADR 0003) 시나리오를 돌려 보며 여기서만 고친다.
 */
object RiskWeights {
    const val WEATHER = 0.35
    const val WARNING = 0.20
    const val VULNERABILITY = 0.30
    const val ICING = 0.15

    /** 이 강수량(mm/h) 이상이면 날씨 점수 1.0 */
    const val HEAVY_RAIN_MM = 30.0

    /** 최단 경로보다 이 비율 이상 오래 걸리면 추천하지 않는다 */
    const val MAX_DETOUR_RATIO = 1.2

    /** 남은 경로가 모두 이 점수 이상이면 출발 연기를 권한다 */
    const val POSTPONE_SCORE = 0.6

    const val VEHICLE_FACTOR_MIN = 0.8
    const val VEHICLE_FACTOR_MAX = 1.5

    /** 점수 → 단계 경계 */
    const val BAND_LOW = 0.15
    const val BAND_MEDIUM = 0.35
    const val BAND_HIGH = 0.6
}
