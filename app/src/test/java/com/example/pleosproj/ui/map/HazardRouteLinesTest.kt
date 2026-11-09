package com.example.pleosproj.ui.map

import com.example.pleosproj.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class HazardRouteLinesTest {
    private val start = GeoPoint(37.0, 127.0)
    private val bend = GeoPoint(37.001, 127.0)
    private val end = GeoPoint(37.001, 127.001)
    private val route = listOf(start, bend, end)
    private fun hazard(position: GeoPoint, ratio: Double? = null) = Hazard(
        "h", HazardKind.ICE, position, RiskLevel.HIGH, "결빙", "결빙 구간", ratio,
    )

    @Test
    fun pointEmphasisFollowsRoadVerticesAroundBend() {
        val line = hazardRouteLines(route, listOf(hazard(bend))).single()
        assertEquals(HazardKind.ICE, line.kind)
        assertEquals(3, line.points.size)
        assertEquals(bend, line.points[1])
        assertTrue(line.points.first().lat > start.lat)
        assertTrue(line.points.last().lng < end.lng)
    }

    @Test
    fun endpointEmphasisClampsToRouteAndToleratesDuplicateVertices() {
        val line = hazardRouteLines(listOf(start, start, bend), listOf(hazard(start))).single()
        assertEquals(start, line.points.first())
        assertTrue(line.points.last().lat < bend.lat)
        assertTrue(line.points.all { it.lat.isFinite() && it.lng.isFinite() })
    }

    @Test
    fun projectionUsesEachCandidateGeometryRatherThanAnotherRoutesRatio() {
        val line = hazardRouteLines(route, listOf(hazard(end, ratio = 0.0))).single()
        assertEquals(end, line.points.last())
        assertNotEquals(start, line.points.first())
        val otherRoad = route.map { it.copy(lng = it.lng + .01) }
        assertTrue(hazardRouteLines(otherRoad, listOf(hazard(end))).isEmpty())
    }

    @Test
    fun hazardsAssignedByProviderAreKeptEvenWithWideImpactRadius() {
        val nearby = hazard(GeoPoint(37.005, 127.003))
        val straight = listOf(start, GeoPoint(37.01, 127.0))
        assertTrue(hazardRouteLines(straight, listOf(nearby)).isEmpty())
        assertEquals(1, hazardRouteLines(straight, listOf(nearby), hazardsOnRoute = true).size)
        assertEquals(1, hazardRouteLines(straight, listOf(nearby.copy(ratioOnRoute = .5))).size)
    }

    @Test
    fun suppliedHazardIntervalIsUsedWithoutDuplicatePointEmphasis() {
        val straight = listOf(start, GeoPoint(37.01, 127.0))
        val range = ProgressSegment(SegmentKind.HAZARD, .2, .6, hazardKind = HazardKind.ICE)
        val lines = hazardRouteLines(straight, listOf(hazard(GeoPoint(37.004, 127.0))), listOf(range))
        assertEquals(1, lines.size)
        assertEquals(37.002, lines.single().points.first().lat, .000001)
        assertEquals(37.006, lines.single().points.last().lat, .000001)
    }

    @Test
    fun changingOrClearingRangesRebuildsGeometry() {
        val first = ProgressSegment(SegmentKind.HAZARD, 0.0, .2, hazardKind = HazardKind.ICE)
        val second = first.copy(startRatio = .8, endRatio = 1.0)
        assertNotEquals(hazardRouteLines(route, emptyList(), listOf(first)), hazardRouteLines(route, emptyList(), listOf(second)))
        assertTrue(hazardRouteLines(route, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun invalidOrMissingGeometryCannotCreateFloatingHazardMarks() {
        val hazards = listOf(hazard(bend))
        for (bad in listOf(emptyList(), listOf(start), listOf(start, start), listOf(start, GeoPoint(Double.NaN, 127.0)))) {
            assertTrue(hazardRouteLines(bad, hazards).isEmpty())
        }
        assertTrue(hazardRouteLines(route, listOf(hazard(GeoPoint(Double.NaN, 127.0)))).isEmpty())
        val invalid = ProgressSegment(SegmentKind.HAZARD, Double.NaN, .9, hazardKind = HazardKind.ICE)
        assertTrue(hazardRouteLines(route, emptyList(), listOf(invalid)).isEmpty())
    }
}
