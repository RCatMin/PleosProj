package com.example.pleosproj.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.GuidanceState

internal interface DemoSpeech {
    var enabled: Boolean
    fun onState(state: GuidanceState)
    fun speakBriefing(state: BriefingState)
    fun release()
}

// 시연 페이지와 재생 상태에 맞춰 음성 수명을 관리한다. 한 번의 재생이 끝나면 판단 이력도 새로 만든다.
internal class DemoVoiceController(private val createSpeech: () -> DemoSpeech) {
    var enabled by mutableStateOf(true)
        private set
    private var speech: DemoSpeech? = null
    private var foreground = true
    private var active = false

    fun briefing(state: BriefingState) {
        replaceSpeech()
        active = true
        updateEnabled()
        speech?.speakBriefing(state)
    }

    fun beginGuidance() {
        replaceSpeech()
        active = false
        updateEnabled()
    }

    fun setPlaying(playing: Boolean) {
        active = playing
        updateEnabled()
    }

    fun onState(state: GuidanceState) {
        // 일시정지·배속 변경·처음으로에서 오는 seek 결과는 발화 이력에 넣지 않는다.
        if (foreground && active) speech?.onState(state)
    }

    fun toggle() {
        enabled = !enabled
        updateEnabled()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        updateEnabled()
    }

    fun release() {
        active = false
        speech?.release()
        speech = null
    }

    private fun replaceSpeech() {
        release()
        speech = createSpeech()
    }

    private fun updateEnabled() {
        speech?.enabled = enabled && foreground && active
    }
}
