package com.example.pleosproj.scenario

import com.example.pleosproj.risk.BodyType
import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.routing.RiskPoint
import com.example.pleosproj.routing.RouteComparer
import com.example.pleosproj.routing.RoutePlan
import com.example.pleosproj.routing.RouteTurn
import com.example.pleosproj.routing.WeatherSample
import com.example.pleosproj.risk.RiskBand
import com.example.pleosproj.risk.RiskEngine
import com.example.pleosproj.risk.RiskWeights
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.RouteInput
import com.example.pleosproj.risk.RouteSegment
import com.example.pleosproj.risk.SegmentConditions
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.DriveMode
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.Hazard
import com.example.pleosproj.ui.state.HazardAlert
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.ProgressSegment
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.RouteCandidate
import com.example.pleosproj.ui.state.RouteCompareState
import com.example.pleosproj.ui.state.RouteProgress
import com.example.pleosproj.ui.state.SegmentKind
import com.example.pleosproj.ui.state.TurnInstruction
import com.example.pleosproj.ui.state.WeatherCard
import kotlin.math.roundToInt

/**
 * 시나리오를 시간에 따라 화면 상태로 바꾼다. 상태가 없는 순수 계산이라 재생기와 테스트가 같은 코드를 쓴다.
 * 위험도는 RiskEngine(ADR 0003)으로 계산한다.
 */
