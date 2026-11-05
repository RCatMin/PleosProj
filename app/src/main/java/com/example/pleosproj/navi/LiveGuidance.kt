package com.example.pleosproj.navi

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RiskEngine
import com.example.pleosproj.risk.RiskWeights
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.SegmentConditions
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.risk.Vulnerability
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.scenario.toLevel
import com.example.pleosproj.traffic.TrafficEvent
import com.example.pleosproj.ui.state.DriveMode
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.Hazard
import com.example.pleosproj.ui.state.HazardAlert
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.ProgressSegment
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.RouteProgress
import com.example.pleosproj.ui.state.SegmentKind
import com.example.pleosproj.ui.state.TurnInstruction
import com.example.pleosproj.ui.state.WeatherCard
import com.example.pleosproj.vehicle.VehicleSnapshot
import com.example.pleosproj.weather.WeatherForecast
import com.example.pleosproj.weather.WeatherNow
import com.example.pleosproj.weather.WeatherWarning
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 실제 주행 상태를 만들 때 필요한 값. 없는 값은 비워 두면 그 부분만 빠진다. */
data class LiveInputs(
    val navi: NaviSnapshot,
    val now: LocalDateTime,
    /** 이번 경로의 전체 길이. 회전 안내 목록이 주행 중 줄어들어도 진행률을 맞추려고 처음 길이를 기억해 넘긴다 */
    val routeTotalMeters: Double? = null,
    /** FusedLocation 위치. 없으면 기본 내비가 준 위치를 쓴다 */
    val myLat: Double? = null,
    val myLng: Double? = null,
    val myHeading: Double? = null,
    val weatherHere: WeatherNow? = null,
    val forecastAtDestination: List<WeatherForecast> = emptyList(),
    val warnings: List<WeatherWarning> = emptyList(),
    /** ITS 돌발상황 (내 주변) */
    val trafficEvents: List<TrafficEvent> = emptyList(),
    val vehicle: VehicleSnapshot? = null,
)

/**
 * 실제 주행의 안내 화면 상태를 만든다 (ADR 0003·0005). 경로선이 없으므로 위험은
 * 지금 위치의 날씨와 차량 신호, 회전 안내 속 지하차도, 기본 내비 주의 지점, 교통 이벤트로 판단한다.
 *
 * 경로 위 위치는 "목적지까지 남은 거리" 기준으로 잡는다. 회전 안내 목록이 출발점 기준이든
 * 지금 위치 기준으로 다시 오든 같은 결과가 나온다.
 */
object LiveGuidance {

    /** 앞쪽 위험 지점 알림을 띄우기 시작하는 거리 */
    const val ALERT_AHEAD_METERS = 1200.0

    /** 교통 이벤트를 내 앞쪽으로 볼 직선거리 */
    private const val EVENT_RADIUS_METERS = 2000.0

