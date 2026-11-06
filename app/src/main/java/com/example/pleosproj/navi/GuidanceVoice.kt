package com.example.pleosproj.navi

import android.content.Context
import com.example.pleosproj.ui.state.BriefingState
import com.example.pleosproj.ui.state.GuidanceState
import com.example.pleosproj.voice.VoiceOutput
import com.example.pleosproj.voice.Voices

/**
 * 우리 내비의 안내 음성. 안내 화면 상태를 받을 때마다 회전 안내와 위험 경고 중 말할 것을 골라
 * Pleos TTS(안 되면 안드로이드 TTS)로 말한다. 실제 주행 세션과 시연 재생 양쪽에서 쓴다.
 * 음성 준비(수백 ms)가 끝나기 전에 들어온 브리핑과 안내도 잃지 않는다 (PLE-32).
 * 메인 스레드에서 만들고 부른다.
 */
class GuidanceVoice internal constructor(start: ((VoiceOutput) -> Unit) -> Unit) {

    constructor(context: Context) : this({ onReady -> Voices.output(context, onReady = onReady) })

    /** 음성을 낼지. 화면의 "음성 켜짐" 버튼과 연결한다 */
    var enabled = true
        set(value) {
            field = value
            if (!value) {
                voice?.stop()
                // 꺼진 뒤에는 준비 중에 맡아 둔 것도 말하지 않는다
                pendingBriefing = null
            }
        }

    private var voice: VoiceOutput? = null
    private var released = false
    private val alerts = AlertSpeech()
    private val turns = TurnSpeech()

    // 음성이 준비되기 전에 들어온 요청. 준비되면 지금 시점 것만 한 번 처리한다
    private var pendingBriefing: BriefingState? = null
    private var latestState: GuidanceState? = null
    private var sawState = false

    init {
        start { v ->
            if (released) {
                v.release()
                return@start
            }
            voice = v
            pendingBriefing?.let { if (enabled) v.speak(briefingSentence(it), flush = true) }
            pendingBriefing = null
            // 준비 전 마지막 상태로 한 번 판단한다. 지나간 안내를 몰아서 말하지 않고 지금 것만 말한다
            if (sawState) onState(latestState)
        }
    }

    /** 새 화면 상태. 안내가 끝나면 null을 넘긴다. 메인 스레드에서 부른다 */
    fun onState(state: GuidanceState?) {
        if (voice == null) {
            // 준비 전에는 판단하지 않고 마지막 상태만 기억한다 (판단 이력을 미리 써 버리면 첫 안내가 사라진다)
            latestState = state
            sawState = true
            return
        }
        // 음성이 꺼져 있어도 판단은 계속해서, 켰을 때 지난 안내를 몰아서 말하지 않게 한다
        val alert = alerts.next(state?.alert)
        val turn = turns.next(state?.nextTurn)
        if (!enabled) return
        // 위험 경고가 먼저다. 회전 안내는 그 뒤에 이어서 말한다
        alert?.let { voice?.speak(it.text, it.flush) }
        turn?.let { voice?.speak(it.text, flush = false) }
    }

    /** 출발 전 브리핑을 읽는다. 음성이 아직 준비 중이면 준비되는 대로 한 번 읽는다 */
    fun speakBriefing(b: BriefingState) {
        if (!enabled || released) return
        val v = voice
        if (v == null) pendingBriefing = b else v.speak(briefingSentence(b), flush = true)
    }

    fun release() {
        released = true
        pendingBriefing = null
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
