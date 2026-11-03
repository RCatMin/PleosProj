package com.example.pleosproj.scenario

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.pleosproj.ui.state.GuidanceState

/**
 * 시나리오를 1초마다 재생해 안내 화면 상태를 넘겨준다. 메인 스레드에서 만들고 부른다.
 * [speed]가 2면 실제 시간의 두 배로 달린다 (시연 영상 길이 조절용).
 */
class ScenarioPlayer(
    val scenario: Scenario,
    private val speed: Double = 1.0,
    private val onState: (seconds: Double, state: GuidanceState) -> Unit,
) {
    val engine = ScenarioEngine(scenario)

    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var offset = 0.0
    private var running = false

    /** 재생 시작 후 흐른 시나리오 시간(초) */
    val seconds: Double
        get() = if (running) offset + (SystemClock.elapsedRealtime() - startedAt) / 1000.0 * speed else offset

    val finished get() = seconds >= scenario.driveRoute.durationSeconds

    private val tick = object : Runnable {
        override fun run() {
            val t = seconds
            onState(t, engine.guidance(t))
            if (t >= scenario.driveRoute.durationSeconds) {
                pause()
                return
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun play() {
        if (running || finished) return
        startedAt = SystemClock.elapsedRealtime()
        running = true
        handler.post(tick)
    }

    fun pause() {
        offset = seconds
        running = false
        handler.removeCallbacks(tick)
    }

    /** 원하는 시점으로 건너뛴다. 시연 중 위험 구간 바로 앞으로 갈 때 쓴다. */
    fun seek(toSeconds: Double) {
        val wasRunning = running
        pause()
        offset = toSeconds.coerceIn(0.0, scenario.driveRoute.durationSeconds.toDouble())
        onState(offset, engine.guidance(offset))
        if (wasRunning) play()
    }

    fun stop() = pause()

    companion object {
        /** assets/scenarios/<id>.json 을 읽는다 */
        fun load(context: Context, id: String): Scenario =
            context.assets.open("scenarios/$id.json").bufferedReader().use { ScenarioParser.parse(it.readText()) }
    }
}
