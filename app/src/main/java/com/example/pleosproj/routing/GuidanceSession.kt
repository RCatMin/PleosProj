package com.example.pleosproj.routing

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.pleosproj.BuildConfig
import com.example.pleosproj.location.LocationFix
import com.example.pleosproj.location.LocationListener
import com.example.pleosproj.location.Locations
import com.example.pleosproj.navi.GuidanceVoice
import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.ui.state.Place
import com.example.pleosproj.vehicle.VehicleSignalReader
import com.example.pleosproj.vehicle.VehicleSnapshot
import org.json.JSONArray
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.Executors

/**
 * 우리 내비의 실제 주행 세션 (ADR 0007). 목적지까지 후보 경로를 계산하고([plan]), 고른 경로로 안내를 시작하면
 * 내 위치(FusedLocation)를 따라 1초마다 안내 화면 상태를 넘기고 안내 음성을 낸다. 경로를 벗어나면 다시 계산하고,
 * 통제 알림에서 우회를 고르면 그 지점을 피하는 경로로 바꾼다. 안내 중에도 1분마다 ITS 돌발상황을 다시 받는다. 도착하거나 종료하면 [onState]에 null을 넘긴다.
 *
 * 메인 스레드에서 만들고 부른다. 콜백도 메인 스레드에서 온다.
 */
