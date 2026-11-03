package com.example.pleosproj.ui.state

// 화면 상태 모델에서 함께 쓰는 값들. 화면(Codex)과 데이터(Claude) 사이의 약속이므로
// 바꿀 때는 이 패키지만 담은 PR로 올린다 (ADR 0001).

/** 위도·경도. */
data class GeoPoint(val lat: Double, val lng: Double)

/** 지금 화면을 움직이는 데이터의 출처. 시나리오면 시연용 재생 데이터다 (ADR 0005). */
enum class DriveMode { LIVE, SCENARIO }

/** 위험 지점 종류. 표시 색·아이콘은 화면에서 정한다. */
enum class HazardKind { FLOOD, ICE, CLOSURE, HEAVY_RAIN, HEAVY_SNOW, FOG, ACCIDENT, CONSTRUCTION }

/** 위험 정도. 위험도 점수(0~1)를 화면용으로 나눈 단계다. */
enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }

data class Hazard(
    val id: String,
    val kind: HazardKind,
    val position: GeoPoint,
    val level: RiskLevel,
    /** 짧은 이름. 예: "OO 지하차도" */
    val title: String,
    /** 동승자 화면 등에서 보여 줄 설명. 예: "과거 침수 이력, 시간당 40mm 예보" */
    val detail: String,
    /** 경로 위에 있으면 전체 경로 중 위치(0~1), 경로 밖이면 null */
    val ratioOnRoute: Double? = null,
)

/** 회전 종류. 기본 내비의 회전 종류를 화면에 필요한 만큼만 줄였다. */
enum class TurnKind {
    STRAIGHT, LEFT, RIGHT, KEEP_LEFT, KEEP_RIGHT, SHARP_LEFT, SHARP_RIGHT, U_TURN,
    ENTER_HIGHWAY, EXIT_HIGHWAY, TOLLGATE, TUNNEL, ARRIVE, UNKNOWN,
}

data class TurnInstruction(
    val kind: TurnKind,
    /** 이 회전까지 남은 거리(m) */
    val distanceMeters: Int,
    /** 안내 문구. 예: "대왕판교IC 방면" */
    val text: String,
)

data class WeatherCard(
    /** 예: "지금", "10km 앞" */
    val label: String,
    val temperatureC: Int,
    /** 강수 확률(%) */
    val precipitationChance: Int,
    /** 시간당 강수량(mm). 비가 없으면 0 */
    val rainMmPerHour: Double,
    val snow: Boolean,
)
