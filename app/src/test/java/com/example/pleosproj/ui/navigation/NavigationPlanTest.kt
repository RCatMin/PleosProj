package com.example.pleosproj.ui.navigation

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.routing.*
import com.example.pleosproj.ui.state.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NavigationPlanTest {
    @Test fun briefingUsesSelectedCandidateRiskAndTimeAndDoesNotOfferExcludedRoutes() {
        val result = result(weatherMissing = false).toScreenPlan()
        assertFalse(result.briefings.containsKey("a"))
        val chosen = result.briefings.getValue("c")
        assertEquals(44 * 60, chosen.durationSeconds)
        assertEquals(RiskLevel.MEDIUM, chosen.overallLevel)
        assertEquals(listOf(PreviewStates.routeCompare.candidates.last().riskSummary), chosen.riskLines)
        assertEquals(18, chosen.arrivalTime.hour)
        assertEquals(54, chosen.arrivalTime.minute)
        assertTrue(chosen.weather.all { it.rainMmPerHour == 12.0 })
    }

    @Test fun missingWeatherIsVisibleAndFallbackWeatherIsNotPresentedAsAnObservation() {
        val result = result(weatherMissing = true).toScreenPlan()
        assertTrue(result.weatherMissing)
        result.briefings.values.forEach {
            assertTrue(it.weather.isEmpty())
            assertTrue(it.riskLines.any { line -> line.contains("날씨 정보를 받지 못해") })
        }
    }

    private fun result(weatherMissing: Boolean) = RoutePlanResult(
        compare = PreviewStates.routeCompare,
        routes = PreviewStates.routeCompare.candidates.map { candidate ->
            RoutePlan(candidate.id, candidate.name, candidate.distanceMeters, candidate.durationSeconds,
                candidate.routeLine.map { LatLng(it.lat, it.lng) }, emptyList())
        }, weatherMissing = weatherMissing,
        weather = WeatherAlong { _, _ -> WeatherSample(12.0, false, 9.0, WarningLevel.NONE) },
        plannedAt = LocalDateTime.of(2026, 11, 6, 18, 10),
    )
}
