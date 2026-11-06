package com.example.pleosproj.ui.navigation

import com.example.pleosproj.ui.state.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NavigationFlowControllerTest {
    private val place = PlaceExample("samsung", "삼성역", "서울 강남구 테헤란로 538", 3360)

    @Test fun selectedRouteIsPreservedAcrossComparisonBriefingAndGuidance() = runBlocking {
        val source = FakeSource()
        val flow = NavigationFlowController(source, this)
        flow.updateParked(true)
        flow.selectPlace(place)
        flow.selectRoute("a")
        assertEquals("b", flow.comparison?.selectedId)
        flow.showBriefing("a")
        assertEquals(NavigationPage.COMPARE, flow.page)
        flow.selectRoute("c")
        flow.showBriefing("c")
        assertEquals(NavigationPage.BRIEFING, flow.page)
        assertEquals("c", source.briefed)
        flow.back()
        assertEquals("c", flow.comparison?.selectedId)
        flow.showBriefing("c")
        flow.startGuidance()
        assertEquals(NavigationPage.GUIDANCE, flow.page)
        assertEquals("c", source.guided)
        assertEquals(PreviewStates.routeCompare.candidates.last().routeLine, flow.guidance?.routeLine)
        flow.showComparison()
        flow.selectRoute("b")
        flow.back()
        assertEquals("c", flow.comparison?.selectedId)
        assertEquals("c", source.guided)
        flow.dispose()
    }

    @Test fun inputIsClosedUnlessParkedAndDrivingLeavesBriefingForGuidance() = runBlocking {
        val flow = NavigationFlowController(FakeSource(), this)
        for (state in listOf(null, false)) {
            flow.updateParked(state)
            flow.editQuery("삼성역")
            flow.selectPlace(place)
            assertEquals("", flow.query)
            assertEquals(NavigationPage.SEARCH, flow.page)
        }
        flow.updateParked(true)
        flow.selectPlace(place)
        flow.showBriefing("b")
        flow.updateParked(false)
        flow.startGuidance()
        assertEquals(NavigationPage.BRIEFING, flow.page)
        flow.drivingStarted()
        assertEquals(NavigationPage.GUIDANCE, flow.page)
        flow.showComparison()
        assertEquals(NavigationPage.GUIDANCE, flow.page)
        flow.dispose()
    }

    @Test fun replacingQueryAndLeavingScreenRejectOldResponsesEvenWhenSourceIgnoresCancellation() = runBlocking {
        val pending = CompletableDeferred<List<PlaceExample>>()
        val source = FakeSource().apply { searchBlock = { withContext(NonCancellable) { pending.await() } } }
        val flow = NavigationFlowController(source, this)
        flow.updateParked(true)
        flow.editQuery("old")
        flow.search()
        yield()
        assertEquals(SearchContent.Loading, flow.searchContent)
        flow.editQuery("new")
        flow.selectPlace(place)
        pending.complete(listOf(place))
        yield()
        assertEquals(NavigationPage.COMPARE, flow.page)
        assertEquals(SearchContent.Recent, flow.searchContent)
        flow.dispose()
    }

    @Test fun searchHandlesLoadingEmptyFailureRetryAndStop() = runBlocking {
        val source = FakeSource()
        val flow = NavigationFlowController(source, this)
        flow.updateParked(true)
        flow.editQuery("삼성역")
        source.searchBlock = { throw SearchExampleFailure(SearchExample.KEY_MISSING) }
        flow.search(); yield()
        assertEquals(SearchContent.Failed(SearchExample.KEY_MISSING), flow.searchContent)
        source.searchBlock = { emptyList() }
        flow.search(); yield()
        assertEquals(SearchContent.Results(emptyList()), flow.searchContent)
        source.searchBlock = { listOf(place) }
        flow.search(); yield()
        assertEquals(SearchContent.Results(listOf(place)), flow.searchContent)
        source.searchBlock = { awaitCancellation() }
        flow.search(); yield()
        flow.updateParked(null)
        assertEquals(SearchContent.Recent, flow.searchContent)
        flow.dispose()
    }

    @Test fun endAndArrivalClearTripAndReturnToRecentDestinations() = runBlocking {
        val flow = NavigationFlowController(FakeSource(), this)
        flow.updateParked(true)
        repeat(2) { index ->
            flow.selectPlace(place)
            flow.showBriefing("b")
            flow.startGuidance()
            flow.requestEnd()
            flow.dismissEnd()
            assertEquals(NavigationPage.GUIDANCE, flow.page)
            flow.endGuidance(arrived = index == 1)
            assertEquals(NavigationPage.SEARCH, flow.page)
            assertNull(flow.guidance)
            assertNull(flow.briefing)
            assertNull(flow.comparison)
            assertFalse(flow.confirmEnd)
            assertEquals(1, flow.recent.count { it.id == place.id })
            assertEquals(if (index == 1) "삼성역에 도착했어요" else "안내를 종료했어요", flow.notice)
        }
        flow.dispose()
    }

    private inner class FakeSource : NavigationExampleSource {
        override val recent = listOf(place)
        var searchBlock: suspend () -> List<PlaceExample> = { listOf(place) }
        var briefed: String? = null
        var guided: String? = null
        override suspend fun search(query: String, example: SearchExample) = searchBlock()
        override fun compare(place: PlaceExample) = PreviewStates.routeCompare
        override fun briefing(routeId: String): BriefingState {
            briefed = routeId
            return PreviewStates.briefing.copy(durationSeconds = compare(place).candidates.first { it.id == routeId }.durationSeconds)
        }
        override fun guidance(routeId: String, advanced: Boolean): GuidanceState {
            guided = routeId
            return PreviewStates.guidanceScenario.copy(routeLine = compare(place).candidates.first { it.id == routeId }.routeLine)
        }
    }
}
