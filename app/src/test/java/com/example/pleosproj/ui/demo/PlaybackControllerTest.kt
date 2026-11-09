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
    fun seekingWhilePlayingKeepsFixedSpeedAndRejectsOldPlayerUpdates() {
        val controller = controller()
        controller.play()
        val first = players.single()
        first.emit(137.25)
        controller.seekTo(137.25)
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
    fun seekingWhilePausedPublishesNewPositionWithoutRestarting() {
        val controller = controller()
        controller.play()
        players.single().emit(10.0)
        controller.stop()
        controller.seekTo(600.0)
        assertFalse(controller.playing)
        assertFalse(players.last().running)
        assertEquals(600.0, controller.seconds, 0.0)
    }

    @Test
    fun resetStopsPlaybackAndPublishesBeginningImmediately() {
        val controller = controller()
        controller.play()
        players.single().emit(500.0)
        controller.reset()
        assertFalse(controller.playing)
        assertFalse(players.last().running)
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

    @Test
    fun presentationClockMovesBetweenSnapshotsAndFreezesWithPlayer() {
        val controller = controller()
        controller.play()
        players.single().emit(10.0)
        players.single().seconds = 10.375
        assertEquals(10.0, controller.seconds, 0.0)
        assertEquals(10.375, controller.presentationSeconds, 0.0)
        controller.stop()
        assertEquals(10.375, controller.presentationSeconds, 0.0)
        assertFalse(controller.playing)
        controller.reset()
        assertEquals(0.0, controller.presentationSeconds, 0.0)
    }

    @Test
    fun oldPlayerCallbacksDoNotReachTheVoiceListenerAfterSeekOrDisposal() {
        val states = mutableListOf<GuidanceState>()
        val controller = PlaybackController(PreviewStates.guidanceScenario, 1080,
            onGuidance = { states += it }) { rate, callback -> FakePlayer(rate, callback).also(players::add) }
        players.single().emit(20.0)
        val first = players.single()
        controller.seekTo(400.0)
        val count = states.size
        first.emit(500.0)
        assertEquals(count, states.size)
        controller.dispose()
        players.last().emit(600.0)
        assertEquals(count, states.size)
    }

    @Test
    fun initialAndResetPlayersAlwaysRunAtFourTimesSpeed() {
        val controller = controller()
        assertEquals(4.0, controller.speed, 0.0)
        assertEquals(4.0, players.single().rate, 0.0)
        controller.play()
        players.last().emit(500.0)
        controller.reset()
        controller.play()
        assertTrue(players.all { it.rate == 4.0 })
    }

    @Test
    fun seekingClampsBoundsAndCanRewindFromFinish() {
        val controller = controller()
        controller.play()
        controller.seekTo(2000.0)
        assertTrue(controller.finished)
        assertFalse(controller.playing)
        controller.seekTo(200.0)
        assertFalse(controller.finished)
        assertFalse(controller.playing)
        assertEquals(880, controller.guidance.remainingSeconds)
        controller.seekTo(-50.0)
        assertEquals(0.0, controller.seconds, 0.0)
        controller.seekTo(Double.NaN)
        controller.seekTo(Double.POSITIVE_INFINITY)
        assertEquals(0.0, controller.seconds, 0.0)
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