    fun build(i: LiveInputs): GuidanceState {
        val navi = i.navi
        val listMeters = navi.routeMeters
        val remaining = remainingMeters(navi)
        val total = maxOf(i.routeTotalMeters ?: 0.0, listMeters, remaining, 1.0)
        val traveled = (total - remaining).coerceAtLeast(0.0)
        // 회전 안내 목록이 시작되는 지점(출발점부터 m)
        val listStart = total - listMeters

        val me = LatLng(i.myLat ?: navi.lat ?: 0.0, i.myLng ?: navi.lng ?: 0.0)
        val heading = i.myHeading ?: navi.heading ?: 0.0
        val vehicle = i.vehicle?.toRiskSignals() ?: VehicleSignals()
        val here = conditionsHere(i)

        // 회전 지점마다 출발점부터의 거리
        val turnAt = navi.turns.runningFold(listStart) { acc, t -> acc + t.segmentMeters }.drop(1)
        val turnsAhead = navi.turns.zip(turnAt).filter { (_, at) -> at > traveled + 1 }
        fun instruction(t: NaviTurn, at: Double) = TurnInstruction(t.kind, (at - traveled).roundToInt(), t.text)

        // 경로 위 위험 지점: 지하차도 진입, 주의 지점
        val along = mutableListOf<AlongHazard>()
        navi.turns.zip(turnAt).forEachIndexed { idx, (t, at) ->
            if (!t.entersUnderpass || at <= traveled) return@forEachIndexed
            val score = RiskEngine.segmentScore(here.copy(vulnerability = Vulnerability.UNDERPASS), vehicle)
            along += AlongHazard("underpass-$idx", HazardKind.FLOOD, at, RiskEngine.band(score).toLevel(),
                "지하차도", t.text.ifBlank { "지하차도 진입" })
        }
        navi.cautions.forEach { c ->
            caution(c, here, vehicle)?.let { (kind, level, title) ->
                along += AlongHazard("caution-${c.id}", kind, traveled + c.remainMeters, level, title, "기본 내비 주의 지점")
            }
        }

        val hazards = mutableListOf<Hazard>()
        along.forEach { h ->
            hazards += Hazard(h.id, h.kind, ahead(me, heading, h.at - traveled), h.level, h.title, h.detail, h.at / total)
        }
        val events = events(i, me)
        hazards += events
        val weatherHazard = weatherHazard(i, here, vehicle, me)
        weatherHazard?.let { hazards += it }

        val segments = buildList {
            navi.turns.zip(turnAt).filter { (t, _) -> t.kind != com.example.pleosproj.ui.state.TurnKind.ARRIVE }.forEach { (t, at) ->
                add(ProgressSegment(SegmentKind.TURN, at / total, at / total, t.text))
            }
            navi.congestion.forEach { c ->
                val start = (listStart + c.offsetMeters) / total
                val end = (listStart + c.offsetMeters + c.lengthMeters) / total
                add(ProgressSegment(if (c.heavy) SegmentKind.CONGESTION_HEAVY else SegmentKind.CONGESTION_SLOW,
                    start.coerceIn(0.0, 1.0), end.coerceIn(0.0, 1.0), c.roadName.ifBlank { null }))
            }
            // 위험 지점은 앞뒤 100m 폭으로 보인다
            along.filter { it.level >= RiskLevel.MEDIUM }.forEach { h ->
                add(ProgressSegment(SegmentKind.HAZARD, ((h.at - 100) / total).coerceIn(0.0, 1.0),
                    ((h.at + 100) / total).coerceIn(0.0, 1.0), h.title, h.kind))
            }
        }

        return GuidanceState(
            mode = DriveMode.LIVE,
            destinationName = navi.destinationName,
            nextTurn = turnsAhead.firstOrNull()?.let { (t, at) -> instruction(t, at) },
            upcomingTurns = turnsAhead.drop(1).take(2).map { (t, at) -> instruction(t, at) },
            remainingMeters = navi.remainMeters,
            remainingSeconds = navi.remainSeconds,
            arrivalTime = i.now.plusSeconds(navi.remainSeconds.toLong()).toLocalTime().withNano(0),
            myPosition = GeoPoint(me.lat, me.lng),
            heading = heading,
            routeLine = null,
            hazards = hazards,
            progress = RouteProgress(traveled / total, total.roundToInt(), segments),
            weather = weatherCards(i),
            alert = alert(along, traveled, events, me, heading, weatherHazard, here),
        )
    }

    /**
     * 경로 위치 계산에 쓸 남은 거리. 기본 내비의 남은 거리는 반올림돼서 온다(예: 22000m).
     * 회전 안내 합계가 그 반올림 범위 안이면 합계가 더 정확한 남은 거리이므로 그걸 쓴다.
     * 범위를 벗어나면(목록이 출발점 기준이고 이미 달린 경우) 기본 내비 값을 쓴다.
     */
    internal fun remainingMeters(navi: NaviSnapshot): Double {
        val r = navi.remainMeters
        val list = navi.routeMeters
        if (list <= 0.0) return r.toDouble()
        val tolerance = when {
            r >= 1000 && r % 1000 == 0 -> 500.0
            r % 100 == 0 -> 50.0
            else -> 5.0
        }
        return if (kotlin.math.abs(list - r) <= tolerance) list else r.toDouble()
    }

    // --- 지금 위치의 상황 ---

    /**
     * 지금 위치의 날씨·특보로 위험도 계산 입력을 만든다. 기온은 차에서 읽은 값을 먼저 쓴다.
     * 특보 목록은 전국 단위라 다른 지역 특보가 섞이므로, 지금 위치에 비나 눈이 올 때만 반영한다.
     */
    internal fun conditionsHere(i: LiveInputs): SegmentConditions {
        val w = i.weatherHere
        val wet = (w?.rainMm ?: 0.0) > 0 || w?.precip?.hasSnow == true
        return SegmentConditions(
            rainMmPerHour = w?.rainMm ?: 0.0,
            snowing = w?.precip?.hasSnow ?: false,
            temperatureC = i.vehicle?.outsideTempC ?: w?.temperatureC ?: 15.0,
            warning = if (wet) warningLevel(i.warnings) else WarningLevel.NONE,
        )
    }

    /** 호우·대설 특보가 발표 중이면 그 단계. 경보가 하나라도 있으면 경보로 본다 */
    internal fun warningLevel(warnings: List<WeatherWarning>): WarningLevel {
        val active = warnings.filter { it.active && (it.kind == "호우" || it.kind == "대설") }
        return when {
            active.any { it.level == "경보" } -> WarningLevel.WARNING
            active.isNotEmpty() -> WarningLevel.ADVISORY
            else -> WarningLevel.NONE
        }
    }

