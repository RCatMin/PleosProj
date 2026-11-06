package com.example.pleosproj.ui.navigation

import com.example.pleosproj.scenario.ScenarioParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NavigationExamplesTest {
    @Test fun everySelectableRouteHasItsOwnBriefingAndGuidanceGeometry() {
        val scenario = ScenarioParser.parse(File("src/main/assets/scenarios/rain-commute.json").readText())
        val examples = NavigationExamples(scenario)
        val comparison = examples.compare(examples.recent.single())
        assertTrue(comparison.candidates.any { it.excluded })
        comparison.candidates.filterNot { it.excluded }.forEach { route ->
            val briefing = examples.briefing(route.id)
            val guidance = examples.guidance(route.id, advanced = false)
            val advanced = examples.guidance(route.id, advanced = true)
            assertEquals(route.durationSeconds, briefing.durationSeconds)
            assertEquals(route.durationSeconds, guidance.remainingSeconds)
            assertEquals(route.riskLevel, briefing.overallLevel)
            assertEquals(route.routeLine, guidance.routeLine)
            assertEquals(route.routeLine.first(), guidance.myPosition)
            assertEquals(route.routeLine, advanced.routeLine)
            assertTrue(advanced.remainingMeters < guidance.remainingMeters)
            assertTrue(advanced.remainingSeconds < guidance.remainingSeconds)
            assertNotEquals(guidance.myPosition, advanced.myPosition)
        }
    }
}
