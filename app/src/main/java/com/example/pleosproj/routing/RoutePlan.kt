package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
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
)

/** 회전 지점. [atMeters]는 경로 출발점부터 경로선을 따라 잰 거리 */
data class RouteTurn(val atMeters: Double, val kind: TurnKind, val text: String)