    private data class AlongHazard(
        val id: String, val kind: HazardKind, val at: Double, val level: RiskLevel, val title: String, val detail: String,
    )

    /** 주의 지점을 위험 종류·정도·이름으로 바꾼다. 날씨와 관계없는 지점은 null */
    private fun caution(c: NaviCaution, here: SegmentConditions, vehicle: VehicleSignals): Triple<HazardKind, RiskLevel, String>? {
        val wet = here.rainMmPerHour > 0 || here.snowing
        val level = { cond: SegmentConditions -> RiskEngine.band(RiskEngine.segmentScore(cond, vehicle)).toLevel() }
        // 기본 내비가 미끄럽다고 표시한 곳이 영하이거나 눈이 오면 점수와 상관없이 "보통" 이상으로 본다.
        // 위험도 공식에서 결빙 비중(0.15)만으로는 이 경우가 "낮음"에 머물기 때문이다
        val freezing = here.temperatureC <= 0 || here.snowing
        val icy = { maxOf(if (freezing) RiskLevel.MEDIUM else RiskLevel.LOW, level(here.copy(icingZone = true))) }
        return when (c.kind) {
            CautionKind.SLIPPERY_ROAD -> Triple(HazardKind.ICE, icy(), "미끄러운 도로")
            CautionKind.DOWNHILL -> if (wet || here.temperatureC <= 2) Triple(HazardKind.ICE, icy(), "내리막 주의") else null
            CautionKind.FOG -> Triple(HazardKind.FOG, RiskLevel.MEDIUM, "안개 잦은 곳")
            CautionKind.ACCIDENT_PRONE -> Triple(HazardKind.ACCIDENT, if (wet) RiskLevel.MEDIUM else RiskLevel.LOW, "사고 잦은 곳")
            CautionKind.ROAD_DAMAGE -> Triple(HazardKind.CONSTRUCTION, if (wet) RiskLevel.MEDIUM else RiskLevel.LOW, "도로 파손")
            CautionKind.CONSTRUCTION -> Triple(HazardKind.CONSTRUCTION, RiskLevel.LOW, "공사 구간")
            CautionKind.FALLING_ROCK -> Triple(HazardKind.CONSTRUCTION, if (wet) RiskLevel.MEDIUM else RiskLevel.LOW, "낙석 주의")
        }
    }

    /** 기본 내비 교통 이벤트와 ITS 돌발상황 중 내 주변 것 */
    private fun events(i: LiveInputs, me: LatLng): List<Hazard> {
        val fromNavi = i.navi.events.mapIndexed { idx, e ->
            Hazard("navi-event-$idx", e.kind, GeoPoint(e.lat, e.lng), if (e.closure) RiskLevel.HIGH else RiskLevel.MEDIUM,
                if (e.closure) "도로 통제" else eventTitle(e.kind), e.description)
        }
        val fromIts = i.trafficEvents.mapIndexed { idx, e ->
            val kind = when {
                e.isClosure -> HazardKind.CLOSURE
                e.eventType.contains("사고") -> HazardKind.ACCIDENT
                e.eventType.contains("기상") -> if (e.message.contains("눈") || e.message.contains("결빙")) HazardKind.ICE else HazardKind.FLOOD
                else -> HazardKind.CONSTRUCTION
            }
            Hazard("its-$idx", kind, GeoPoint(e.lat, e.lng), if (e.isClosure) RiskLevel.HIGH else RiskLevel.MEDIUM,
                listOf(e.roadName, eventTitle(kind)).filter { it.isNotBlank() }.joinToString(" "), e.message)
        }
        return (fromNavi + fromIts).filter {
            RouteGeometry.distanceMeters(me, LatLng(it.position.lat, it.position.lng)) <= EVENT_RADIUS_METERS * 2
        }
    }

    private fun eventTitle(kind: HazardKind) = when (kind) {
        HazardKind.CLOSURE -> "통제"
        HazardKind.ACCIDENT -> "사고"
        HazardKind.FLOOD -> "침수"
        HazardKind.ICE -> "결빙"
        else -> "공사"
    }

