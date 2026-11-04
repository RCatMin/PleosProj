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

// 배속 변경 시 이전 재생기를 멈추고 위치를 이어받는다. 모든 호출은 메인 스레드에서 한다.
internal class PlaybackController(
    initial: GuidanceState,
    val durationSeconds: Int,
    private val createPlayer: (Double, (Double, GuidanceState) -> Unit) -> DemoPlayer,
) {
    var guidance by mutableStateOf(initial)
        private set
    var seconds by mutableStateOf(0.0)
        private set
    var speed by mutableStateOf(1.0)
        private set
    var playing by mutableStateOf(false)
        private set
    val finished get() = seconds >= durationSeconds
    private var generation = 0
    private var player = newPlayer(1.0)

    private fun newPlayer(rate: Double): DemoPlayer {
        val current = ++generation
        return createPlayer(rate) { time, state ->
            if (current == generation) {
                seconds = time.coerceIn(0.0, durationSeconds.toDouble())
                guidance = state
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
        player.stop()
        playing = false
        player.seek(0.0)
    }

    fun changeSpeed(rate: Double) {
        require(rate in listOf(1.0, 2.0, 4.0))
        if (speed == rate) return
        val resume = playing
        player.stop()
        val position = player.seconds
        player = newPlayer(rate)
        speed = rate
        playing = false
        player.seek(position)
        if (resume && !finished) play()
    }

    fun dispose() {
        ++generation
        playing = false
        player.stop()
    }
}
