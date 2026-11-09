package com.example.pleosproj.ui.guidance

import com.example.pleosproj.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class RouteProgressRailTest {
    @Test fun denseLabelsRemainWithinBoundsAndDoNotOverlap() {
        val segments = (1..9).map { ProgressSegment(SegmentKind.TURN, 0.9 + it * 0.001, 0.9 + it * 0.001, "회전 $it") }
        val labels = railLabels(RouteProgress(0.2, 5000, segments), 600f)
        assertEquals(4, labels.size)
        assertTrue(labels.first().top >= 0)
        assertTrue(labels.last().top + 58 <= 600 * 0.8 - 36)
        labels.zipWithNext().forEach { (a, b) -> assertTrue(b.top - a.top >= 76) }
        assertEquals(segments.take(4).toSet(), labels.map { it.segment }.toSet())
    }

    @Test fun passedAndRepeatedLabelsAreOmitted() {
        val passed = ProgressSegment(SegmentKind.TURN, 0.1, 0.1, "지난 회전")
        val hazard = ProgressSegment(SegmentKind.HAZARD, 0.5, 0.6, "물 고임")
        val labels = railLabels(RouteProgress(0.4, 5000, listOf(passed, hazard, hazard.copy(startRatio = 0.6, endRatio = 0.7))), 600f)
        assertEquals(listOf(hazard), labels.map { it.segment })
    }

    @Test fun arrivalLeavesSpaceForCurrentPosition() {
        assertTrue(railLabels(RouteProgress(0.98, 5000, listOf(ProgressSegment(SegmentKind.TURN, 1.0, 1.0))), 600f).isEmpty())
    }
    @Test fun trackTapMapsDestinationTopAndDepartureBottomInBothDirections() {
        assertEquals(1.0, railRatioAt(0f, 1000f)!!, .000001)
        assertEquals(.75, railRatioAt(250f, 1000f)!!, .000001)
        assertEquals(.25, railRatioAt(750f, 1000f)!!, .000001)
        assertEquals(0.0, railRatioAt(1000f, 1000f)!!, .000001)
        assertEquals(0.0, railRatioAt(1200f, 1000f)!!, .000001)
        assertNull(railRatioAt(0f, 0f))
        assertNull(railRatioAt(Float.NaN, 1000f))
    }

    @Test fun eventJumpLeavesTwoHundredMetersForRecordingAndClampsNearStart() {
        assertEquals(.46, railEventRatio(.5, 5000), .000001)
        assertEquals(0.0, railEventRatio(.01, 5000), .000001)
        assertEquals(.5, railEventRatio(.5, 0), .000001)
    }

}
