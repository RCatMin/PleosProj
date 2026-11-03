package com.example.pleosproj.risk

// 위험도 계산의 입력과 출력. 기상청·ITS·차량 SDK 클래스와 분리해서,
// 어디서 온 값이든 이 모양으로 바꿔 넣으면 계산할 수 있게 한다 (ADR 0003).

/** 특보 단계. */
enum class WarningLevel { NONE, ADVISORY, WARNING }

/** 침수에 약한 지점의 종류. */
enum class Vulnerability { NONE, UNDERPASS, RIVERSIDE, PAST_FLOOD }

/** 한 구간을 지나는 시각 기준의 상황. */
data class SegmentConditions(
    /** 예보 시간당 강수량(mm) */
    val rainMmPerHour: Double = 0.0,
    val snowing: Boolean = false,
    /** 예보 기온(℃) */
    val temperatureC: Double = 15.0,
    val warning: WarningLevel = WarningLevel.NONE,
    val vulnerability: Vulnerability = Vulnerability.NONE,
    /** 결빙 취약 구간인지 */
    val icingZone: Boolean = false,
    /** 통제·사고로 지날 수 없는 구간인지 */
    val closed: Boolean = false,
)

data class RouteSegment(
    val lengthMeters: Double,
    /** 이 구간을 지나는 데 걸리는 시간(초) */
    val travelSeconds: Double,
    val conditions: SegmentConditions,
)

/** 와이퍼 단계. Vehicle SDK의 와이퍼 상태를 이 네 단계로 줄인다. */
enum class WiperLevel { OFF, INTERMITTENT, LOW, HIGH }

/** 차종. 침수 기준을 다르게 잡는다. */
enum class BodyType { SEDAN, SUV, EV }

data class VehicleSignals(
    /** 모르면 null. 보정하지 않는다 */
    val wiper: WiperLevel? = null,
    /** 차에서 읽은 외기온도. 있으면 예보 기온보다 먼저 쓴다 */
    val outsideTempC: Double? = null,
    val bodyType: BodyType = BodyType.SEDAN,
)

/** 위험 정도. 화면에서는 이 값을 보고 색·문구를 정한다. */
enum class RiskBand { NONE, LOW, MEDIUM, HIGH }

data class RouteInput(
    val id: String,
    val segments: List<RouteSegment>,
)

data class RouteAssessment(
    val id: String,
    /** 0~1 */
    val score: Double,
    val band: RiskBand,
    val durationSeconds: Double,
    /** 통제 구간이 있어 후보에서 뺀 경로 */
    val excluded: Boolean,
    /** 구간별 점수. 진행 막대의 위험 구간 표시에 쓴다 */
    val segmentScores: List<Double>,
)

data class Recommendation(
    val assessments: List<RouteAssessment>,
    /** 추천 경로. 지날 수 있는 경로가 없으면 null */
    val recommendedId: String?,
    /** 남은 경로가 모두 위험해서 출발을 미루거나 쉬어 가기를 권해야 하는지 */
    val suggestPostpone: Boolean,
)
