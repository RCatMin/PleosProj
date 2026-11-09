package com.example.pleosproj.ui.guidance

import com.example.pleosproj.ui.state.WeatherCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidanceWeatherSelectionTest {
    private fun weather(label: String, temperature: Int = 5) = WeatherCard(label, temperature, 70, 2.0, false)

    @Test fun destinationAndTimeForecastsNeverFillMissingDistanceSlots() {
        val now = weather("지금")
        val slots = guidanceWeatherSlots(listOf(weather("목적지", -1), weather("10분 뒤", -3), now), 30_000)
        assertEquals(now, slots[0])
        assertNull(slots[1])
        assertNull(slots[2])
    }

    @Test fun distanceForecastsMatchLabelsRatherThanInputOrder() {
        val now = weather("지금", 5)
        val ten = weather("10km 앞", 3)
        val twenty = weather("20km 앞", -1)
        assertEquals(listOf(now, ten, twenty), guidanceWeatherSlots(listOf(twenty, weather("목적지"), ten, now), 30_000))
    }

    @Test fun forecastsAtOrBeyondDestinationAreHiddenEvenIfPresent() {
        val now = weather("지금")
        val ten = weather("10km 앞")
        val twenty = weather("20km 앞")
        val all = listOf(now, ten, twenty)
        assertEquals(listOf(now, null, null), guidanceWeatherSlots(all, 9_999))
        assertEquals(listOf(now, null, null), guidanceWeatherSlots(all, 10_000))
        assertEquals(listOf(now, ten, null), guidanceWeatherSlots(all, 10_001))
        assertEquals(listOf(now, ten, null), guidanceWeatherSlots(all, 20_000))
        assertEquals(listOf(now, ten, twenty), guidanceWeatherSlots(all, 20_001))
    }
}
