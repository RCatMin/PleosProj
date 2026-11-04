package com.example.pleosproj.ui.demo

import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.PreviewStates
import org.junit.Assert.*
import org.junit.Test

class PlaybackControllerTest {
    private val players = mutableListOf<FakePlayer>()
    private fun controller() = PlaybackController(PreviewStates.guidanceScenario, 1080) { rate, callback ->
        FakePlayer(rate, callback).also(players::add)
    }

    @Test
    fun changingSpeedKeepsExactPositionAndRejectsOldPlayerUpdates() {
        val controller = controller()
        controller.play()
        val first = players.single()
        first.emit(137.25)
        controller.changeSpeed(4.0)
        assertFalse(first.running)
        assertEquals(4.0, players.last().rate, 0.0)
        assertEquals(137.25, players.last().seconds, 0.0)
        assertTrue(players.last().running)
        first.emit(200.0)
        assertEquals(137.25, controller.seconds, 0.0)
        players.last().emit(141.25)
        assertEquals(141.25, controller.seconds, 0.0)
    }

    @Test
    fun changingSpeedWhilePausedDoesNotRestartPlayback() {
        val controller = controller()
        controller.play()
        players.single().emit(10.0)
        controller.stop()
        controller.changeSpeed(2.0)
        assertFalse(controller.playing)
        assertFalse(players.last().running)
        assertEquals(10.0, controller.seconds, 0.0)
    }

    @Test
    fun resetStopsPlaybackAndPublishesBeginningImmediately() {
        val controller = controller()
        controller.play()
        players.single().emit(500.0)
        controller.reset()
        assertFalse(controller.playing)
        assertFalse(players.single().running)
        assertEquals(0.0, controller.seconds, 0.0)
        assertEquals(1080, controller.guidance.remainingSeconds)
    }

    @Test
    fun finishClampsTimeAndPlayCanRestartFromBeginning() {
        val controller = controller()
        controller.play()
        players.single().emit(1080.5)
        assertTrue(controller.finished)
        assertFalse(controller.playing)
        assertEquals(1080.0, controller.seconds, 0.0)
        controller.play()
        assertEquals(0.0, controller.seconds, 0.0)
        assertFalse(controller.finished)
        assertTrue(controller.playing)
    }

    @Test
    fun disposalStopsPlayerAndIgnoresLateCallbacks() {
        val controller = controller()
        controller.play()
        players.single().emit(10.0)
        controller.dispose()
        players.single().emit(20.0)
        assertFalse(players.single().running)
        assertFalse(controller.playing)
        assertEquals(10.0, controller.seconds, 0.0)
    }

    private class FakePlayer(val rate: Double, val callback: (Double, GuidanceState) -> Unit) : DemoPlayer {
        override var seconds = 0.0
        var running = false
        override fun play() { running = true }
        override fun stop() { running = false }
        override fun seek(seconds: Double) { emit(seconds) }
        fun emit(time: Double) {
            seconds = time
            callback(time, PreviewStates.guidanceScenario.copy(remainingSeconds = (1080 - time).toInt().coerceAtLeast(0)))
        }
    }
}