class GuidanceSession(
    private val context: Context,
    private val onState: (GuidanceState?) -> Unit,
    private val onEvent: (Event) -> Unit = {},
    private val routing: Routing = Routing.create(),
) {
    sealed interface Event {
        /** 경로를 벗어나 다시 계산한 경로로 안내를 바꿨다 */
        data object Rerouted : Event
        /** 우회 경로로 바꿨다 */
        data object Detoured : Event
        /** 우회 경로를 찾지 못했다 */
        data object DetourFailed : Event
        data object Arrived : Event
        /** 재탐색·우회 계산이 실패했다. [message]는 화면에 그대로 보여도 된다 */
        data class Failed(val message: String) : Event
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val locations = Locations(context)
    private val vehicleReader = VehicleSignalReader(context) { vehicle = it }
    private val voice = GuidanceVoice(context)

    @Volatile private var fix: LocationFix? = null
    @Volatile private var vehicle: VehicleSnapshot? = null

    private var lastPlan: RoutePlanResult? = null
    private var destination: Place? = null
    private var guidance: RouteGuidance? = null
    private var tracker: RouteTracker? = null
    private var recalculating = false
    private var started = false
    /** 안내 중 쓰는 위험 지점. 경로 계산 때 받은 것에 안내 중 새로 받은 ITS 돌발상황을 더한다 */
    private var hazards: List<RiskPoint> = emptyList()
    private var lastTraveled = 0.0
    private var lastRefresh = 0L
    private var refreshing = false

    /** 안내 음성(회전 안내·위험 경고)을 낼지. 화면의 "음성 켜짐" 버튼과 연결한다 */
    var voiceEnabled: Boolean
        get() = voice.enabled
        set(value) { voice.enabled = value }

    /** 지금 위치. 아직 모르면 null */
    val position: LatLng? get() = fix?.let { LatLng(it.lat, it.lng) }

    val guiding: Boolean get() = guidance != null

    private val tick = object : Runnable {
        override fun run() {
            emit()
            refreshHazardsIfDue()
            main.postDelayed(this, 1_000)
        }
    }

    /** 위치·차량 신호 구독을 시작한다. 화면에 들어올 때 부른다 */
    fun start() {
        if (started) return
        started = true
        locations.start(object : LocationListener {
            override fun onLocation(fix: LocationFix) { this@GuidanceSession.fix = fix }
        })
        vehicleReader.start()
        main.post(tick)
    }

    /** 화면을 떠날 때 부른다. 안내 중이면 안내도 끝난다 */
    fun stop() {
        started = false
        main.removeCallbacks(tick)
        locations.release()
        vehicleReader.stop()
        voice.release()
        io.shutdownNow()
    }

    /**
     * 지금 위치에서 [place]까지 후보 경로와 위험도를 계산한다. 결과의 `compare`를 경로 비교 화면에 그린다.
     * 위치를 아직 모르면 실패한다.
     */
    fun plan(place: Place, onResult: (Result<RoutePlanResult>) -> Unit) {
        val from = position ?: return onResult(Result.failure(RoutingException("현재 위치를 아직 알 수 없어요")))
        val heading = fix?.heading?.toDouble()
        val signals = vehicleSignals()
        io.execute {
            val result = runCatching {
                routing.plan(from, LatLng(place.position.lat, place.position.lng), place.name, signals, heading)
            }
            main.post {
                result.onSuccess { lastPlan = it; destination = place }
                onResult(result)
            }
        }
    }

    /** [plan] 결과 중 [routeId] 경로로 안내를 시작한다 */
    fun startGuidance(routeId: String) {
        val plan = lastPlan ?: return
        val route = plan.routes.firstOrNull { it.id == routeId } ?: return
        hazards = plan.hazards
        use(route, plan)
        Log.i(TAG, "안내 시작: ${route.name} ${route.distanceMeters}m")
    }

    /** 안내를 끝낸다 */
    fun endGuidance() {
        guidance = null
        tracker = null
        voice.onState(null)
        onState(null)
    }

    /** 지금 알림의 위험 지점을 피하는 경로로 바꾼다 (알림의 "우회할까요?"에 답했을 때) */
    fun acceptDetour(hazardId: String) {
        val g = guidance ?: return
        val avoid = g.hazardPosition(hazardId) ?: return
        val from = position ?: return
        val to = g.route.line.last()
        val plan = lastPlan ?: return
        val heading = fix?.heading?.toDouble()
        recalculating = true
        io.execute {
            val route = runCatching { routing.detour(from, to, avoid, heading) }.getOrNull()
            main.post {
                recalculating = false
                if (route == null) {
                    voice.announce("피해 갈 길을 찾지 못했습니다. 주의해서 지나가 주세요.")
                    onEvent(Event.DetourFailed)
                } else {
                    voice.announce("위험 구간을 피하는 길로 바꿨습니다.")
                    use(route, plan)
                    onEvent(Event.Detoured)
                }
            }
        }
    }

    // --- 내부 ---

    private fun use(route: RoutePlan, plan: RoutePlanResult) {
        guidance = guidanceFor(route, plan)
        tracker = RouteTracker(route)
        lastTraveled = 0.0
        lastRefresh = SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) saveRouteLine(route)
        emit()
    }

    private fun guidanceFor(route: RoutePlan, plan: RoutePlanResult) =
        RouteGuidance(route, destination?.name ?: plan.compare.destinationName, hazards, plan.weather, plan.plannedAt, vehicleSignals())

    /**
     * 안내 중에도 1분마다 경로 근처의 ITS 돌발상황을 다시 받는다. 출발할 때 없던 통제가 앞에 생기면
     * 알림과 "우회할까요?"가 뜬다. 디버그 빌드의 시험용 통제([TestClosure])는 바로 넣는다.
     */
    private fun refreshHazardsIfDue() {
        val g = guidance ?: return
        val plan = lastPlan ?: return
        val test = TestClosure.aheadMeters
        val now = SystemClock.elapsedRealtime()
        if (refreshing || (test == null && now - lastRefresh < REFRESH_MS)) return
        TestClosure.aheadMeters = null
        refreshing = true
        lastRefresh = now
        val route = g.route
        val testPoint = test?.let {
            val at = RouteGeometry.pointAt(route.line, lastTraveled + it)
            RiskPoint("test-closure", HazardKind.CLOSURE, at, "시험 도로 통제", "디버그용 시험 통제", closedFromSeconds = 0)
        }
        io.execute {
            val live = routing.liveRiskPoints(route)
            main.post {
                refreshing = false
                // 그사이 경로가 바뀌었으면(재탐색·우회) 다음 갱신 때 다시 받는다
                if (guidance?.route !== route) return@post
                val kept = hazards.filterNot { live != null && it.id.startsWith("its-") }
                val next = (kept + live.orEmpty() + listOfNotNull(testPoint)).distinctBy { it.title to it.position }
                if (next == hazards) return@post
                Log.i(TAG, "안내 중 위험 지점 갱신: ${hazards.size} → ${next.size}")
                hazards = next
                guidance = guidanceFor(route, plan)
                emit()
            }
        }
    }

    /** 디버그 빌드에서만: 고른 경로선을 남겨 scripts/replay_route.py가 그 길을 따라 위치를 움직이게 한다 */
    private fun saveRouteLine(route: RoutePlan) = runCatching {
        File(context.filesDir, "route.json").writeText(JSONArray(route.line.map { JSONArray(listOf(it.lat, it.lng)) }).toString())
    }

    private fun emit() {
        val g = guidance ?: return
        val t = tracker ?: return
        val f = fix ?: return
        val here = LatLng(f.lat, f.lng)
        val track = t.update(here, SystemClock.elapsedRealtime() / 1000.0)
        lastTraveled = track.traveled
        if (track.arrived) {
            endGuidance()
            voice.announce("목적지에 도착했습니다. 안내를 마칩니다.")
            onEvent(Event.Arrived)
            return
        }
        if (track.offRoute && !recalculating) reroute(here)
        val state = g.state(track.traveled, here, f.heading?.toDouble(), vehicleSignals(), LocalDateTime.now())
        onState(state)
        voice.onState(state)
    }

    /** 경로를 벗어났을 때 지금 위치에서 같은 목적지로 다시 계산한다 */
    private fun reroute(from: LatLng) {
        val g = guidance ?: return
        val plan = lastPlan ?: return
        recalculating = true
        Log.i(TAG, "경로 이탈, 다시 계산")
        val to = g.route.line.last()
        val signals = vehicleSignals()
        io.execute {
            val result = runCatching {
                routing.plan(from, to, plan.compare.destinationName, signals, fix?.heading?.toDouble())
            }
            main.post {
                recalculating = false
                result.onSuccess { r ->
                    // 다시 계산할 때는 위험도 추천 경로로 바로 바꾼다
                    val pick = r.routes.firstOrNull { it.id == r.compare.recommendedId } ?: r.routes.first()
                    lastPlan = r
                    hazards = r.hazards
                    // 새 경로의 첫 안내보다 먼저 말한다
                    voice.announce("경로를 다시 찾았습니다.")
                    use(pick, r)
                    onEvent(Event.Rerouted)
                }.onFailure { onEvent(Event.Failed(it.message ?: "경로를 다시 찾지 못했어요")) }
            }
        }
    }

    private fun vehicleSignals(): VehicleSignals = vehicle?.toRiskSignals() ?: VehicleSignals()

    private companion object {
        const val TAG = "GuidanceSession"
        const val REFRESH_MS = 60_000L
    }
}
