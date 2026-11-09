package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.PreviewStates
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.Point

class GuidanceMapDataTest {
    @Test
    fun switchingToLiveClearsRouteWithoutInventingDestinationCoordinates() {
        val scenario = GuidanceMapData(PreviewStates.guidanceScenario)
        assertEquals(1, scenario.routeFeatures.features()!!.size)
        assertEquals(2, scenario.locationFeatures.features()!!.size)

        val live = GuidanceMapData(PreviewStates.guidanceLive)
        assertTrue(live.routeFeatures.features()!!.isEmpty())
        assertTrue(live.routes.isEmpty())
        assertEquals(listOf("내 위치"), live.locationFeatures.features()!!.map { it.getStringProperty("label") })
    }

    @Test
    fun currentPositionFollowsStateInsteadOfRouteStart() {
        val moved = PreviewStates.guidanceScenario.copy(myPosition = GeoPoint(37.51, 127.05))
        val data = GuidanceMapData(moved)
        val current = data.locationFeatures.features()!!.first().geometry() as Point
        assertEquals(37.51, current.latitude(), 0.000001)
        assertEquals(127.05, current.longitude(), 0.000001)
        assertTrue(data.protectedPoints.contains(current))
        assertNotEquals(moved.routeLine!!.first(), moved.myPosition)
    }

    @Test
    fun clearingHazardsAndRouteLeavesOnlyCurrentLocationProtected() {
        val state = PreviewStates.guidanceLive.copy(hazards = emptyList(), alert = null)
        val data = GuidanceMapData(state)
        assertTrue(data.hazardFeatures.features()!!.isEmpty())
        assertTrue(data.routeFeatures.features()!!.isEmpty())
        assertEquals(1, data.protectedPoints.size)
        assertEquals(state.myPosition.lng, data.protectedPoints.single().longitude(), 0.000001)
    }
    @Test
    fun hazardSourceContainsRouteLinesInsteadOfLabeledPointsAndClearsWithRoute() {
        val original = PreviewStates.guidanceScenario
        val state = original.copy(
            routeLine = listOf(GeoPoint(37.0, 127.0), GeoPoint(37.01, 127.0)),
            hazards = listOf(original.hazards.first().copy(position = GeoPoint(37.005, 127.0))),
            progress = original.progress.copy(segments = emptyList()),
        )
        val data = GuidanceMapData(state)
        val feature = data.hazardFeatures.features()!!.single()
        assertTrue(feature.geometry() is org.maplibre.geojson.LineString)
        assertFalse(feature.hasProperty("label"))
        assertEquals(listOf(state.hazards.single().kind), data.hazardKinds)
        val cleared = GuidanceMapData(state.copy(routeLine = null))
        assertTrue(cleared.hazardFeatures.features()!!.isEmpty())
        assertTrue(cleared.hazardKinds.isEmpty())
    }

}
