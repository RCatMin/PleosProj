package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.compare.selectedRoute
import com.example.pleosproj.ui.state.PreviewStates
import org.junit.Assert.*
import org.junit.Test

class RouteCompareMapDataTest {
    private val state = PreviewStates.routeCompare

    @Test
    fun defaultRecommendationIsTheOnlySelectedMapRoute() {
        assertEquals(state.recommendedId, state.selectedRoute()?.id)
        val features = RouteCompareMapData(state).routeFeatures.features()!!
        assertEquals(3, features.size)
        assertEquals(listOf(state.recommendedId), features.filter { it.getBooleanProperty("selected") }.map { it.getStringProperty("id") })
        assertEquals(listOf("a"), features.filter { it.getBooleanProperty("excluded") }.map { it.getStringProperty("id") })
    }

    @Test
    fun selectingAnotherCandidateMovesMapHighlightWithoutLosingAlternatives() {
        val changed = state.copy(selectedId = "c")
        assertEquals("c", changed.selectedRoute()?.id)
        val features = RouteCompareMapData(changed).routeFeatures.features()!!
        assertEquals(3, features.size)
        assertEquals(listOf("c"), features.filter { it.getBooleanProperty("selected") }.map { it.getStringProperty("id") })
        assertFalse(features.first { it.getStringProperty("id") == "b" }.getBooleanProperty("selected"))
    }

    @Test
    fun excludedOrMissingSelectionFallsBackToUsableRecommendation() {
        for (id in listOf("a", "missing")) {
            assertEquals("b", state.copy(selectedId = id).selectedRoute()?.id)
            val features = RouteCompareMapData(state.copy(selectedId = id)).routeFeatures.features()!!
            assertFalse(features.first { it.getBooleanProperty("excluded") }.getBooleanProperty("selected"))
        }
    }

    @Test
    fun allExcludedHasNoStartableOrHighlightedRoute() {
        val excluded = state.copy(candidates = state.candidates.map { it.copy(excluded = true) })
        assertNull(excluded.selectedRoute())
        assertTrue(RouteCompareMapData(excluded).routeFeatures.features()!!.none { it.getBooleanProperty("selected") })
    }

    @Test
    fun clearedCandidatesRemoveRoutesHazardsAndEndpoints() {
        val data = RouteCompareMapData(state.copy(candidates = emptyList()))
        assertTrue(data.routes.isEmpty())
        assertTrue(data.routeFeatures.features()!!.isEmpty())
        assertTrue(data.hazardFeatures.features()!!.isEmpty())
        assertTrue(data.locationFeatures.features()!!.isEmpty())
    }
}
