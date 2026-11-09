package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.ui.state.FacilityKind
import com.example.pleosproj.ui.state.TurnKind

/**
 * 우리 앱이 계산한 경로 하나 (ADR 0007). 경로선과 회전 지점을 모두 가지고 있어서
 * 지도에 그리고, 위험도를 비교하고, 안내하는 데 그대로 쓴다.
 */
data class RoutePlan(
    val id: String,
    /** 화면에 보일 이름. 예: "경부고속도로 경유" */
    val name: String,
    val distanceMeters: Int,
    /** 교통을 반영하지 않은 소요 시간(초) */
    val durationSeconds: Int,
    val line: List<LatLng>,
    val turns: List<RouteTurn>,
    val tollWon: Int = 0,
    /** 지나는 길 이름과 그 길을 달리는 거리(m). 후보 경로끼리 구별되는 이름을 붙일 때 쓴다 */
    val roads: Map<String, Double> = emptyMap(),
    /** 길찾기가 알려 준 경로 위 위험 지점 (예: 지하차도 진입). 위험도 계산에 함께 넣는다 */
    val hazards: List<RiskPoint> = emptyList(),
    /** 막히는 구간 (교통 정보가 있는 길찾기만) */
    val congestion: List<Congestion> = emptyList(),
    /** 어느 길찾기로 계산했는지 */
    val provider: String = "",
    /** 카카오모빌리티 대신 OSRM으로 계산했을 때 그 까닭. 아니면 null */
    val fallbackReason: String? = null,
    /** 경로 위 IC·JC·톨게이트·휴게소 (카카오모빌리티만) */
    val facilities: List<FacilityPoint> = emptyList(),
)

/** 경로 위 시설. [atMeters]는 경로 출발점부터 경로선을 따라 잰 거리 */
data class FacilityPoint(val atMeters: Double, val kind: FacilityKind, val name: String)

/** 막히는 구간. [startMeters]~[endMeters]는 경로 출발점부터의 거리 */
data class Congestion(val startMeters: Double, val endMeters: Double, val heavy: Boolean, val roadName: String)

/** 회전 지점. [atMeters]는 경로 출발점부터 경로선을 따라 잰 거리 */
data class RouteTurn(val atMeters: Double, val kind: TurnKind, val text: String)
