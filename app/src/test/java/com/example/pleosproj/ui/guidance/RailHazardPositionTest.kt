package com.example.pleosproj.ui.guidance

import com.example.pleosproj.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class RailHazardPositionTest {
    @Test fun hazardDistanceUsesThePointInsteadOfTheStartOfItsColoredRiskZone() {
        val zone = ProgressSegment(SegmentKind.HAZARD, .1, .8, "물 고임", HazardKind.FLOOD)
        val traffic = ProgressSegment(SegmentKind.CONGESTION_HEAVY, .2, .7, "정체")
        val progress = RouteProgress(.3, 10000, listOf(zone, traffic))
        val hazard = Hazard("flood", HazardKind.FLOOD, GeoPoint(37.0, 127.0), RiskLevel.MEDIUM, "물 고임", "", .6)
        val result = progressForLabels(progress, listOf(hazard))
        val label = railLabels(result, 1000f).first { it.segment.kind == SegmentKind.HAZARD }
        assertEquals(3000, ((label.segment.startRatio - result.currentRatio) * result.totalMeters).toInt())
        assertTrue(result.segments.contains(traffic))
        assertEquals(listOf(zone, traffic), progress.segments)
    }
}
