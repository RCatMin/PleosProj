package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RiskEngine
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.RouteInput
import com.example.pleosproj.risk.RouteSegment
import com.example.pleosproj.risk.SegmentConditions
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.scenario.toLevel
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.Hazard
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.RouteCandidate
import com.example.pleosproj.ui.state.RouteCompareState
import kotlin.math.roundToInt

/** 경로 위·근처의 위험 지점. 시나리오 위험 지점과 실제 ITS 돌발상황이 모두 이 모양으로 들어온다 */
data class RiskPoint(
    val id: String,
    val kind: HazardKind,
    val position: LatLng,
    val title: String,
    val detail: String = "",
    val vulnerability: Vulnerability = Vulnerability.NONE,
    val icingZone: Boolean = false,
    /** 이 시각(출발 기준 초)부터 통제. 이미 통제 중이면 0, 통제가 아니면 null */
    val closedFromSeconds: Int? = null,
    /** 경로에서 이만큼 안에 있으면 그 경로의 위험 지점으로 본다. null이면 비교기의 기본 반경 */
    val radiusMeters: Double? = null,
    /** 결빙 사고 다발지역처럼 통계로 뽑은 지점인지. 요약에서 이름 대신 개수로 말한다 */
    val statistical: Boolean = false,
) {
    fun closedAt(seconds: Double) = closedFromSeconds != null && seconds >= closedFromSeconds
}

/** 어떤 지점·시각의 날씨 */
data class WeatherSample(
    val rainMmPerHour: Double,
    val snowing: Boolean,
    val temperatureC: Double,
    val warning: WarningLevel,
)

/** 경로 위 [point]를 출발 기준 [seconds]초에 지날 때의 날씨를 알려 준다 */
fun interface WeatherAlong {
    fun at(point: LatLng, seconds: Double): WeatherSample
}

/**
 * 후보 경로마다 위험도를 계산해서 비교 화면 상태를 만든다 (ADR 0003). 경로를 약 1km 구간으로 나누고,
 * 구간마다 그 구간을 지날 시각의 날씨와 근처 위험 지점으로 점수를 매긴 뒤 [RiskEngine.recommend]로 추천한다.
 * 시나리오 재생과 실제 길찾기(OSRM)가 같은 계산을 쓴다.
 */
class RouteComparer(private val hazardRadiusMeters: Double = 150.0) {

    fun compare(
        destinationName: String,
        routes: List<RoutePlan>,
        hazards: List<RiskPoint>,
        weather: WeatherAlong,
        vehicle: VehicleSignals,
        startSeconds: Double = 0.0,
    ): RouteCompareState {
        require(routes.isNotEmpty()) { "비교할 경로가 없다" }
        val prepared = routes.map { Prepared(it, onRoute(it, hazards)) }
        val rec = RiskEngine.recommend(prepared.map { RouteInput(it.route.id, segments(it, weather, startSeconds)) }, vehicle)
        val candidates = prepared.map { p ->
            val a = rec.assessments.first { it.id == p.route.id }
            RouteCandidate(
                id = p.route.id,
                name = p.route.name,
                durationSeconds = p.route.durationSeconds,
                distanceMeters = p.route.distanceMeters,
                tollWon = p.route.tollWon,
                riskScore = a.score,
                riskLevel = a.band.toLevel(),
                excluded = a.excluded,
                riskSummary = summary(p, weather, vehicle, startSeconds, a.excluded, a.band.toLevel()),
                routeLine = p.route.line.map { GeoPoint(it.lat, it.lng) },
                hazards = p.near.map { (h, along) -> uiHazard(h, along, p, weather, vehicle, startSeconds) },
            )
        }
        return RouteCompareState(
            destinationName = destinationName,
            candidates = candidates,
            selectedId = rec.recommendedId ?: candidates.first().id,
            recommendedId = rec.recommendedId,
            adviceWhenAllRisky = if (rec.suggestPostpone) "모든 경로가 위험해요. 비가 약해질 때까지 출발을 미루는 게 좋아요." else null,
        )
    }

    /** 한 경로의 1km 구간별 위험 점수. 안내 화면 진행 막대의 위험 구간 표시에 쓴다 */
    fun segmentScores(
        route: RoutePlan, hazards: List<RiskPoint>, weather: WeatherAlong, vehicle: VehicleSignals, startSeconds: Double = 0.0,
    ): List<Double> = RiskEngine.assess(RouteInput(route.id, segments(Prepared(route, onRoute(route, hazards)), weather, startSeconds)), vehicle).segmentScores

    /** 경로와 그 경로 반경 안의 위험 지점(경로 위 위치 m) */
    private class Prepared(val route: RoutePlan, val near: List<Pair<RiskPoint, Double>>) {
        val length = RouteGeometry.lengthMeters(route.line).coerceAtLeast(1.0)
        fun passAt(along: Double, start: Double) = start + route.durationSeconds * along / length
    }