    /** 지금 위치의 비·눈이 위험 수준이면 내 위치에 위험 표시를 하나 둔다 */
    private fun weatherHazard(i: LiveInputs, here: SegmentConditions, vehicle: VehicleSignals, me: LatLng): Hazard? {
        val score = RiskEngine.segmentScore(here, vehicle)
        if (score < RiskWeights.BAND_MEDIUM) return null
        val snow = here.snowing
        val rain = here.rainMmPerHour.roundToInt()
        return Hazard(
            id = "weather-here",
            kind = if (snow) HazardKind.HEAVY_SNOW else HazardKind.HEAVY_RAIN,
            position = GeoPoint(me.lat, me.lng),
            level = RiskEngine.band(score).toLevel(),
            title = if (snow) "지금 위치 눈" else "지금 위치 강한 비",
            detail = buildString {
                if (!snow) append("시간당 ${rain}mm")
                if (i.vehicle?.wiper == com.example.pleosproj.risk.WiperLevel.HIGH) append(if (isEmpty()) "와이퍼 최고 단계" else ", 와이퍼 최고 단계")
                if (here.warning != WarningLevel.NONE) append(if (isEmpty()) "" else ", ").append(if (here.warning == WarningLevel.WARNING) "특보(경보) 발표 중" else "특보(주의보) 발표 중")
            },
        )
    }

    /** 통제 → 앞쪽 위험 지점 → 지금 위치 날씨 순으로 하나만 고른다 */
    private fun alert(
        along: List<AlongHazard>, traveled: Double, events: List<Hazard>, me: LatLng, heading: Double,
        weatherHazard: Hazard?, here: SegmentConditions,
    ): HazardAlert? {
        val closure = events
            .filter { it.kind == HazardKind.CLOSURE }
            .map { it to RouteGeometry.distanceMeters(me, LatLng(it.position.lat, it.position.lng)) }
            .filter { (h, d) -> d <= EVENT_RADIUS_METERS && isAhead(me, heading, h.position) }
            .minByOrNull { it.second }
        if (closure != null) {
            val (h, d) = closure
            return HazardAlert(h.id, "앞쪽 도로 통제", "${roundTo100(d)}m 앞 ${h.title}, 우회할까요?", RiskLevel.HIGH, suggestsDetour = true)
        }
        val next = along
            .filter { it.level >= RiskLevel.MEDIUM && it.at - traveled in 0.0..ALERT_AHEAD_METERS }
            .minByOrNull { it.at }
        if (next != null) {
            val title = when (next.kind) {
                HazardKind.FLOOD -> "앞쪽 침수 우려"
                HazardKind.ICE -> "앞쪽 미끄럼 주의"
                HazardKind.FOG -> "앞쪽 안개 주의"
                else -> "앞쪽 위험 구간"
            }
            return HazardAlert(next.id, title, "${roundTo100(next.at - traveled)}m 앞 ${next.title}", next.level, suggestsDetour = false)
        }
        if (weatherHazard != null && weatherHazard.level >= RiskLevel.MEDIUM) {
            return HazardAlert(weatherHazard.id, weatherHazard.title, "${weatherHazard.detail}. 속도를 줄이세요",
                weatherHazard.level, suggestsDetour = false)
        }
        return null
    }

    private fun weatherCards(i: LiveInputs): List<WeatherCard> = buildList {
        i.weatherHere?.let { w ->
            add(WeatherCard("지금", (i.vehicle?.outsideTempC ?: w.temperatureC).roundToInt(), chance(w.rainMm, w.precip.hasSnow),
                w.rainMm, w.precip.hasSnow))
        }
        // 도착할 무렵 목적지 날씨: 도착 시각 이후 가장 가까운 예보
        val arrival = i.now.plusSeconds(i.navi.remainSeconds.toLong())
        i.forecastAtDestination.filter { !it.time.isBefore(arrival.withMinute(0).withSecond(0).withNano(0)) }
            .minByOrNull { it.time }
            ?.let { f -> add(WeatherCard("목적지", f.temperatureC.roundToInt(), chance(f.rainMm, f.precip.hasSnow), f.rainMm, f.precip.hasSnow)) }
    }

    /** 초단기예보에는 강수확률이 없어서 강수량으로 대략 나타낸다 */
    private fun chance(rainMm: Double, snow: Boolean) = when {
        rainMm >= 10 -> 90
        rainMm > 0 || snow -> 70
        else -> 10
    }

    private fun roundTo100(m: Double) = ((m / 100).roundToInt() * 100).coerceAtLeast(100)

    /** [heading] 방향으로 [meters]만큼 간 지점. 경로선이 없어서 경로 위 위험 지점의 지도 위치는 대략으로만 잡는다 */
    internal fun ahead(from: LatLng, heading: Double, meters: Double): GeoPoint {
        val r = 6_371_000.0
        val d = meters.coerceAtLeast(0.0) / r
        val b = Math.toRadians(heading)
        val lat1 = Math.toRadians(from.lat)
        val lng1 = Math.toRadians(from.lng)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lng2 = lng1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /** 진행 방향 앞쪽 ±60도 안에 있는지 */
    private fun isAhead(me: LatLng, heading: Double, p: GeoPoint): Boolean {
        val b = RouteGeometry.bearing(me, LatLng(p.lat, p.lng))
        val diff = abs(((b - heading + 540) % 360) - 180)
        return diff <= 60
    }
}
