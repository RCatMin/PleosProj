package com.example.pleosproj.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** 안드로이드 TextToSpeech 구현. 한국어 음성을 쓴다. */
class AndroidVoiceOutput private constructor(private val tts: TextToSpeech) : VoiceOutput {

    override val name = "Android TextToSpeech"

    override fun speak(text: String, flush: Boolean) {
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, mode, null, text.hashCode().toString())
    }

    override fun stop() {
        tts.stop()
    }

    override fun release() {
        tts.shutdown()
    }

    companion object {
        fun create(context: Context, onReady: (AndroidVoiceOutput) -> Unit, onFailure: (String) -> Unit) {
            val main = Handler(Looper.getMainLooper())
            var tts: TextToSpeech? = null
            // 엔진이 없으면 생성자 안에서 바로 결과가 와서 tts가 아직 비어 있다. 메인 스레드로 한 번 넘겨 처리한다
            tts = TextToSpeech(context.applicationContext) { status ->
                main.post {
                    val t = tts ?: return@post onFailure("엔진 없음")
                    if (status != TextToSpeech.SUCCESS) {
                        t.shutdown()
                        onFailure("초기화 실패: $status")
                        return@post
                    }
                    val lang = t.setLanguage(Locale.KOREAN)
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 한국어가 없어도 기본 언어로라도 말하게 둔다
                        Log.w("Voices", "안드로이드 TTS에 한국어 음성 없음")
                    }
                    onReady(AndroidVoiceOutput(t))
                }
            }
        }
    }
}