    private fun onRoute(route: RoutePlan, hazards: List<RiskPoint>) = hazards.mapNotNull { h ->
        val (along, off) = RouteGeometry.project(route.line, h.position)
        if (off <= (h.radiusMeters ?: hazardRadiusMeters)) h to along else null
    }

    /** 1km 구간마다 그 구간을 지날 시각의 날씨와 근처 위험 지점으로 조건을 만든다 */
    private fun segments(p: Prepared, weather: WeatherAlong, start: Double): List<RouteSegment> =
        RouteGeometry.slice(p.route.line).map { slice ->
            val passAt = start + p.route.durationSeconds * (slice.startRatio + slice.endRatio) / 2
            val w = weather.at(slice.middle, passAt)
            val near = p.near.filter { (_, along) -> along / p.length in slice.startRatio..slice.endRatio }.map { it.first }
            RouteSegment(
                lengthMeters = slice.lengthMeters,
                travelSeconds = p.route.durationSeconds * (slice.endRatio - slice.startRatio),
                conditions = SegmentConditions(
                    rainMmPerHour = w.rainMmPerHour,
                    snowing = w.snowing,
                    temperatureC = w.temperatureC,
                    warning = w.warning,
                    vulnerability = near.map { it.vulnerability }.maxByOrNull { it.ordinal } ?: Vulnerability.NONE,
                    icingZone = near.any { it.icingZone },
                    closed = near.any { it.closedAt(passAt) },
                ),
            )
        }

    /** 그 지점을 지날 시각 기준의 위험 정도 */
    private fun levelAt(h: RiskPoint, seconds: Double, weather: WeatherAlong, vehicle: VehicleSignals): RiskLevel {
        if (h.closedAt(seconds)) return RiskLevel.HIGH
        val w = weather.at(h.position, seconds)
        val c = SegmentConditions(
            rainMmPerHour = w.rainMmPerHour, snowing = w.snowing, temperatureC = w.temperatureC, warning = w.warning,
            vulnerability = h.vulnerability, icingZone = h.icingZone,
        )
        return RiskEngine.pointBand(c, vehicle).toLevel()
    }

    private fun uiHazard(h: RiskPoint, along: Double, p: Prepared, weather: WeatherAlong, vehicle: VehicleSignals, start: Double): Hazard {
        val at = p.passAt(along, start)
        return Hazard(
            id = h.id,
            kind = if (h.closedAt(at)) HazardKind.CLOSURE else h.kind,
            position = GeoPoint(h.position.lat, h.position.lng),
            level = levelAt(h, at, weather, vehicle),
            title = h.title,
            detail = h.detail,
            ratioOnRoute = along / p.length,
        )
    }

    /**
     * 경로 카드 한 줄 요약. 경로 점수와 어긋나지 않게, 취약 지점뿐 아니라 그 지점을 지날 시각의 날씨로
     * "보통" 이상인 지점(물 고임·호우 등)도 넣는다 (PLE-27).
     */
    private fun summary(
        p: Prepared, weather: WeatherAlong, vehicle: VehicleSignals, start: Double, excluded: Boolean, routeLevel: RiskLevel,
    ): String {
        val hazards = p.near.map { it.first }
        // 빠진 경로는 지날 시각에 통제되는 곳 때문이므로, 출발할 때 통제 중이 아니면 "통제 예정"으로 쓴다
        val closing = hazards.filter { it.closedFromSeconds != null }
        if (excluded && closing.isNotEmpty()) {
            return closing.joinToString { it.title } + if (closing.all { it.closedAt(start) }) " 통제" else " 통제 예정"
        }
        val risky = p.near.filter { (h, along) ->
            !h.statistical && (h.vulnerability != Vulnerability.NONE || h.icingZone ||
                levelAt(h, p.passAt(along, start), weather, vehicle) >= RiskLevel.MEDIUM)
        }.map { it.first }
        // 결빙 사고 다발지역은 계절과 상관없이 늘 개수로 알린다 (예시 앱의 "결빙 사고가 잦은 곳 N곳")
        val icy = hazards.count { it.statistical && it.icingZone }
        val main = when {
            risky.isNotEmpty() -> risky.joinToString { it.title } + " 지남"
            // 위험 지점은 없지만 경로 전체에 비가 강하면 그렇게 말한다
            routeLevel >= RiskLevel.MEDIUM -> "경로 전체 강한 비"
            icy > 0 -> null
            else -> "위험 구간 없음"
        }
        return listOfNotNull(main, if (icy > 0) "결빙 사고가 잦은 곳 ${icy}곳" else null).joinToString(" · ")
    }

    companion object {
        /** 경로 길이를 m로 반올림 (시나리오 경로처럼 거리 값이 없을 때) */
        fun lengthOf(line: List<LatLng>) = RouteGeometry.lengthMeters(line).roundToInt()
    }
}
