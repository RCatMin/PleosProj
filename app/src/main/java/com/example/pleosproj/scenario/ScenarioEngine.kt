package com.example.pleosproj.scenario

import com.example.pleosproj.risk.BodyType
import com.example.pleosproj.risk.LatLng
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

    /** 출발 시각(재생 0초) 기준 후보 경로 비교 */
    fun compare(atSeconds: Double = 0.0): RouteCompareState {
        val vehicle = vehicleAt(atSeconds)
        val inputs = scenario.routes.map { RouteInput(it.id, segments(it, atSeconds)) }
        val rec = RiskEngine.recommend(inputs, vehicle)
        val candidates = scenario.routes.map { route ->
            val a = rec.assessments.first { it.id == route.id }
            val hazards = hazardsNear(route, atSeconds)
            RouteCandidate(
                id = route.id,
                name = route.name,
                durationSeconds = route.durationSeconds,
                distanceMeters = RouteGeometry.lengthMeters(route.line).roundToInt(),
                tollWon = route.tollWon,
                riskScore = a.score,
                riskLevel = a.band.toLevel(),
                excluded = a.excluded,
                riskSummary = summary(route, hazards, atSeconds, a.excluded, a.band.toLevel()),
                routeLine = route.line.map { it.toGeo() },
                hazards = hazards.map { uiHazard(it, atSeconds) },
            )
        }
        return RouteCompareState(
            destinationName = scenario.destinationName,
            candidates = candidates,
            selectedId = rec.recommendedId ?: candidates.first().id,
            recommendedId = rec.recommendedId,
            adviceWhenAllRisky = if (rec.suggestPostpone) "모든 경로가 위험해요. 비가 약해질 때까지 출발을 미루는 게 좋아요." else null,
        )
    }

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
        val segs = segments(drive, 0.0)
        val segScores = RiskEngine.assess(RouteInput(drive.id, segs), vehicleAt(seconds)).segmentScores
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
            weather = weatherCards(seconds, traveled),
            alert = alert(seconds, traveled),
        )
    }

    // --- 내부 계산 ---

    /** 경로를 1km 구간으로 나누고, 출발 시각 [startSeconds]에 떠났을 때 각 구간을 지나는 시각의 상황을 붙인다 */
    private fun segments(route: ScenarioRoute, startSeconds: Double): List<RouteSegment> {
        val length = RouteGeometry.lengthMeters(route.line)
        // 경로에서 반경 안에 있는 위험 지점과, 그 지점이 경로의 몇 % 위치인지
        val onRoute = scenario.hazards.mapNotNull { h ->
            val (along, off) = RouteGeometry.project(route.line, h.position)
            if (off <= hazardRadiusMeters) h to along / length else null
        }
        return RouteGeometry.slice(route.line).map { slice ->
            val passAt = startSeconds + route.durationSeconds * (slice.startRatio + slice.endRatio) / 2
            val w = scenario.weather.at(passAt) { it.atSeconds }
            val near = onRoute.filter { (_, r) -> r >= slice.startRatio && r <= slice.endRatio }.map { it.first }
            RouteSegment(
                lengthMeters = slice.lengthMeters,
                travelSeconds = route.durationSeconds * (slice.endRatio - slice.startRatio),
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
    }

    private fun hazardsNear(route: ScenarioRoute, @Suppress("UNUSED_PARAMETER") atSeconds: Double) = scenario.hazards.filter { h ->
        RouteGeometry.project(route.line, h.position).second <= hazardRadiusMeters
    }

    private fun nearestHazard(p: LatLng) = scenario.hazards
        .minByOrNull { RouteGeometry.distanceMeters(it.position, p) }
        ?.takeIf { RouteGeometry.distanceMeters(it.position, p) <= 700.0 }

    private fun uiHazard(h: ScenarioHazard, seconds: Double): Hazard {
        val (along, off) = hazardOnDrive.getValue(h.id)
        val score = RiskEngine.segmentScore(conditionsAt(h, seconds), vehicleAt(seconds))
        return Hazard(
            id = h.id,
            kind = if (h.closedAt(seconds)) HazardKind.CLOSURE else h.kind,
            position = h.position.toGeo(),
            level = if (h.closedAt(seconds)) RiskLevel.HIGH else RiskEngine.band(score).toLevel(),
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

    /**
     * 경로 카드에 보일 한 줄 요약. 경로 점수와 어긋나지 않게,
     * 취약 지점뿐 아니라 그 지점을 지날 시각의 날씨로 "보통" 이상인 지점(물 고임·호우 등)도 넣는다 (PLE-27).
     */
    private fun summary(
        route: ScenarioRoute, hazards: List<ScenarioHazard>, seconds: Double, excluded: Boolean, routeLevel: RiskLevel,
    ): String {
        // 빠진 경로는 지날 시각에 통제되는 곳 때문이므로, 지금 통제 중이 아니면 "통제 예정"으로 쓴다
        val closing = hazards.filter { it.closedFromSeconds != null }
        if (excluded && closing.isNotEmpty()) {
            return closing.joinToString { it.title } + if (closing.all { it.closedAt(seconds) }) " 통제" else " 통제 예정"
        }
        val risky = hazards.filter {
            it.vulnerability != Vulnerability.NONE || it.icingZone || uiHazard(it, passAt(route, it, seconds)).level >= RiskLevel.MEDIUM
        }
        return when {
            risky.isNotEmpty() -> risky.joinToString { it.title } + " 지남"
            // 위험 지점은 없지만 경로 전체에 비가 강하면 그렇게 말한다
            routeLevel >= RiskLevel.MEDIUM -> "경로 전체 강한 비"
            else -> "위험 구간 없음"
        }
    }

    /** 출발 시각 [startSeconds]에 떠났을 때 [route]에서 [h] 지점을 지나는 시각 */
    private fun passAt(route: ScenarioRoute, h: ScenarioHazard, startSeconds: Double): Double {
        val along = RouteGeometry.project(route.line, h.position).first
        return startSeconds + route.durationSeconds * along / RouteGeometry.lengthMeters(route.line)
    }

    private fun LatLng.toGeo() = GeoPoint(lat, lng)
}

internal fun RiskBand.toLevel() = when (this) {
    RiskBand.NONE -> RiskLevel.NONE
    RiskBand.LOW -> RiskLevel.LOW
    RiskBand.MEDIUM -> RiskLevel.MEDIUM
    RiskBand.HIGH -> RiskLevel.HIGH
}