class ScenarioEngine(
    private val scenario: Scenario,
    private val bodyType: BodyType = BodyType.EV,
) {
    /** 위험 지점이 이 거리 안에 있으면 그 구간에 영향을 준다 */
    private val hazardRadiusMeters = 150.0

    /** 위험 알림을 띄우기 시작하는 거리 */
    private val alertAheadMeters = 1200.0

    private val drive = scenario.driveRoute
    private val driveLength = RouteGeometry.lengthMeters(drive.line)

    // 위험 지점이 운전 경로의 어디쯤(m)에 있는지, 경로에서 얼마나 떨어졌는지 미리 구해 둔다
    private val hazardOnDrive: Map<String, Pair<Double, Double>> =
        scenario.hazards.associate { it.id to RouteGeometry.project(drive.line, it.position) }

    fun vehicleAt(seconds: Double) = VehicleSignals(
        wiper = scenario.vehicle.at(seconds) { it.atSeconds }.wiper,
        outsideTempC = scenario.weather.at(seconds) { it.atSeconds }.temperatureC,
        bodyType = bodyType,
    )

    // --- 경로 비교 ---

    /** 출발 시각(재생 0초) 기준 후보 경로 비교. 실제 길찾기와 같은 계산([RouteComparer])을 쓴다 */
    fun compare(atSeconds: Double = 0.0): RouteCompareState = RouteComparer(hazardRadiusMeters).compare(
        destinationName = scenario.destinationName,
        routes = scenario.routes.map { it.toPlan() },
        hazards = scenario.hazards.map { it.toRiskPoint() },
        weather = { _, seconds -> scenario.weather.at(seconds) { it.atSeconds }.toSample() },
        vehicle = vehicleAt(atSeconds),
        startSeconds = atSeconds,
        departure = scenario.startTime.plusSeconds(atSeconds.toLong()),
    ).copy(forecastBasis = "시연 시나리오의 가정 날씨")

    // --- 출발 전 브리핑 ---

    fun briefing(speaking: Boolean = true): BriefingState {
        val compare = compare(0.0)
        val chosen = compare.candidates.first { it.id == compare.selectedId }
        val lines = buildList {
            compare.candidates.filter { it.excluded }.forEach { add("${it.name} 경로: ${it.riskSummary}") }
            val peak = scenario.weather.maxBy { it.rainMmPerHour }
            if (peak.rainMmPerHour >= 10.0) add("${peak.atSeconds / 60}분 뒤 시간당 ${peak.rainMmPerHour.roundToInt()}mm 비 예보")
            chosen.hazards.filter { it.level >= RiskLevel.MEDIUM }.forEach { add("${it.title} 주의") }
        }
        return BriefingState(
            destinationName = scenario.destinationName,
            arrivalTime = scenario.startTime.plusSeconds(chosen.durationSeconds.toLong()),
            durationSeconds = chosen.durationSeconds,
            riskLines = lines,
            overallLevel = compare.candidates.filterNot { it.excluded }.minOf { it.riskLevel },
            weather = weatherCards(0.0, 0.0),
            speaking = speaking,
        )
    }

    // --- 안내 화면 ---

    /** 재생 시작 후 [seconds]초의 안내 화면 상태. 운전 경로를 일정한 속도로 달린다고 본다. */
    fun guidance(seconds: Double): GuidanceState {
        val ratio = (seconds / drive.durationSeconds).coerceIn(0.0, 1.0)
        val traveled = driveLength * ratio
        val here = RouteGeometry.pointAt(drive.line, traveled)
        val turnsAhead = drive.turns.filter { it.atMeters > traveled }
        val segScores = RouteComparer(hazardRadiusMeters).segmentScores(
            drive.toPlan(), scenario.hazards.map { it.toRiskPoint() },
            { _, t -> scenario.weather.at(t) { it.atSeconds }.toSample() }, vehicleAt(seconds),
        )
        val slices = RouteGeometry.slice(drive.line)

        return GuidanceState(
            mode = DriveMode.SCENARIO,
            destinationName = scenario.destinationName,
            nextTurn = turnsAhead.firstOrNull()?.let { TurnInstruction(it.kind, (it.atMeters - traveled).roundToInt(), it.text) },
            upcomingTurns = turnsAhead.drop(1).take(2).map { TurnInstruction(it.kind, (it.atMeters - traveled).roundToInt(), it.text) },
            remainingMeters = (driveLength - traveled).roundToInt(),
            remainingSeconds = (drive.durationSeconds - seconds).coerceAtLeast(0.0).roundToInt(),
            arrivalTime = scenario.startTime.plusSeconds(drive.durationSeconds.toLong()),
            myPosition = here.toGeo(),
            heading = RouteGeometry.bearingAt(drive.line, traveled),
            routeLine = drive.line.map { it.toGeo() },
            hazards = scenario.hazards.map { uiHazard(it, seconds) },
            progress = RouteProgress(
                currentRatio = ratio,
                totalMeters = driveLength.roundToInt(),
                segments = drive.turns.map {
                    ProgressSegment(SegmentKind.TURN, it.atMeters / driveLength, it.atMeters / driveLength, it.text)
                } + slices.zip(segScores).filter { (_, s) -> s >= RiskWeights.BAND_MEDIUM }.map { (slice, _) ->
                    val h = nearestHazard(slice.middle)
                    ProgressSegment(SegmentKind.HAZARD, slice.startRatio, slice.endRatio, h?.title ?: "위험 구간", h?.kind)
                },
            ),
            weather = guidanceWeatherCards(seconds, traveled),
            alert = alert(seconds, traveled),
        )
    }

    // --- 내부 계산 ---

    /** 경로를 1km 구간으로 나누고, 출발 시각 [startSeconds]에 떠났을 때 각 구간을 지나는 시각의 상황을 붙인다 */
    private fun nearestHazard(p: LatLng) = scenario.hazards
        .minByOrNull { RouteGeometry.distanceMeters(it.position, p) }
        ?.takeIf { RouteGeometry.distanceMeters(it.position, p) <= 700.0 }

    private fun uiHazard(h: ScenarioHazard, seconds: Double): Hazard {
        val (along, off) = hazardOnDrive.getValue(h.id)
        val band = RiskEngine.pointBand(conditionsAt(h, seconds), vehicleAt(seconds))
        return Hazard(
            id = h.id,
            kind = if (h.closedAt(seconds)) HazardKind.CLOSURE else h.kind,
            position = h.position.toGeo(),
            level = if (h.closedAt(seconds)) RiskLevel.HIGH else band.toLevel(),
            title = h.title,
            detail = h.detail,
            ratioOnRoute = if (off <= hazardRadiusMeters) along / driveLength else null,
        )
    }

    private fun conditionsAt(h: ScenarioHazard, seconds: Double): SegmentConditions {
        val w = scenario.weather.at(seconds) { it.atSeconds }
        return SegmentConditions(
            rainMmPerHour = w.rainMmPerHour, snowing = w.snowing, temperatureC = w.temperatureC, warning = w.warning,
            vulnerability = h.vulnerability, icingZone = h.icingZone, closed = h.closedAt(seconds),
        )
    }

    /** 운전 경로 앞쪽 [alertAheadMeters] 안의 가장 가까운 위험 지점으로 알림을 만든다 */
    private fun alert(seconds: Double, traveled: Double): HazardAlert? {
        val ahead = scenario.hazards.mapNotNull { h ->
            val (along, off) = hazardOnDrive.getValue(h.id)
            val dist = along - traveled
            if (off <= hazardRadiusMeters && dist in 0.0..alertAheadMeters) h to dist else null
        }.minByOrNull { it.second } ?: return null
        val (h, dist) = ahead
        val ui = uiHazard(h, seconds)
        if (ui.level < RiskLevel.MEDIUM) return null
        val closed = h.closedAt(seconds)
        return HazardAlert(
            hazardId = h.id,
            title = when {
                closed -> "앞쪽 도로 통제"
                h.kind == HazardKind.FLOOD -> "앞쪽 침수 우려"
                h.kind == HazardKind.ICE -> "앞쪽 결빙 주의"
                else -> "앞쪽 위험 구간"
            },
            message = "${(dist / 100).roundToInt() * 100}m 앞 ${h.title}" + if (closed) ", 우회할까요?" else "",
            level = ui.level,
            suggestsDetour = closed,
        )
    }

    private fun weatherCards(seconds: Double, traveled: Double): List<WeatherCard> {
        val now = scenario.weather.at(seconds) { it.atSeconds }
        // 10분 뒤 날씨: 실제로는 그때 지날 구간의 예보를 쓴다
        val later = scenario.weather.at(seconds + 600) { it.atSeconds }
        return listOf(card("지금", now), card("10분 뒤", later))
    }

    /**
     * 안내 화면 날씨. 실제 안내([com.example.pleosproj.routing.RouteGuidance])와 같이 지금, 10km·20km 앞, 목적지 카드를 만든다.
     * 시나리오 날씨는 시간만 따르므로 앞 지점은 그곳을 지날 시각의 날씨다. 목적지보다 먼 카드는 넣지 않는다.
     */
    private fun guidanceWeatherCards(seconds: Double, traveled: Double): List<WeatherCard> {
        val remaining = (driveLength - traveled).coerceAtLeast(0.0)
        val ahead = listOf(10_000.0, 20_000.0).filter { it < remaining }.map { d ->
            val passAt = seconds + drive.durationSeconds * (d / driveLength)
            card("${(d / 1000).roundToInt()}km 앞", scenario.weather.at(passAt) { it.atSeconds })
        }
        val arrival = seconds + drive.durationSeconds * (remaining / driveLength)
        return listOf(card("지금", scenario.weather.at(seconds) { it.atSeconds })) + ahead +
            card("목적지", scenario.weather.at(arrival) { it.atSeconds })
    }

    private fun card(label: String, w: WeatherKey) = WeatherCard(
        label = label,
        temperatureC = w.temperatureC.roundToInt(),
        precipitationChance = when {
            w.rainMmPerHour >= 10 -> 90
            w.rainMmPerHour > 0 -> 70
            else -> 20
        },
        rainMmPerHour = w.rainMmPerHour,
        snow = w.snowing,
    )

    private fun ScenarioRoute.toPlan() = RoutePlan(
        id = id, name = name, distanceMeters = RouteComparer.lengthOf(line), durationSeconds = durationSeconds,
        line = line, turns = turns.map { RouteTurn(it.atMeters, it.kind, it.text) }, tollWon = tollWon,
    )

    private fun ScenarioHazard.toRiskPoint() = RiskPoint(
        id = id, kind = kind, position = position, title = title, detail = detail,
        vulnerability = vulnerability, icingZone = icingZone, closedFromSeconds = closedFromSeconds,
        statistical = statistical,
    )

    private fun WeatherKey.toSample() = WeatherSample(rainMmPerHour, snowing, temperatureC, warning)

    private fun LatLng.toGeo() = GeoPoint(lat, lng)
}

internal fun RiskBand.toLevel() = when (this) {
    RiskBand.NONE -> RiskLevel.NONE
    RiskBand.LOW -> RiskLevel.LOW
    RiskBand.MEDIUM -> RiskLevel.MEDIUM
    RiskBand.HIGH -> RiskLevel.HIGH
}
