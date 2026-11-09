package com.example.pleosproj.ui.state

import java.time.LocalTime

/** 경로 비교 화면(PLE-20) 상태. 경로선이 필요해서 시나리오 모드에서만 쓴다 (ADR 0003). */
data class RouteCompareState(
    val destinationName: String,
    val candidates: List<RouteCandidate>,
    /** 지금 선택된 후보 id. 처음에는 추천 경로다 */
    val selectedId: String,
    val recommendedId: String?,
    /** 모든 경로가 위험하면 보여 줄 권유. 예: "30분 뒤 출발하면 비가 약해져요" */
    val adviceWhenAllRisky: String?,
    /**
     * 경로 예보의 근거. 예: "기상청 초단기예보 · 6시간 이내". 날씨를 받지 못했으면 null.
     * 정확도 등급(높음·보통)은 뒷받침할 자료가 없어 주지 않는다 (PLE-43)
     */
    val forecastBasis: String? = null,
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
    /** 경로 위 지점별 통과 시각과 예보. 출발부터 도착까지 거리 순서. 날씨를 받지 못했으면 비어 있다 */
    val forecast: List<RouteForecastPoint> = emptyList(),
)

/** 경로 위 한 지점을 지날 때의 예보 */
data class RouteForecastPoint(
    /** 예: "출발", "3.2km", "도착" */
    val label: String,
    /** 출발부터 이 지점까지 거리(m) */
    val distanceMeters: Int,
    /** 이 지점을 지날 예상 시각 */
    val passTime: LocalTime,
    /** 이 지점·시각의 예보. 예보가 없으면 null (가짜 값을 채우지 않는다) */
    val weather: WeatherCard?,
)
