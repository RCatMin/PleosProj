package com.example.pleosproj.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.routing.PlaceSearchException
import com.example.pleosproj.ui.compare.selectedRoute
import com.example.pleosproj.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class NavigationPage { SEARCH, PLANNING, COMPARE, BRIEFING, GUIDANCE }
internal sealed interface SearchContent {
    data object Recent : SearchContent
    data object Loading : SearchContent
    data class Results(val places: List<Place>) : SearchContent
    data class Failed(val reason: SearchFailure) : SearchContent
}

// 화면 선택과 비동기 요청의 수명만 관리한다. 검색·길찾기·위험 판단·안내는 연결부가 맡는다.
internal class NavigationFlowController(private val source: NavigationSource, private val scope: CoroutineScope) {
    var page by mutableStateOf(NavigationPage.SEARCH)
        private set
    var query by mutableStateOf("")
        private set
    var searchContent by mutableStateOf<SearchContent>(SearchContent.Recent)
        private set
    var recent by mutableStateOf(source.recent())
        private set
    var plan by mutableStateOf<NavigationPlan?>(null)
        private set
    var comparison by mutableStateOf<RouteCompareState?>(null)
        private set
    var briefing by mutableStateOf<BriefingState?>(null)
        private set
    var guidance by mutableStateOf<GuidanceState?>(null)
        private set
    var parked by mutableStateOf<Boolean?>(null)
        private set
    var confirmEnd by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var planFailure by mutableStateOf<String?>(null)
        private set
    var sessionFailure by mutableStateOf<String?>(null)
        private set
    var starting by mutableStateOf(false)
        private set
    var detouring by mutableStateOf(false)
        private set
    var voiceEnabled by mutableStateOf(true)
        private set
    var routeName by mutableStateOf("")
        private set
    var guiding by mutableStateOf(false)
        private set
    private var foreground = false
    private var feed: NavigationFeed? = null
    private var destination: Place? = null
    private var lastDestination = ""
    private var sessionGeneration = 0
    private var searchGeneration = 0
    private var planGeneration = 0
    private var searchJob: Job? = null
    private var fromGuidance = false

