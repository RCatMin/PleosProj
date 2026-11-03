package com.example.pleosproj.ui.state

/** 경로 비교 화면(PLE-20) 상태. 경로선이 필요해서 시나리오 모드에서만 쓴다 (ADR 0003). */
data class RouteCompareState(
    val destinationName: String,
    val candidates: List<RouteCandidate>,
    /** 지금 선택된 후보 id. 처음에는 추천 경로다 */
    val selectedId: String,
    val recommendedId: String?,
    /** 모든 경로가 위험하면 보여 줄 권유. 예: "30분 뒤 출발하면 비가 약해져요" */
    val adviceWhenAllRisky: String?,
)

data class RouteCandidate(
    val id: String,
    /** 예: "추천", "녹사평대로" */
    val name: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val tollWon: Int,
    /** 위험도 점수 0~1 */
    val riskScore: Double,
    val riskLevel: RiskLevel,
    /** 통제 구간 때문에 후보에서 뺀 경로 */
    val excluded: Boolean,
    /** 예: "지하차도 1곳 침수 통제" */
    val riskSummary: String,
    val routeLine: List<GeoPoint>,
    val hazards: List<Hazard>,
)
