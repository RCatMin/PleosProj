package com.example.pleosproj.ui.navigation

import com.example.pleosproj.routing.PlaceSearchException
import com.example.pleosproj.ui.state.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NavigationFlowControllerTest {
    private val place = Place("samsung", "삼성역", "테헤란로 538", GeoPoint(37.5088, 127.0631), 3000)
    private val plan = NavigationPlan(PreviewStates.routeCompare,
        PreviewStates.routeCompare.candidates.filterNot { it.excluded }.associate {
            it.id to PreviewStates.briefing.copy(durationSeconds = it.durationSeconds)
        }, weatherMissing = false)

    @Test fun selectedRealPlacePlansThenSelectedRouteStartsWithSessionState() = runBlocking {
        val source = FakeSource()
        val flow = ready(source, this)
        flow.selectPlace(place)
        assertEquals(NavigationPage.PLANNING, flow.page)
        assertEquals(place, source.feeds.single().places.single())
        source.feeds.single().planned.single()(Result.success(plan))
        flow.showBriefing("a")
        assertEquals(NavigationPage.COMPARE, flow.page)
        flow.selectRoute("c")
        flow.showBriefing("c")
        assertEquals(44 * 60, flow.briefing?.durationSeconds)
        flow.back()
        assertEquals("c", flow.comparison?.selectedId)
        flow.showBriefing("c")
        flow.startGuidance()
        assertEquals(listOf("c"), source.feeds.single().started)
        assertTrue(flow.starting)
        val state = PreviewStates.guidanceScenario.copy(mode = DriveMode.LIVE,
            routeLine = plan.compare.candidates.last().routeLine)
        source.feeds.single().onState(state)
        assertEquals(NavigationPage.GUIDANCE, flow.page)
        assertEquals(state, flow.guidance)
        assertFalse(flow.starting)
        flow.dispose()
    }

    @Test fun unknownAndDrivingStatesCloseInputButDrivingOutOfBriefingStartsGuidance() = runBlocking {
        val source = FakeSource()
        val flow = NavigationFlowController(source, this)
        flow.updateForeground(true)
        for (value in listOf(null, false)) {
            flow.updateParked(value)
            flow.editQuery("삼성역")
            flow.selectPlace(place)
            assertEquals("", flow.query)
            assertEquals(NavigationPage.SEARCH, flow.page)
        }
        flow.updateParked(true)
        flow.selectPlace(place)
        source.feeds.single().planned.single()(Result.success(plan))
        flow.showBriefing("b")
        flow.updateParked(false)
        flow.startGuidance()
        assertFalse(flow.starting)
        flow.drivingStarted()
        assertTrue(flow.starting)
        flow.dispose()
    }

    @Test fun searchUsesCurrentPositionRejectsStaleResultsAndPreservesFailureReason() = runBlocking {
        val source = FakeSource()
        val pending = CompletableDeferred<List<Place>>()
        source.searchBlock = { withContext(NonCancellable) { pending.await() } }
        val flow = ready(source, this)
        flow.editQuery(" 삼성역 ")
        flow.search(); yield()
        assertEquals("삼성역", source.lastQuery)
        assertEquals(GeoPoint(37.5, 127.0), source.lastNear)
        flow.editQuery("new")
        pending.complete(listOf(place)); yield()
        assertEquals(SearchContent.Recent, flow.searchContent)
        source.searchBlock = { throw PlaceSearchException(SearchFailure.NOT_ALLOWED, "not allowed") }
        flow.search(); yield()
        assertEquals(SearchContent.Failed(SearchFailure.NOT_ALLOWED), flow.searchContent)
        source.searchBlock = { emptyList() }
        flow.search(); yield()
        assertEquals(SearchContent.Results(emptyList()), flow.searchContent)
        source.searchBlock = { listOf(place) }
        flow.search(); yield()
        assertEquals(SearchContent.Results(listOf(place)), flow.searchContent)
        flow.dispose()
    }

    @Test fun planRetryAndBackIgnoreOldResponsesAndReleaseStopsOldSessionCallbacks() = runBlocking {
        val source = FakeSource()
        val flow = ready(source, this)
        flow.selectPlace(place)
        val old = source.feeds.single()
        old.planned[0](Result.failure(Exception("위치 없음")))
        assertEquals("위치 없음", flow.planFailure)
        flow.retryPlan()
        flow.back()
        old.planned[1](Result.success(plan))
        assertEquals(NavigationPage.SEARCH, flow.page)
        flow.updateForeground(false)
        assertTrue(old.stopped)
        assertFalse(old.voiceEnabled)
        flow.updateForeground(true)
        assertEquals(2, source.feeds.size)
        old.onState(PreviewStates.guidanceScenario)
        old.onEvent(NavigationEvent.Arrived)
        assertNull(flow.guidance)
        assertNull(flow.notice)
        flow.dispose()
    }

    @Test fun detourUsesHazardIdAndEventsUpdateTheCurrentGuidance() = runBlocking {
        val source = FakeSource()
        val flow = ready(source, this)
        val feed = source.feeds.single()
        guide(flow, feed)
        feed.onState(PreviewStates.guidanceLive)
        flow.acceptDetour()
        assertEquals(listOf("closure-1"), feed.detours)
        assertTrue(flow.detouring)
        feed.onEvent(NavigationEvent.DetourFailed)
        assertFalse(flow.detouring)
        assertTrue(flow.notice!!.contains("찾지 못했어요"))
        val changed = PreviewStates.guidanceLive.copy(routeLine = PreviewStates.routeCompare.candidates.last().routeLine)
        feed.onState(changed)
        feed.onEvent(NavigationEvent.Detoured)
        assertEquals(changed, flow.guidance)
        assertEquals("위험 회피", flow.routeName)
        feed.onEvent(NavigationEvent.Rerouted)
        assertEquals("다시 찾은", flow.routeName)
        flow.dispose()
    }

    @Test fun changingPermissionsRecreatesTheSessionAndRejectsTheOldPlan() = runBlocking {
        val source = FakeSource()
        val flow = ready(source, this)
        flow.selectPlace(place)
        val old = source.feeds.single()
        flow.permissionsChanged()
        assertTrue(old.stopped)
        assertEquals(2, source.feeds.size)
        old.planned.single()(Result.success(plan))
        assertEquals(NavigationPage.SEARCH, flow.page)
        assertNull(flow.comparison)
        flow.dispose()
    }

    @Test fun muteEndAndArrivalAreConnectedToSessionAndDoNotLeaveOldTripState() = runBlocking {
        val source = FakeSource()
        val flow = ready(source, this)
        val feed = source.feeds.single()
        guide(flow, feed)
        flow.toggleVoice()
        assertFalse(feed.voiceEnabled)
        flow.requestEnd(); flow.dismissEnd()
        assertTrue(flow.guiding)
        flow.endGuidance()
        assertEquals(1, feed.ended)
        assertTrue(feed.stopped)
        assertNull(flow.guidance)
        assertEquals(NavigationPage.SEARCH, flow.page)
        assertFalse(flow.voiceEnabled)
        val next = source.feeds.last()
        guide(flow, next)
        assertFalse(next.voiceEnabled)
        next.onState(null)
        next.onEvent(NavigationEvent.Arrived)
        assertEquals(NavigationPage.SEARCH, flow.page)
        assertNull(flow.comparison)
        assertEquals("삼성역에 도착했어요", flow.notice)
        flow.dispose()
    }

    private fun ready(source: FakeSource, scope: CoroutineScope) = NavigationFlowController(source, scope).apply {
        updateForeground(true); updateParked(true)
    }
    private fun guide(flow: NavigationFlowController, feed: FakeFeed) {
        flow.selectPlace(place)
        feed.planned.last()(Result.success(plan))
        flow.showBriefing("b"); flow.startGuidance()
        feed.onState(PreviewStates.guidanceScenario.copy(mode = DriveMode.LIVE))
    }
    private inner class FakeSource : NavigationSource {
        val feeds = mutableListOf<FakeFeed>()
        val history = mutableListOf<Place>()
        var searchBlock: suspend () -> List<Place> = { listOf(place) }
        var lastNear: GeoPoint? = null
        var lastQuery: String? = null
        override fun recent() = history.toList()
        override fun addRecent(place: Place) { history.removeAll { it.id == place.id }; history.add(0, place) }
        override suspend fun search(query: String, near: GeoPoint?): List<Place> {
            lastQuery = query; lastNear = near; return searchBlock()
        }
        override fun open(onState: (GuidanceState?) -> Unit, onEvent: (NavigationEvent) -> Unit) =
            FakeFeed(onState, onEvent).also(feeds::add)
    }
    private class FakeFeed(val onState: (GuidanceState?) -> Unit, val onEvent: (NavigationEvent) -> Unit) : NavigationFeed {
        override val position = GeoPoint(37.5, 127.0)
        override var voiceEnabled = true
        val places = mutableListOf<Place>()
        val planned = mutableListOf<(Result<NavigationPlan>) -> Unit>()
        val started = mutableListOf<String>()
        val detours = mutableListOf<String>()
        var stopped = false
        var ended = 0
        override fun start() {}
        override fun stop() { stopped = true }
        override fun plan(place: Place, result: (Result<NavigationPlan>) -> Unit) { places += place; planned += result }
        override fun startGuidance(routeId: String) { started += routeId }
        override fun acceptDetour(hazardId: String) { detours += hazardId }
        override fun endGuidance() { ended++; onState(null) }
    }
}
