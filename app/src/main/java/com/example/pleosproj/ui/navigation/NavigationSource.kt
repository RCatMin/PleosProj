package com.example.pleosproj.ui.navigation

import android.content.Context
import com.example.pleosproj.BuildConfig
import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.VehicleSignals
import com.example.pleosproj.routing.*
import com.example.pleosproj.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

// 화면 수명과 비동기 요청을 테스트하기 위한 연결부다. 제품 데이터는 개발 영역의 공개 API에서만 받는다.
internal data class NavigationPlan(val compare: RouteCompareState, val briefings: Map<String, BriefingState>, val weatherMissing: Boolean)
internal sealed interface NavigationEvent {
    data object Rerouted : NavigationEvent
    data object Detoured : NavigationEvent
    data object DetourFailed : NavigationEvent
    data object Arrived : NavigationEvent
    data class Failed(val message: String) : NavigationEvent
}
internal interface NavigationFeed {
    val position: GeoPoint?
    var voiceEnabled: Boolean
    fun start()
    fun stop()
    fun plan(place: Place, result: (Result<NavigationPlan>) -> Unit)
    fun startGuidance(routeId: String)
    fun acceptDetour(hazardId: String)
    fun endGuidance()
}
internal interface NavigationSource {
    fun recent(): List<Place>
    fun addRecent(place: Place)
    suspend fun search(query: String, near: GeoPoint?): List<Place>
    fun open(onState: (GuidanceState?) -> Unit, onEvent: (NavigationEvent) -> Unit): NavigationFeed
}

internal class RealNavigationSource(private val context: Context) : NavigationSource {
    private val places = PlaceSearch.create()
    private val history = RecentPlaces(context)
    override fun recent() = history.list()
    override fun addRecent(place: Place) = history.add(place)
    override suspend fun search(query: String, near: GeoPoint?) = withContext(Dispatchers.IO) {
        places.search(query, near?.let { LatLng(it.lat, it.lng) })
    }

    override fun open(onState: (GuidanceState?) -> Unit, onEvent: (NavigationEvent) -> Unit): NavigationFeed {
        var lastPlan: RoutePlanResult? = null
        val session = GuidanceSession(context, onState, { event ->
            onEvent(when (event) {
                GuidanceSession.Event.Rerouted -> NavigationEvent.Rerouted
                GuidanceSession.Event.Detoured -> NavigationEvent.Detoured
                GuidanceSession.Event.DetourFailed -> NavigationEvent.DetourFailed
                GuidanceSession.Event.Arrived -> NavigationEvent.Arrived
                is GuidanceSession.Event.Failed -> NavigationEvent.Failed(event.message)
            })
        })
        return object : NavigationFeed {
            override val position get() = session.position?.let { GeoPoint(it.lat, it.lng) }
            override var voiceEnabled: Boolean
                get() = session.voiceEnabled
                set(value) { session.voiceEnabled = value }
            override fun start() = session.start()
            override fun stop() = session.stop()
            override fun plan(place: Place, result: (Result<NavigationPlan>) -> Unit) {
                session.plan(place) { planned ->
                    planned.onSuccess { lastPlan = it }
                    result(planned.mapCatching { it.toScreenPlan() })
                }
            }
            override fun startGuidance(routeId: String) {
                // 디버그 재생 도구가 화면에서 고른 경로와 같은 길을 따라가게 한다. 비밀값은 담지 않는다.
                if (BuildConfig.DEBUG) lastPlan?.routes?.firstOrNull { it.id == routeId }?.let { route ->
                    runCatching {
                        File(context.filesDir, "route.json").writeText(
                            JSONArray(route.line.map { JSONArray(listOf(it.lat, it.lng)) }).toString())
                    }
                }
                session.startGuidance(routeId)
            }
            override fun acceptDetour(hazardId: String) = session.acceptDetour(hazardId)
            override fun endGuidance() = session.endGuidance()
        }
    }
}

// 위험 단계·요약은 비교 결과를 쓴다. 날씨 카드도 안내 엔진의 표현을 재사용해 브리핑과 안내를 맞춘다.
internal fun RoutePlanResult.toScreenPlan(): NavigationPlan = NavigationPlan(compare,
    compare.candidates.filterNot { it.excluded }.associate { chosen ->
        val route = routes.first { it.id == chosen.id }
        val initial = RouteGuidance(route, compare.destinationName, hazards, weather, plannedAt, VehicleSignals())
            .state(0.0, route.line.first(), null, VehicleSignals(), plannedAt)
        chosen.id to BriefingState(
            compare.destinationName, plannedAt.plusSeconds(chosen.durationSeconds.toLong()).toLocalTime(),
            chosen.durationSeconds,
            buildList {
                if (chosen.riskLevel != RiskLevel.NONE) add(chosen.riskSummary)
                if (weatherMissing) add("날씨 정보를 받지 못해 위험도에 날씨가 반영되지 않았습니다")
            }, chosen.riskLevel, if (weatherMissing) emptyList() else initial.weather, speaking = false,
        )
    }, weatherMissing)
