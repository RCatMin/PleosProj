package com.example.pleosproj.scenario

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.risk.WiperLevel
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.TurnKind
import java.time.LocalTime

/**
 * 시연용 시나리오 (ADR 0005). 실제 주행에서 NaviHelper·기상청·차량에서 받을 값을
 * 미리 만들어 둔 것이다. 시간은 모두 재생 시작부터의 초다.
 */
data class Scenario(
    val id: String,
    val title: String,
    val startTime: LocalTime,
    val destinationName: String,
    val routes: List<ScenarioRoute>,
    /** 실제로 따라갈 경로. 경로 비교에서 추천된 경로와 같게 둔다 */
    val driveRouteId: String,
    val hazards: List<ScenarioHazard>,
    val weather: List<WeatherKey>,
    val vehicle: List<VehicleKey>,
) {
    val driveRoute get() = routes.first { it.id == driveRouteId }
}

data class ScenarioRoute(
    val id: String,
    val name: String,
    val durationSeconds: Int,
    val tollWon: Int,
    val line: List<LatLng>,
    val turns: List<ScenarioTurn>,
)

data class ScenarioTurn(
    /** 출발점부터 이 회전까지의 거리(m) */
    val atMeters: Double,
    val kind: TurnKind,
    val text: String,
)

data class ScenarioHazard(
    val id: String,
    val kind: HazardKind,
    val position: LatLng,
    val title: String,
    val detail: String,
    val vulnerability: Vulnerability = Vulnerability.NONE,
    val icingZone: Boolean = false,
    /** 이 시각부터 통제된다. null이면 통제 없음 */
    val closedFromSeconds: Int? = null,
) {
    fun closedAt(seconds: Double) = closedFromSeconds != null && seconds >= closedFromSeconds
}

/** 이 시각부터 다음 키까지 유지되는 날씨. */
data class WeatherKey(
    val atSeconds: Int,
    val rainMmPerHour: Double,
    val snowing: Boolean,
    val temperatureC: Double,
    val warning: WarningLevel,
)

/** 이 시각부터 다음 키까지 유지되는 차량 신호. */
data class VehicleKey(
    val atSeconds: Int,
    val wiper: WiperLevel,
)

/** 시간 순서 키 목록에서 [seconds] 시각의 값을 고른다. 처음 키보다 이르면 첫 키를 쓴다. */
internal fun <T> List<T>.at(seconds: Double, time: (T) -> Int): T =
    lastOrNull { time(it) <= seconds } ?: first()
