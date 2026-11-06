package com.example.pleosproj.routing

import com.example.pleosproj.risk.WarningLevel
import com.example.pleosproj.weather.WeatherWarning
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WarningsTest {
    private val now = LocalDateTime.of(2026, 11, 6, 18, 0)

    @Test
    fun `특보 단계는 경보를 우선하고 도로와 관계없는 특보는 뺀다`() {
        val w = listOf(
            WeatherWarning(now, "호우주의보", "호우", "주의보", true),
            WeatherWarning(now, "호우경보", "호우", "경보", true),
            WeatherWarning(now, "풍랑경보", "풍랑", "경보", true),
        )
        assertEquals(WarningLevel.WARNING, Warnings.level(w))
        assertEquals(WarningLevel.ADVISORY, Warnings.level(w.take(1)))
        assertEquals(WarningLevel.NONE, Warnings.level(w.drop(2)))
    }
}
