package com.example.pleosproj.ui.demo

import android.content.Context
import com.example.pleosproj.ui.map.RouteMotion
import com.example.pleosproj.ui.map.MapMotion
import com.example.pleosproj.ui.state.GeoPoint
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.scenario.ScenarioEngine
import com.example.pleosproj.scenario.ScenarioPlayer
import com.example.pleosproj.navi.GuidanceVoice

// 재생기와 화면을 조립하는 연결부다. 엔진이 만든 화면 상태를 그대로 전달한다.
internal class DemoSession(context: Context, scenarioId: String) {
    private val scenario = ScenarioPlayer.load(context, scenarioId)
    val voice = DemoVoiceController {
        val delegate = GuidanceVoice(context)
        object : DemoSpeech {
            override var enabled: Boolean
                get() = delegate.enabled
                set(value) { delegate.enabled = value }
            override fun onState(state: GuidanceState) = delegate.onState(state)
            override fun speakBriefing(state: BriefingState) = delegate.speakBriefing(state)
            override fun release() = delegate.release()
        }
    }
    private val engine = ScenarioEngine(scenario)
    val title = scenario.title
    val driveRouteId = scenario.driveRouteId
    val driveRouteName = scenario.driveRoute.name
    private val motion = RouteMotion(scenario.driveRoute.line.map { GeoPoint(it.lat, it.lng) })
    fun motionFrame(): MapMotion = motion.at(playback.presentationSeconds / playback.durationSeconds)
    val comparison = engine.compare()
    val briefing = engine.briefing(speaking = false)
    val playback = PlaybackController(engine.guidance(0.0), scenario.driveRoute.durationSeconds,
        onGuidance = voice::onState) { speed, onState ->
        val delegate = ScenarioPlayer(scenario, speed, onState)
        object : DemoPlayer {
            override val seconds get() = delegate.seconds
            override fun play() = delegate.play()
            override fun stop() = delegate.stop()
            override fun seek(seconds: Double) = delegate.seek(seconds)
        }
    }

    fun play() { voice.setPlaying(true); playback.play() }
    fun pause() { voice.setPlaying(false); playback.stop() }
    fun reset() { voice.beginGuidance(); playback.reset() }
    fun changeSpeed(speed: Double) {
        voice.setPlaying(false)
        playback.changeSpeed(speed)
        voice.setPlaying(playback.playing)
    }
    fun dispose() { voice.release(); playback.dispose() }
}
