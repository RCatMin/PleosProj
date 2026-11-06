package com.example.pleosproj.navi

import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.TurnKind

// NaviHelper 값을 앱 쪽 모양으로 옮긴 것. SDK 클래스와 분리해서 실제 주행 상태 계산을 단위 테스트할 수 있게 한다.

/** 회전 안내 한 칸. [segmentMeters]는 앞 회전(첫 칸은 출발점)에서 이 회전까지의 구간 거리다 */
data class NaviTurn(
    val kind: TurnKind,
    val segmentMeters: Double,
    val text: String,
    /** 지하차도로 들어가는 안내인지. 비가 오면 침수 위험 지점으로 본다 */
    val entersUnderpass: Boolean = false,
)

/** 막힘 구간. [offsetMeters]는 경로 시작점부터의 거리 */
data class NaviCongestion(val offsetMeters: Double, val lengthMeters: Double, val heavy: Boolean, val roadName: String)

/** 기본 내비가 알려 주는 주의 지점 중 날씨·노면과 관련된 것 */
enum class CautionKind { SLIPPERY_ROAD, FOG, ACCIDENT_PRONE, ROAD_DAMAGE, CONSTRUCTION, FALLING_ROCK, DOWNHILL }

/** 주의 지점. [remainMeters]는 지금 위치에서 그 지점까지 남은 거리 */
data class NaviCaution(val id: String, val kind: CautionKind, val remainMeters: Int)

/** 기본 내비 교통 이벤트(사고·공사·통제 등). 좌표가 있다 */
data class NaviTrafficEvent(val lat: Double, val lng: Double, val kind: HazardKind, val description: String, val closure: Boolean)

/** NaviHelper에서 받은 값을 모은 한 시점의 상태 */
data class NaviSnapshot(
    /** 기본 내비에게서 목적지 정보를 한 번이라도 받았는지. 받기 전에는 안내 중인지 아닌지 모른다 */
    val known: Boolean = false,
    /** 기본 내비가 경로 안내 중인지 */
    val guiding: Boolean = false,
    val destinationName: String = "",
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val remainMeters: Int = 0,
    val remainSeconds: Int = 0,
    val turns: List<NaviTurn> = emptyList(),
    val congestion: List<NaviCongestion> = emptyList(),
    val cautions: List<NaviCaution> = emptyList(),
    val events: List<NaviTrafficEvent> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    val heading: Double? = null,
    val address: String? = null,
) {
    /** 회전 안내 구간 거리의 합 = 경로 전체 길이 */
    val routeMeters: Double get() = turns.sumOf { it.segmentMeters }
}
