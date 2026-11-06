package com.example.pleosproj.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.ui.compare.selectedRoute
import com.example.pleosproj.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class NavigationPage { SEARCH, COMPARE, BRIEFING, GUIDANCE }
internal sealed interface SearchContent {
    data object Recent : SearchContent
    data object Loading : SearchContent
    data class Results(val places: List<PlaceExample>) : SearchContent
    data class Failed(val reason: SearchExample) : SearchContent
}

// 검색 요청의 수명과 화면 선택만 관리한다. 검색·위험도·안내 엔진의 제품 계약은 PLE-34·36·37에서 받는다.
internal class NavigationFlowController(private val source: NavigationExampleSource, private val scope: CoroutineScope) {
    var page by mutableStateOf(NavigationPage.SEARCH)
        private set
    var query by mutableStateOf("")
        private set
    var searchContent by mutableStateOf<SearchContent>(SearchContent.Recent)
        private set
    var recent by mutableStateOf(source.recent)
        private set
    var comparison by mutableStateOf<RouteCompareState?>(null)
        private set
    var briefing by mutableStateOf<BriefingState?>(null)
        private set
    var guidance by mutableStateOf<GuidanceState?>(null)
        private set
    var parked by mutableStateOf<Boolean?>(null)
        private set
    var example by mutableStateOf(SearchExample.RESULTS)
        private set
    var confirmEnd by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var advanced by mutableStateOf(false)
        private set
    private var fromGuidance = false
    private var activeRouteId: String? = null
    private var searchJob: Job? = null
    private var generation = 0

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

    fun chooseExample(value: SearchExample) {
        if (parked != true) return
        cancelSearch()
        example = value
        searchContent = SearchContent.Recent
    }

    fun search() {
        if (parked != true || query.isBlank()) return
        cancelSearch()
        val request = generation
        val text = query.trim()
        val condition = example
        searchContent = SearchContent.Loading
        searchJob = scope.launch {
            val next = try {
                SearchContent.Results(source.search(text, condition))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SearchExampleFailure) {
                SearchContent.Failed(failure.reason)
            } catch (_: Exception) {
                SearchContent.Failed(SearchExample.NETWORK)
            }
            if (request == generation && page == NavigationPage.SEARCH && parked == true) searchContent = next
        }
    }

    fun selectPlace(place: PlaceExample) {
        if (parked != true) return
        cancelSearch()
        comparison = source.compare(place)
        recent = (listOf(place) + recent.filterNot { it.id == place.id }).take(5)
        fromGuidance = false
        page = NavigationPage.COMPARE
        notice = null
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
        comparison = current.copy(selectedId = id)
        briefing = source.briefing(id)
        page = NavigationPage.BRIEFING
    }

    fun startGuidance() {
        if (parked != true || page != NavigationPage.BRIEFING) return
        beginGuidance()
    }

    fun drivingStarted() {
        if (parked == false && page == NavigationPage.BRIEFING) beginGuidance()
    }

    private fun beginGuidance() {
        val id = comparison?.selectedRoute()?.id ?: return
        activeRouteId = id
        guidance = source.guidance(id, advanced = false)
        advanced = false
        page = NavigationPage.GUIDANCE
        confirmEnd = false
    }

    fun showComparison() {
        if (parked != true) { notice = "경로 변경은 정차 후 이용해 주세요"; return }
        fromGuidance = page == NavigationPage.GUIDANCE || fromGuidance
        page = NavigationPage.COMPARE
    }

    fun advanceExample() {
        if (page != NavigationPage.GUIDANCE) return
        val id = comparison?.selectedRoute()?.id ?: return
        advanced = !advanced
        guidance = source.guidance(id, advanced)
    }

    fun acknowledge(id: String) {
        guidance?.let { if (it.alert?.hazardId == id) guidance = it.copy(alert = null) }
    }

    fun requestEnd() { confirmEnd = true }
    fun dismissEnd() { confirmEnd = false }
    fun endGuidance(arrived: Boolean = false) {
        val destination = comparison?.destinationName.orEmpty()
        cancelSearch()
        query = ""
        searchContent = SearchContent.Recent
        comparison = null
        briefing = null
        guidance = null
        activeRouteId = null
        fromGuidance = false
        confirmEnd = false
        page = NavigationPage.SEARCH
        notice = if (arrived) "${destination}에 도착했어요" else "안내를 종료했어요"
    }

    fun back() {
        when (page) {
            NavigationPage.SEARCH -> { cancelSearch(); query = ""; searchContent = SearchContent.Recent }
            NavigationPage.COMPARE -> {
                page = if (fromGuidance) NavigationPage.GUIDANCE else NavigationPage.SEARCH
                // 경로를 바꾸기 전의 안내 화면으로 돌아가면 그때 사용하던 선택도 복원한다.
                if (fromGuidance) comparison = comparison?.let { it.copy(selectedId = activeRouteId ?: it.selectedId) }
            }
            NavigationPage.BRIEFING -> page = NavigationPage.COMPARE
            NavigationPage.GUIDANCE -> requestEnd()
        }
    }

    fun consumeNotice() { notice = null }
    fun dispose() { cancelSearch() }
    private fun cancelSearch() {
        ++generation
        searchJob?.cancel()
        searchJob = null
        if (searchContent == SearchContent.Loading) searchContent = SearchContent.Recent
    }
}
