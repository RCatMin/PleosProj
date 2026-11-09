package com.example.pleosproj.ui.guidance

import com.example.pleosproj.ui.state.WeatherCard

// 목적지·시간 기준 예보를 거리 기준 예보로 바꾸지 않는다. 목적지보다 먼 지점도 표시하지 않는다.
internal fun guidanceWeatherSlots(weather: List<WeatherCard>, remainingMeters: Int): List<WeatherCard?> =
    listOf("지금" to 0, "10km 앞" to 10_000, "20km 앞" to 20_000).map { (label, meters) ->
        if (meters > 0 && meters >= remainingMeters) null else weather.firstOrNull { it.label == label }
    }
