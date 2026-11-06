package com.example.pleosproj.ui.navigation

import com.example.pleosproj.scenario.Scenario
import com.example.pleosproj.scenario.ScenarioEngine
import com.example.pleosproj.ui.state.*
import kotlinx.coroutines.delay

// 공유 상태 계약이 확정되기 전 화면 흐름을 확인하는 전용 예시다. 실제 검색·길찾기를 호출하지 않는다.
internal data class PlaceExample(val id: String, val name: String, val address: String, val distanceMeters: Int)

internal enum class SearchExample(val title: String) {
    RESULTS("검색 성공"), EMPTY("결과 없음"), KEY_MISSING("검색 준비 안 됨"), NETWORK("연결 실패"), LIMIT("요청 한도 초과"),
}

internal class SearchExampleFailure(val reason: SearchExample) : Exception()

internal interface NavigationExampleSource {
    val recent: List<PlaceExample>
    suspend fun search(query: String, example: SearchExample): List<PlaceExample>
    fun compare(place: PlaceExample): RouteCompareState
    fun briefing(routeId: String): BriefingState
    fun guidance(routeId: String, advanced: Boolean): GuidanceState
}

internal class NavigationExamples(private val scenario: Scenario) : NavigationExampleSource {
    private val engine = ScenarioEngine(scenario)
    private val comparison = engine.compare()
    private val destination = PlaceExample("samsung", scenario.destinationName, "서울 강남구 테헤란로 538", 3360)
    override val recent = listOf(destination)

    override suspend fun search(query: String, example: SearchExample): List<PlaceExample> {
        delay(550)
        return when (example) {
            SearchExample.RESULTS -> if (listOf("삼성", "samsung", "테헤란로", "538").any {
                query.contains(it, ignoreCase = true)
            }) listOf(destination) else emptyList()
            SearchExample.EMPTY -> emptyList()
            else -> throw SearchExampleFailure(example)
        }
    }

    override fun compare(place: PlaceExample): RouteCompareState {
        require(place.id == destination.id)
        return comparison
    }

    override fun briefing(routeId: String): BriefingState {
        val chosen = comparison.candidates.first { it.id == routeId && !it.excluded }
        val base = engine.briefing(speaking = false)
        return base.copy(
            durationSeconds = chosen.durationSeconds,
            arrivalTime = scenario.startTime.plusSeconds(chosen.durationSeconds.toLong()),
            overallLevel = chosen.riskLevel,
            riskLines = listOf("${chosen.name} · ${chosen.riskSummary}") +
                base.riskLines.filter { it.contains("비 예보") },
        )
    }

    override fun guidance(routeId: String, advanced: Boolean): GuidanceState {
        val route = scenario.routes.first { it.id == routeId }
        // 선택한 후보로 예시를 만든다. 추천 경로로 되돌리거나 GPS가 연결된 것처럼 움직이지 않는다.
        val selected = ScenarioEngine(scenario.copy(driveRouteId = routeId))
        return selected.guidance(if (advanced) route.durationSeconds * 0.45 else 0.0)
    }
}
