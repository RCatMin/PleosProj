package com.example.pleosproj.ui.state

import java.time.LocalTime

/** 안내 화면(PLE-19) 상태. */
data class GuidanceState(
    val mode: DriveMode,
    val destinationName: String,
    val nextTurn: TurnInstruction?,
    /** 다음 회전 이후의 회전 몇 개. 필요할 때만 쓴다 */
    val upcomingTurns: List<TurnInstruction>,
    val remainingMeters: Int,
    val remainingSeconds: Int,
    val arrivalTime: LocalTime,
    val myPosition: GeoPoint,
    /** 진행 방향(도, 북쪽 0) */
    val heading: Double,
    /** 지도에 그릴 경로선. 실제 주행에서는 null이다 (ADR 0005) */
    val routeLine: List<GeoPoint>?,
    val hazards: List<Hazard>,
    val progress: RouteProgress,
    val weather: List<WeatherCard>,
    /** 지금 띄울 위험 알림. 없으면 null */
    val alert: HazardAlert?,
    /** 앞으로 지날 IC·JC·톨게이트·휴게소. 가까운 순서. 시내 경로처럼 없으면 비어 있다 */
    val facilities: List<RouteFacility> = emptyList(),
)

/** 경로 위 시설 */
data class RouteFacility(
    val kind: FacilityKind,
    /** 예: "구리IC", "구리휴게소" */
    val name: String,
    /** 지금 위치에서 남은 거리(m) */
    val distanceMeters: Int,
)

enum class FacilityKind { IC, JC, TOLLGATE, REST_AREA, ROADSIDE_STOP }

/** 오른쪽 세로 진행 막대. 위치는 모두 전체 경로 중 비율(0=출발, 1=도착). */
data class RouteProgress(
    /** 지금 위치 */
    val currentRatio: Double,
    val totalMeters: Int,
    val segments: List<ProgressSegment>,
)

data class ProgressSegment(
    val kind: SegmentKind,
    val startRatio: Double,
    /** 회전처럼 한 지점이면 startRatio와 같다 */
    val endRatio: Double,
    val label: String? = null,
    /** kind가 HAZARD일 때 어떤 위험인지 */
    val hazardKind: HazardKind? = null,
)

enum class SegmentKind { TURN, CONGESTION_SLOW, CONGESTION_HEAVY, HAZARD }

data class HazardAlert(
    val hazardId: String,
    /** 예: "바로 앞 침수 우려" */
    val title: String,
    /** 예: "800m 앞 지하차도, 과거 침수 이력" */
    val message: String,
    val level: RiskLevel,
    /** 우회 제안이 함께 있으면 true. 화면에 "우회" 버튼을 보여 준다 */
    val suggestsDetour: Boolean,
)
