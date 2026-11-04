package com.example.pleosproj.ui.demo

import android.content.Context
import com.example.pleosproj.scenario.ScenarioEngine
import com.example.pleosproj.scenario.ScenarioPlayer

// 재생기와 화면을 조립하는 연결부다. 엔진이 만든 화면 상태를 그대로 전달한다.
internal class DemoSession(context: Context) {
    private val scenario = ScenarioPlayer.load(context, "rain-commute")
    private val engine = ScenarioEngine(scenario)
    val title = scenario.title
    val driveRouteId = scenario.driveRouteId
    val driveRouteName = scenario.driveRoute.name
    val comparison = engine.compare()
    val briefing = engine.briefing(speaking = false)
    val playback = PlaybackController(engine.guidance(0.0), scenario.driveRoute.durationSeconds) { speed, onState ->
        val delegate = ScenarioPlayer(scenario, speed, onState)
        object : DemoPlayer {
            override val seconds get() = delegate.seconds
            override fun play() = delegate.play()
            override fun stop() = delegate.stop()
            override fun seek(seconds: Double) = delegate.seek(seconds)
        }
    }
}
