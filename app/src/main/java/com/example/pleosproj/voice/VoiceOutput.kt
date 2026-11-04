package com.example.pleosproj.voice

/** 음성 출력 (ADR 0006). Pleos TextToSpeech를 먼저 쓰고, 안 되면 안드로이드 TextToSpeech. 고르는 건 [Voices]. */
interface VoiceOutput {
    val name: String

    /** [flush]가 true면 말하던 것을 끊고 바로 말한다 (위험 경고용) */
    fun speak(text: String, flush: Boolean = true)
    fun stop()
    fun release()
}

/** 음성 입력. 한 번 듣고 결과를 돌려준다. */
interface VoiceInput {
    val name: String

    fun listen(listener: Listener)
    fun stop()
    fun release()

    interface Listener {
        /** 말하는 중간 결과 */
        fun onPartial(text: String) {}
        fun onResult(text: String)
        fun onError(reason: String)
    }
}

/** 음성 출력이 모두 안 될 때 쓰는 빈 구현. 말할 문장을 로그에만 남기고, 화면 알림은 그대로 동작하게 한다. */
object SilentVoiceOutput : VoiceOutput {
    override val name = "음성 없음"
    override fun speak(text: String, flush: Boolean) {
        android.util.Log.w("Voices", "음성 출력 없음, 말하지 못한 문장: $text")
    }
    override fun stop() {}
    override fun release() {}
}
