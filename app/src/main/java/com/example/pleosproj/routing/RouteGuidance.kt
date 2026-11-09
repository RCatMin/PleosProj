package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RiskEngine
import com.example.pleosproj.risk.RiskWeights
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.SegmentConditions
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.scenario.toLevel
import com.example.pleosproj.ui.state.DriveMode
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.Hazard
import com.example.pleosproj.ui.state.HazardAlert
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.ProgressSegment
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.RouteFacility
import com.example.pleosproj.ui.state.RouteProgress
import com.example.pleosproj.ui.state.SegmentKind
import com.example.pleosproj.ui.state.TurnInstruction
import com.example.pleosproj.ui.state.TurnKind
import com.example.pleosproj.ui.state.WeatherCard
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 우리 경로를 따라가는 안내 화면 상태를 만든다 (ADR 0007). 경로선이 있으므로 지도에 그리고,
 * 경로 위 위험 지점과 진행 막대 위치를 정확히 잡는다. 순수 계산이라 단위 테스트한다.
 */
class RouteGuidance(
    val route: RoutePlan,
    private val destinationName: String,
    hazards: List<RiskPoint>,
    private val weather: WeatherAlong,
    /** 경로 계산 시각. [weather]의 초는 이 시각부터 센다 */
    private val plannedAt: LocalDateTime,
    vehicle: VehicleSignals,
    private val hazardRadiusMeters: Double = 150.0,
    private val alertAheadMeters: Double = 1200.0,
) {
    private val length = RouteGeometry.lengthMeters(route.line).coerceAtLeast(1.0)

    /** 이 경로 위(반경 안)의 위험 지점과 경로 위 위치(m) */
    private val onRoute: List<Pair<RiskPoint, Double>> = hazards.mapNotNull { h ->
        val (along, off) = RouteGeometry.project(route.line, h.position)
        if (off <= hazardRadiusMeters) h to along else null
    }

    // 진행 막대의 위험 구간은 출발할 때 한 번 계산한다
    private val hazardSegments: List<ProgressSegment> = run {
        val scores = RouteComparer(hazardRadiusMeters).segmentScores(route, onRoute.map { it.first }, weather, vehicle)
        RouteGeometry.slice(route.line).zip(scores).filter { (_, s) -> s >= RiskWeights.BAND_MEDIUM }.map { (slice, _) ->
            val h = onRoute.firstOrNull { (_, along) -> along / length in slice.startRatio..slice.endRatio }?.first
            ProgressSegment(SegmentKind.HAZARD, slice.startRatio, slice.endRatio, h?.title ?: "위험 구간", h?.kind)
        }
    }

    // 길찾기가 알려 준 막히는 구간
    private val congestionSegments: List<ProgressSegment> = route.congestion.map {
        ProgressSegment(if (it.heavy) SegmentKind.CONGESTION_HEAVY else SegmentKind.CONGESTION_SLOW,
            (it.startMeters / length).coerceIn(0.0, 1.0), (it.endMeters / length).coerceIn(0.0, 1.0), it.roadName.ifBlank { null })
    }

    fun state(traveled: Double, position: LatLng, heading: Double?, vehicle: VehicleSignals, now: LocalDateTime): GuidanceState {
        val ratio = (traveled / length).coerceIn(0.0, 1.0)
        val remaining = (length - traveled).coerceAtLeast(0.0)
        // OSRM 소요 시간을 남은 거리 비율로 나눈다 (교통 미반영)
        val remainingSeconds = (route.durationSeconds * (1 - ratio)).roundToInt()
        val elapsed = java.time.Duration.between(plannedAt, now).seconds.toDouble()
        val turnsAhead = route.turns.filter { it.atMeters > traveled + 1 }
        fun instruction(t: RouteTurn) = TurnInstruction(t.kind, (t.atMeters - traveled).roundToInt(), t.text)

        val hazards = onRoute.map { (h, along) ->
            val passAt = elapsed + route.durationSeconds * ((along - traveled).coerceAtLeast(0.0) / length)
            Hazard(
                id = h.id,
                kind = if (h.closedAt(passAt)) HazardKind.CLOSURE else h.kind,
                position = GeoPoint(h.position.lat, h.position.lng),
                level = level(h, passAt, vehicle),
                title = h.title,
                detail = h.detail,
                ratioOnRoute = along / length,
            )
        }

        return GuidanceState(
            mode = DriveMode.LIVE,
            destinationName = destinationName,
            nextTurn = turnsAhead.firstOrNull()?.let(::instruction),
            upcomingTurns = turnsAhead.drop(1).take(2).map(::instruction),
            remainingMeters = remaining.roundToInt(),
            remainingSeconds = remainingSeconds,
            arrivalTime = now.plusSeconds(remainingSeconds.toLong()).toLocalTime().withNano(0),
            myPosition = GeoPoint(position.lat, position.lng),
            heading = heading ?: RouteGeometry.bearingAt(route.line, traveled),
            routeLine = route.line.map { GeoPoint(it.lat, it.lng) },
            hazards = hazards,
            progress = RouteProgress(
                currentRatio = ratio,
                totalMeters = length.roundToInt(),
                segments = route.turns.filter { it.kind != TurnKind.ARRIVE }.map {
                    ProgressSegment(SegmentKind.TURN, it.atMeters / length, it.atMeters / length, it.text.ifBlank { null })
                } + congestionSegments + hazardSegments,
            ),
            weather = weatherCards(position, traveled, elapsed, remainingSeconds),
            alert = alert(hazards, traveled),
            facilities = route.facilities.filter { it.atMeters > traveled }.take(MAX_FACILITIES).map {
                RouteFacility(it.kind, it.name, (it.atMeters - traveled).roundToInt())
            },
        )
    }

    private fun level(h: RiskPoint, seconds: Double, vehicle: VehicleSignals): RiskLevel {
        if (h.closedAt(seconds)) return RiskLevel.HIGH
        val w = weather.at(h.position, seconds)
        val c = SegmentConditions(w.rainMmPerHour, w.snowing, w.temperatureC, w.warning, h.vulnerability, h.icingZone)
        return RiskEngine.pointBand(c, vehicle).toLevel()
    }

    /** 앞쪽 [alertAheadMeters] 안의 가장 가까운 "보통" 이상 위험 지점. 통제면 우회를 제안한다 */
    private fun alert(hazards: List<Hazard>, traveled: Double): HazardAlert? {
        val (h, dist) = hazards.mapNotNull { h ->
            val d = (h.ratioOnRoute ?: return@mapNotNull null) * length - traveled
            if (d in 0.0..alertAheadMeters && h.level >= RiskLevel.MEDIUM) h to d else null
        }.minByOrNull { it.second } ?: return null
        val closed = h.kind == HazardKind.CLOSURE
        return HazardAlert(
            hazardId = h.id,
            title = when {
                closed -> "앞쪽 도로 통제"
                h.kind == HazardKind.FLOOD -> "앞쪽 침수 우려"
                h.kind == HazardKind.ICE -> "앞쪽 결빙 주의"
                else -> "앞쪽 위험 구간"
            },
            message = "${((dist / 100).roundToInt() * 100).coerceAtLeast(100)}m 앞 ${h.title}" + if (closed) ", 우회할까요?" else "",
            level = h.level,
            suggestsDetour = closed,
        )
    }

    /**
     * 지금 위치, 10km·20km 앞, 도착할 무렵 목적지 날씨. 앞 지점은 지날 예상 시각의 예보다.
     * 목적지보다 먼 거리의 카드는 넣지 않는다 (가짜 값을 만들지 않는다)
     */
    private fun weatherCards(position: LatLng, traveled: Double, elapsed: Double, remainingSeconds: Int): List<WeatherCard> {
        val remaining = (length - traveled).coerceAtLeast(0.0)
        val ahead = AHEAD_METERS.filter { it < remaining }.map { d ->
            val passAt = elapsed + route.durationSeconds * (d / length)
            weather.at(RouteGeometry.pointAt(route.line, traveled + d), passAt).toCard("${(d / 1000).roundToInt()}km 앞")
        }
        return listOf(weather.at(position, elapsed).toCard("지금")) + ahead +
            weather.at(route.line.last(), elapsed + remainingSeconds).toCard("목적지")
    }

    private companion object {
        val AHEAD_METERS = listOf(10_000.0, 20_000.0)
        const val MAX_FACILITIES = 5
    }

    /** [id] 위험 지점 위치 (우회할 때 쓴다) */
    fun hazardPosition(id: String): LatLng? = onRoute.firstOrNull { it.first.id == id }?.first?.position
}
