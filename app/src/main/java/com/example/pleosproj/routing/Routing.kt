package com.example.pleosproj.routing

import android.util.Log
import com.example.pleosproj.BuildConfig
import com.example.pleosproj.traffic.ItsClient
import com.example.pleosproj.weather.KmaClient
import com.example.pleosproj.navi.LiveGuidance
import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.traffic.TrafficEvent
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.RouteCompareState
import com.example.pleosproj.weather.KmaGrid
import com.example.pleosproj.weather.KmaGridPoint
import com.example.pleosproj.weather.WeatherForecast
import com.example.pleosproj.weather.WeatherWarning
import java.time.LocalDateTime

/** 경로를 계산하지 못했을 때. [message]는 화면에 그대로 보여도 되는 문장이다 */
class RoutingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 길찾기 결과: 비교 화면 상태와, 안내에 쓸 경로들 */
data class RoutePlanResult(
    val compare: RouteCompareState,
    val routes: List<RoutePlan>,
    /** 날씨를 못 받아 비 없이 계산했는지 */
    val weatherMissing: Boolean,
    /** 안내 중에도 쓰는 위험 지점과 날씨 (다시 받지 않는다) */
    val hazards: List<RiskPoint> = emptyList(),
    val weather: WeatherAlong = WeatherAlong { _, _ -> WeatherSample(0.0, false, 15.0, WarningLevel.NONE) },
    val plannedAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * 우리 길찾기 (ADR 0007). 후보 경로를 받아 고르고, 경로마다 지날 시각의 기상청 예보·특보와
 * ITS 돌발상황(통제·사고)으로 위험도를 비교한다. 네트워크 스레드에서 부른다.
 *
 * 외부 값은 함수로 받아서 테스트에서 가짜로 바꿀 수 있다. 실제 연결은 [Routing.create] 참고.
 */
class Routing(
    private val routes: RouteSource,
    private val forecast: ((KmaGridPoint) -> List<WeatherForecast>)?,
    private val warnings: (() -> List<WeatherWarning>)?,
    private val events: ((minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) -> List<TrafficEvent>)?,
    private val comparer: RouteComparer = RouteComparer(),
) {
    /** [from]에서 [to]까지 후보 경로와 위험도 비교. [heading]은 지금 달리는 방향(모르면 null) */
    fun plan(
        from: LatLng, to: LatLng, destinationName: String, vehicle: VehicleSignals,
        heading: Double? = null, now: LocalDateTime = LocalDateTime.now(),
    ): RoutePlanResult {
        val t0 = System.currentTimeMillis()
        val candidates = candidates(from, to, heading)
        if (candidates.isEmpty()) throw RoutingException("갈 수 있는 경로를 찾지 못했어요")
        debug("후보 경로 ${candidates.size}개: ${System.currentTimeMillis() - t0}ms")
        return compare(candidates, destinationName, vehicle, now).also { debug("위험도 비교까지: ${System.currentTimeMillis() - t0}ms") }
    }

    /**
     * [avoid] 지점을 피해 [to]로 가는 경로. 대안 경로 중 피해 가는 것이 있으면 그걸 쓰고,
     * 없으면 그 지점 양옆에 경유 지점을 찍어 다시 계산한다. 못 찾으면 null
     */
    fun detour(from: LatLng, to: LatLng, avoid: LatLng, heading: Double? = null): RoutePlan? {
        val direct = fetch(listOf(from, to), alternatives = true, heading)
        direct.filter { Candidates.avoids(it, avoid) }.minByOrNull { it.durationSeconds }?.let { return it }
        return Candidates.avoidVias(from, avoid)
            .flatMap { via -> fetch(listOf(from, via, to), alternatives = false, heading) }
            .filter { Candidates.avoids(it, avoid) }
            .minByOrNull { it.durationSeconds }
    }

    /** 이미 정한 경로들을 다시 비교한다 (재탐색·우회 뒤 위험도 갱신) */
    fun compare(
        candidates: List<RoutePlan>, destinationName: String, vehicle: VehicleSignals, now: LocalDateTime = LocalDateTime.now(),
    ): RoutePlanResult {
        val t0 = System.currentTimeMillis()
        val (weather, missing) = weatherAlong(candidates, now)
        debug("날씨: ${System.currentTimeMillis() - t0}ms")
        val hazards = riskPoints(candidates)
        val state = comparer.compare(destinationName, candidates, hazards, weather, vehicle)
        return RoutePlanResult(state, candidates, missing, hazards, weather, now)
    }

    // --- 후보 경로 ---

    private fun candidates(from: LatLng, to: LatLng, givenHeading: Double?): List<RoutePlan> {
        // 달리는 방향을 모르면 목적지 쪽을 보고 출발한다고 본다. 반대 차로에 붙어 유턴부터 하는 경로를 막는다
        val heading = givenHeading ?: RouteGeometry.bearing(from, to)
        val found = fetch(listOf(from, to), alternatives = true, heading).toMutableList()
        if (found.isEmpty()) return emptyList()
        // 대안이 모자라면 양옆 경유 지점으로 다른 길을 더 받아 온다
        if (Candidates.pickDistinct(found).size < 3) {
            Candidates.sideVias(from, to).forEach { via ->
                found += runCatching { fetch(listOf(from, via, to), alternatives = false, heading) }.getOrDefault(emptyList())
            }
        }
        return Candidates.nameDistinct(Candidates.pickDistinct(found)).mapIndexed { i, r -> r.copy(id = "route-$i") }
    }

    private fun fetch(points: List<LatLng>, alternatives: Boolean, heading: Double?): List<RoutePlan> =
        try {
            routes.route(points, alternatives, heading)
        } catch (e: OsrmParser.OsrmException) {
            throw RoutingException("길찾기 서버가 경로를 주지 않았어요", e)
        } catch (e: java.io.IOException) {
            throw RoutingException("길찾기 서버에 연결하지 못했어요. 네트워크를 확인해 주세요", e)
        }

    // --- 날씨 ---

    /**
     * 경로가 지나는 기상청 격자마다 초단기예보를 받아, 지점·시각별 날씨를 돌려준다.
     * 예보를 못 받으면 비 없음으로 계산하고 [Pair.second]를 true로 둔다
     */
    private fun weatherAlong(routes: List<RoutePlan>, now: LocalDateTime): Pair<WeatherAlong, Boolean> {
        val fetchForecast = forecast ?: return dry to true
        val grids = routes.flatMap { r -> RouteGeometry.slice(r.line).map { KmaGrid.fromLatLng(it.middle.lat, it.middle.lng) } }.toSet()
        // 격자가 많으면(장거리) 하나씩 물으면 오래 걸려서 4개씩 동시에 묻는다
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val futures = try {
            grids.associateWith { g -> pool.submit<List<WeatherForecast>> { fetchForecast(g) } }
        } finally {
            pool.shutdown()
        }
        var missing = false
        val byGrid = futures.mapValues { (g, f) ->
            runCatching { f.get(20, java.util.concurrent.TimeUnit.SECONDS) }.getOrElse {
                warn("격자 $g 예보 실패: ${it.message}")
                missing = true
                emptyList()
            }
        }
        val level = warnings?.let { w -> runCatching { LiveGuidance.warningLevel(w()) }.getOrDefault(WarningLevel.NONE) } ?: WarningLevel.NONE
        val along = WeatherAlong { point, seconds ->
            val time = now.plusSeconds(seconds.toLong())
            val f = byGrid[KmaGrid.fromLatLng(point.lat, point.lng)].orEmpty()
                .minByOrNull { kotlin.math.abs(java.time.Duration.between(it.time, time).toMinutes()) }
            if (f == null) dryAt(15.0)
            else WeatherSample(
                rainMmPerHour = f.rainMm,
                snowing = f.precip.hasSnow,
                temperatureC = f.temperatureC,
                // 특보 목록은 전국 단위라 그 지점에 비·눈이 올 때만 반영한다 (LiveGuidance와 같은 규칙)
                warning = if (f.rainMm > 0 || f.precip.hasSnow) level else WarningLevel.NONE,
            )
        }
        return along to missing
    }

    // --- 위험 지점 ---

    /** 경로들이 지나는 범위의 ITS 돌발상황을 위험 지점으로 바꾼다 */
    private fun riskPoints(routes: List<RoutePlan>): List<RiskPoint> {
        val fetchEvents = events ?: return emptyList()
        val all = routes.flatMap { it.line }
        val margin = 0.005
        val list = runCatching {
            fetchEvents(all.minOf { it.lat } - margin, all.minOf { it.lng } - margin, all.maxOf { it.lat } + margin, all.maxOf { it.lng } + margin)
        }.getOrElse {
            warn("돌발상황 실패: ${it.message}")
            emptyList()
        }
        return list.mapIndexed { i, e -> riskPoint(i, e) }
    }

    companion object {
        private const val TAG = "Routing"

        // 단위 테스트(JVM)에서는 android.util.Log가 없으므로 실패해도 넘어간다
        private fun warn(message: String) = runCatching { Log.w(TAG, message) }
        private fun debug(message: String) = runCatching { Log.d(TAG, message) }
        private val dry = WeatherAlong { _, _ -> dryAt(15.0) }
        private fun dryAt(tempC: Double) = WeatherSample(0.0, false, tempC, WarningLevel.NONE)

        internal fun riskPoint(i: Int, e: TrafficEvent): RiskPoint {
            val kind = when {
                e.isClosure -> HazardKind.CLOSURE
                e.eventType.contains("사고") -> HazardKind.ACCIDENT
                e.eventType.contains("기상") -> if (e.message.contains("눈") || e.message.contains("결빙")) HazardKind.ICE else HazardKind.FLOOD
                else -> HazardKind.CONSTRUCTION
            }
            val title = listOf(e.roadName, when (kind) {
                HazardKind.CLOSURE -> "통제"
                HazardKind.ACCIDENT -> "사고"
                HazardKind.ICE -> "결빙"
                HazardKind.FLOOD -> "침수"
                else -> "공사"
            }).filter { it.isNotBlank() }.joinToString(" ")
            return RiskPoint(
                id = "its-$i",
                kind = kind,
                position = LatLng(e.lat, e.lng),
                title = title,
                detail = e.message,
                icingZone = kind == HazardKind.ICE,
                // 통제는 지금부터 막힌 것으로 본다
                closedFromSeconds = if (e.isClosure) 0 else null,
            )
        }

        /** 실제 연결: OSRM 공개 서버, 기상청, ITS. 키가 없으면 그 부분만 빼고 계산한다 */
        fun create(): Routing {
            val kma = BuildConfig.KMA_SERVICE_KEY.takeIf { it.isNotBlank() }?.let { KmaClient(it) }
            val its = BuildConfig.ITS_API_KEY.takeIf { it.isNotBlank() }?.let { ItsClient(it) }
            return Routing(
                routes = OsrmClient(),
                forecast = kma?.let { k -> { grid: KmaGridPoint -> k.forecast(grid) } },
                warnings = kma?.let { k -> { k.warnings(LocalDateTime.now().minusDays(1)).sortedByDescending { it.issuedAt }.distinctBy { it.kind to it.level } } },
                events = its?.let { c -> { a: Double, b: Double, cc: Double, d: Double -> c.events(a, b, cc, d) } },
            )
        }
    }
}