    fun updateForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) openFeed() else {
            cancelSearch()
            ++planGeneration
            closeFeed()
            if (page != NavigationPage.SEARCH) {
                clearTrip()
                notice = "안내를 종료했어요. 목적지를 선택해 다시 시작해 주세요."
            }
        }
    }

    private fun openFeed() {
        if (!foreground || feed != null) return
        val generation = ++sessionGeneration
        runCatching {
            source.open(
                { state -> if (generation == sessionGeneration && foreground) receive(state) },
                { event -> if (generation == sessionGeneration && foreground) receive(event) },
            ).also { feed = it; it.voiceEnabled = false; it.start() }
        }.onSuccess { sessionFailure = null }.onFailure {
            closeFeed()
            sessionFailure = "위치와 안내를 연결하지 못했어요. 권한을 확인하고 다시 시도해 주세요."
        }
    }

    private fun closeFeed() {
        ++sessionGeneration
        val previous = feed
        feed = null
        runCatching { previous?.voiceEnabled = false }
        runCatching { previous?.stop() }
    }

    fun retryConnection() { closeFeed(); openFeed() }

    fun permissionsChanged() {
        cancelSearch()
        ++planGeneration
        closeFeed()
        clearTrip()
        openFeed()
    }

    fun updateParked(value: Boolean?) {
        parked = value
        if (value != true) cancelSearch()
    }

    fun editQuery(value: String) {
        if (parked != true) return
        cancelSearch()
        query = value
        searchContent = SearchContent.Recent
    }

    fun search() {
        if (!foreground || parked != true || query.isBlank()) return
        cancelSearch()
        val request = searchGeneration
        val text = query.trim()
        val near = feed?.position
        searchContent = SearchContent.Loading
        searchJob = scope.launch {
            val next = try {
                SearchContent.Results(source.search(text, near))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PlaceSearchException) {
                SearchContent.Failed(failure.reason)
            } catch (_: Exception) {
                SearchContent.Failed(SearchFailure.NETWORK)
            }
            if (request == searchGeneration && page == NavigationPage.SEARCH && parked == true && foreground) searchContent = next
        }
    }

    fun selectPlace(place: Place) {
        if (!foreground || parked != true) return
        cancelSearch()
        destination = place
        lastDestination = place.name
        source.addRecent(place)
        recent = source.recent()
        fromGuidance = false
        requestPlan()
    }

    fun retryPlan() { if (parked == true && page == NavigationPage.PLANNING) requestPlan() }

    private fun requestPlan() {
        val place = destination ?: return
        openFeed()
        val current = feed
        page = NavigationPage.PLANNING
        planFailure = null
        val request = ++planGeneration
        val generation = sessionGeneration
        if (current == null) { planFailure = sessionFailure ?: "안내를 연결하지 못했어요"; return }
        runCatching {
            current.plan(place) { result ->
                if (!foreground || generation != sessionGeneration || request != planGeneration || page != NavigationPage.PLANNING) return@plan
                result.onSuccess { next ->
                    plan = next
                    comparison = next.compare
                    page = NavigationPage.COMPARE
                }.onFailure { planFailure = it.message ?: "경로를 찾지 못했어요. 다시 시도해 주세요." }
            }
        }.onFailure { planFailure = "경로를 찾지 못했어요. 다시 시도해 주세요." }
    }

    fun selectRoute(id: String) {
        if (parked != true) return
        val current = comparison ?: return
        if (current.candidates.any { it.id == id && !it.excluded }) comparison = current.copy(selectedId = id)
    }

    fun showBriefing(id: String) {
        if (parked != true) return
        val current = comparison ?: return
        if (current.candidates.none { it.id == id && !it.excluded }) return
        val next = plan?.briefings?.get(id) ?: return
        comparison = current.copy(selectedId = id)
        briefing = next
        // 경로 변경 중에는 이전 경로의 음성과 브리핑을 동시에 말하지 않는다.
        feed?.voiceEnabled = false
        page = NavigationPage.BRIEFING
    }

    fun startGuidance() { if (parked == true && page == NavigationPage.BRIEFING) beginGuidance() }
    fun drivingStarted() { if (parked == false && page == NavigationPage.BRIEFING) beginGuidance() }
    private fun beginGuidance() {
        val route = comparison?.selectedRoute() ?: return
        val current = feed ?: return
        if (starting) return
        starting = true
        routeName = route.name
        current.voiceEnabled = voiceEnabled
        runCatching { current.startGuidance(route.id) }.onFailure {
            starting = false
            notice = "안내를 시작하지 못했어요. 경로를 다시 확인해 주세요."
        }
    }

    fun showComparison() {
        if (parked != true) { notice = "경로 변경은 정차 후 이용해 주세요"; return }
        if (page == NavigationPage.BRIEFING) { page = NavigationPage.COMPARE; return }
        if (destination == null) return
        fromGuidance = guiding
        requestPlan()
    }

    fun acceptDetour() {
        val alert = guidance?.alert ?: return
        if (!alert.suggestsDetour || detouring || !guiding) return
        detouring = true
        runCatching { feed?.acceptDetour(alert.hazardId) }.onFailure {
            detouring = false
            notice = "우회 경로를 요청하지 못했어요. 다시 시도해 주세요."
        }
    }

    // 확인한 같은 알림은 다음 상태 갱신에서도 다시 띄우지 않는다. 단계·문구가 바뀌면 다시 표시한다.
    private var acknowledged: HazardAlert? = null
    fun acknowledge(id: String) {
        guidance?.let { if (it.alert?.hazardId == id) { acknowledged = it.alert; guidance = it.copy(alert = null) } }
    }

    fun toggleVoice() {
        voiceEnabled = !voiceEnabled
        feed?.voiceEnabled = voiceEnabled && guiding && page != NavigationPage.BRIEFING
    }
    fun requestEnd() { confirmEnd = true }
    fun dismissEnd() { confirmEnd = false }
    fun endGuidance() {
        ++planGeneration
        guiding = false
        starting = false
        feed?.voiceEnabled = false
        runCatching { feed?.endGuidance() }
        clearTrip()
        closeFeed()
        openFeed()
        notice = "안내를 종료했어요"
    }

    private fun receive(state: GuidanceState?) {
        if (!guiding && !starting) return
        if (state == null) {
            clearTrip()
            notice = "안내를 종료했어요"
        } else {
            guidance = state.copy(
                alert = if (state.alert != null && state.alert == acknowledged) null else state.alert,
                weather = if (plan?.weatherMissing == true) emptyList() else state.weather,
            )
            if (starting) page = NavigationPage.GUIDANCE
            starting = false
            guiding = true
        }
    }

    private fun receive(event: NavigationEvent) {
        when (event) {
            NavigationEvent.Arrived -> notice = "${lastDestination}에 도착했어요"
            NavigationEvent.Rerouted -> { routeName = "다시 찾은"; notice = "경로를 다시 찾았어요" }
            NavigationEvent.Detoured -> { detouring = false; routeName = "위험 회피"; notice = "위험 구간을 피하는 경로로 바꿨어요" }
            NavigationEvent.DetourFailed -> { detouring = false; notice = "피해 갈 경로를 찾지 못했어요. 현재 안내를 유지합니다." }
            is NavigationEvent.Failed -> { detouring = false; notice = event.message }
        }
    }

    private fun clearTrip() {
        query = ""
        searchContent = SearchContent.Recent
        plan = null
        comparison = null
        briefing = null
        guidance = null
        destination = null
        acknowledged = null
        guiding = false
        starting = false
        detouring = false
        fromGuidance = false
        confirmEnd = false
        page = NavigationPage.SEARCH
    }

    fun back() {
        when (page) {
            NavigationPage.SEARCH -> { cancelSearch(); query = ""; searchContent = SearchContent.Recent }
            NavigationPage.PLANNING, NavigationPage.COMPARE -> {
                ++planGeneration
                page = if (fromGuidance && guiding) NavigationPage.GUIDANCE else NavigationPage.SEARCH
                if (guiding) feed?.voiceEnabled = voiceEnabled
            }
            NavigationPage.BRIEFING -> page = NavigationPage.COMPARE
            NavigationPage.GUIDANCE -> requestEnd()
        }
    }

    fun consumeNotice() { notice = null }
    fun dispose() { updateForeground(false) }
    private fun cancelSearch() {
        ++searchGeneration
        searchJob?.cancel()
        searchJob = null
        if (searchContent == SearchContent.Loading) searchContent = SearchContent.Recent
    }
}
