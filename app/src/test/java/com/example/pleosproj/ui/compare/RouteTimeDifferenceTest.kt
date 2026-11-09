package com.example.pleosproj.ui.compare

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTimeDifferenceTest {
    @Test fun comparesAlternativesWithTheCurrentRemainingTimeInsteadOfTheFastestCandidate() {
        assertEquals("+5분 · 지금 길 대비", routeTimeDifference(1200, 900, true))
        assertEquals("−5분 · 지금 길 대비", routeTimeDifference(600, 900, true))
        assertEquals("지금 길과 비슷", routeTimeDifference(910, 900, true))
        assertEquals("+5분", routeTimeDifference(1200, 900, false))
        assertEquals("", routeTimeDifference(900, 900, false))
    }
}
