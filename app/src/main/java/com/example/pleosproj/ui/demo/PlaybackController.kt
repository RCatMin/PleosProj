package com.example.pleosproj.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.ui.state.GuidanceState

internal interface DemoPlayer {
    val seconds: Double
    fun play()
    fun stop()
    fun seek(seconds: Double)
}

// 시연은 4배속으로 고정한다. 이동 시 이전 재생기를 폐기하며 모든 호출은 메인 스레드에서 한다.
internal class PlaybackController(
    initial: GuidanceState,
    val durationSeconds: Int,
    private val onGuidance: ((GuidanceState) -> Unit)? = null,
    private val createPlayer: (Double, (Double, GuidanceState) -> Unit) -> DemoPlayer,
) {
    var guidance by mutableStateOf(initial)
        private set
    var seconds by mutableStateOf(0.0)
        private set
    val speed = 4.0
    var playing by mutableStateOf(false)
        private set
    val presentationSeconds get() = player.seconds.coerceIn(0.0, durationSeconds.toDouble())
    val finished get() = seconds >= durationSeconds
    private var generation = 0
    private var player = newPlayer()

    private fun newPlayer(): DemoPlayer {
        val current = ++generation
        return createPlayer(speed) { time, state ->
            if (current == generation) {
                seconds = time.coerceIn(0.0, durationSeconds.toDouble())
                guidance = state
                onGuidance?.invoke(state)
                if (finished) playing = false
            }
        }
    }

    fun play() {
        if (finished) reset()
        playing = true
        player.play()
    }

    fun stop() {
        player.stop()
        playing = false
        player.seek(player.seconds)
    }

    fun reset() {
        playing = false
        seekTo(0.0)
    }

    fun seekTo(targetSeconds: Double) {
        if (!targetSeconds.isFinite()) return
        val resume = playing
        player.stop()
        playing = false
        player = newPlayer()
        player.seek(targetSeconds.coerceIn(0.0, durationSeconds.toDouble()))
        if (resume && !finished) play()
    }

    fun dispose() {
        ++generation
        playing = false
        player.stop()
    }
}
