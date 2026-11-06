package com.example.pleosproj.navi

import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.PreviewStates
import com.example.pleosproj.ui.state.RiskLevel
import com.example.pleosproj.ui.state.TurnInstruction
import com.example.pleosproj.ui.state.TurnKind
import com.example.pleosproj.voice.VoiceOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class GuidanceVoiceTest {

    /** 말한 문장을 모아 두는 가짜 음성 */
    private class FakeVoice : VoiceOutput {
        override val name = "fake"
        val said = mutableListOf<String>()
        var released = false
        override fun speak(text: String, flush: Boolean) { said += text }
        override fun stop() {}
        override fun release() { released = true }
    }

    private val fake = FakeVoice()
    private var ready: ((VoiceOutput) -> Unit)? = null

    /** 준비 완료를 테스트가 직접 정하는 GuidanceVoice */
    private fun voice() = GuidanceVoice { onReady -> ready = onReady }
    private fun becomeReady() = ready!!.invoke(fake)

    private val briefing = BriefingState("삼성역", LocalTime.of(18, 28), 1080, emptyList(), RiskLevel.NONE, emptyList(), true)
    private fun guidance(turnMeters: Int) = PreviewStates.guidanceScenario.copy(
        nextTurn = TurnInstruction(TurnKind.LEFT, turnMeters, "언주로로 좌회전"), alert = null,
    )

    @Test
    fun `준비 전에 요청한 브리핑을 준비되면 한 번 말한다`() {
        val v = voice()
        v.speakBriefing(briefing)
        assertTrue(fake.said.isEmpty())
        becomeReady()
        assertEquals(1, fake.said.size)
        assertTrue(fake.said[0].startsWith("삼성역까지 18분"))
    }

    @Test
    fun `준비 전 첫 안내를 잃지 않고 지금 것만 말한다`() {
        val v = voice()
        // 준비 전에 1km 기준을 지나 300m 기준까지 왔다
        v.onState(guidance(900))
        v.onState(guidance(290))
        becomeReady()
        assertEquals(listOf("300미터 앞에서 언주로로 좌회전입니다."), fake.said)
    }

    @Test
    fun `준비 전에 음성을 끄면 맡아 둔 것을 말하지 않는다`() {
        val v = voice()
        v.speakBriefing(briefing)
        v.onState(guidance(290))
        v.enabled = false
        becomeReady()
        assertTrue(fake.said.isEmpty())
    }

    @Test
    fun `준비 전에 해제하면 준비된 음성도 바로 해제한다`() {
        val v = voice()
        v.speakBriefing(briefing)
        v.release()
        becomeReady()
        assertTrue(fake.said.isEmpty())
        assertTrue(fake.released)
    }

    @Test
    fun `준비된 뒤에는 바로 말한다`() {
        val v = voice()
        becomeReady()
        v.onState(guidance(290))
        v.speakBriefing(briefing)
        assertEquals(2, fake.said.size)
    }
}
