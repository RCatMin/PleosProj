package com.example.pleosproj.ui.demo

import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.ui.state.PreviewStates
import org.junit.Assert.*
import org.junit.Test

class DemoVoiceControllerTest {
    private val speakers = mutableListOf<FakeSpeech>()
    private fun controller() = DemoVoiceController { FakeSpeech().also(speakers::add) }

    @Test fun briefingIsRequestedOnceAndLeavingItReleasesTheOldSpeaker() {
        val controller = controller()
        controller.briefing(PreviewStates.briefing)
        val first = speakers.single()
        assertEquals(listOf(PreviewStates.briefing), first.briefings)
        assertTrue(first.enabled)
        controller.beginGuidance()
        assertTrue(first.released)
        assertEquals(2, speakers.size)
        assertFalse(speakers.last().enabled)
    }

    @Test fun pauseAndSeekDoNotAdvanceSpeechButMutedPlaybackStillTracksCurrentState() {
        val controller = controller()
        controller.beginGuidance()
        val speaker = speakers.single()
        controller.onState(PreviewStates.guidanceScenario)
        assertTrue(speaker.states.isEmpty())
        controller.setPlaying(true)
        controller.onState(PreviewStates.guidanceScenario)
        controller.toggle()
        assertFalse(speaker.enabled)
        controller.onState(PreviewStates.guidanceScenario.copy(remainingMeters = 300))
        assertEquals(2, speaker.states.size)
        controller.setPlaying(false)
        controller.onState(PreviewStates.guidanceScenario.copy(remainingMeters = 100))
        assertEquals(2, speaker.states.size)
    }

    @Test fun resettingCreatesNewSpeechHistoryAndPreservesMuteChoice() {
        val controller = controller()
        controller.beginGuidance()
        controller.setPlaying(true)
        controller.toggle()
        val first = speakers.single()
        controller.beginGuidance()
        controller.setPlaying(true)
        assertTrue(first.released)
        assertFalse(controller.enabled)
        assertFalse(speakers.last().enabled)
        controller.toggle()
        assertTrue(speakers.last().enabled)
    }

    @Test fun backgroundAndExitStopSpeechAndLateStatesAreIgnored() {
        val controller = controller()
        controller.beginGuidance()
        controller.setPlaying(true)
        val speaker = speakers.single()
        controller.setForeground(false)
        assertFalse(speaker.enabled)
        controller.onState(PreviewStates.guidanceScenario)
        assertTrue(speaker.states.isEmpty())
        controller.setPlaying(false)
        controller.setForeground(true)
        assertFalse(speaker.enabled)
        controller.release()
        controller.release()
        controller.onState(PreviewStates.guidanceScenario)
        assertTrue(speaker.released)
        assertTrue(speaker.states.isEmpty())
    }

    private class FakeSpeech : DemoSpeech {
        override var enabled = true
        val states = mutableListOf<GuidanceState>()
        val briefings = mutableListOf<BriefingState>()
        var released = false
        override fun onState(state: GuidanceState) { states += state }
        override fun speakBriefing(state: BriefingState) { briefings += state }
        override fun release() { released = true }
    }
}
