package com.example.pleosproj.navi

import android.content.Context
import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.voice.VoiceOutput
import com.example.pleosproj.voice.Voices

/**
 * 우리 내비의 안내 음성. 안내 화면 상태를 받을 때마다 회전 안내와 위험 경고 중 말할 것을 골라
 * Pleos TTS(안 되면 안드로이드 TTS)로 말한다. 실제 주행 세션과 시연 재생 양쪽에서 쓴다.
 * 메인 스레드에서 만들고 부른다.
 */
class GuidanceVoice(context: Context) {

    /** 음성을 낼지. 화면의 "음성 켜짐" 버튼과 연결한다 */
    var enabled = true
        set(value) {
            field = value
            if (!value) voice?.stop()
        }

    private var voice: VoiceOutput? = null
    private var released = false
    private val alerts = AlertSpeech()
    private val turns = TurnSpeech()

    init {
        Voices.output(context) { v -> if (released) v.release() else voice = v }
    }

    /** 새 화면 상태. 안내가 끝나면 null을 넘긴다 */
    fun onState(state: GuidanceState?) {
        // 음성이 꺼져 있어도 판단은 계속해서, 켰을 때 지난 안내를 몰아서 말하지 않게 한다
        val alert = alerts.next(state?.alert)
        val turn = turns.next(state?.nextTurn)
        if (!enabled) return
        // 위험 경고가 먼저다. 회전 안내는 그 뒤에 이어서 말한다
        alert?.let { voice?.speak(it.text, it.flush) }
        turn?.let { voice?.speak(it.text, flush = false) }
    }

    /** 출발 전 브리핑을 읽는다 */
    fun speakBriefing(b: BriefingState) {
        if (enabled) voice?.speak(briefingSentence(b), flush = true)
    }

    fun release() {
        released = true
        voice?.release()
        voice = null
    }

    companion object {
        /** 예: "삼성역까지 18분, 18시 28분 도착 예정입니다. 15분 뒤 시간당 60밀리미터 비 예보. 안전 운전하세요." */
        fun briefingSentence(b: BriefingState): String {
            val minutes = (b.durationSeconds + 30) / 60
            val lines = b.riskLines.joinToString(" ") { line ->
                line.replace(Regex("(\\d+)mm")) { "${it.groupValues[1]}밀리미터" }.trimEnd('.') + "."
            }
            return listOf(
                "${b.destinationName}까지 ${minutes}분, ${b.arrivalTime.hour}시 ${b.arrivalTime.minute}분 도착 예정입니다.",
                lines,
                if (b.riskLines.isEmpty()) "경로에 위험 구간이 없습니다." else "안전 운전하세요.",
            ).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
}
