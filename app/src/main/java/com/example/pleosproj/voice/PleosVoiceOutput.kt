package com.example.pleosproj.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Pleos TextToSpeech 구현 (기기 안에서 합성하는 ON_DEVICE 모드). */
class PleosVoiceOutput private constructor(private val tts: TextToSpeech) : VoiceOutput {

    override val name = "Pleos TextToSpeech"

    private val main = Handler(Looper.getMainLooper())

    // Pleos TTS는 초기화 콜백 안이나 다른 스레드에서 부르면 조용히 무시될 때가 있어 메인 스레드로 넘긴다.
    // speak()의 두 번째 값은 "이 문장이 끝인지(isFinal)"다. false로 보내면 다음 문장을 기다리며 말하지 않으므로
    // 항상 true로 보내고, 끊어야 할 때는 먼저 stop()을 부른다
    override fun speak(text: String, flush: Boolean) {
        main.post {
            runCatching {
                if (flush) tts.stop()
                tts.speak(text, true)
            }.onFailure { Log.e(TAG, "speak 실패", it) }
        }
    }

    override fun stop() {
        main.post { runCatching { tts.stop() } }
    }

    override fun release() {
        runCatching { tts.release() }
    }

    companion object {
        private const val TAG = "Voices"

        /** 초기화가 끝나면 [onReady], 실패하면 [onFailure]를 부른다 */
        fun create(context: Context, onReady: (PleosVoiceOutput) -> Unit, onFailure: (String) -> Unit) {
            val tts = runCatching { TextToSpeech(context.applicationContext, Mode.ON_DEVICE) }
                .getOrElse { onFailure("생성 실패: ${it.message}"); return }
            runCatching {
                tts.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        // 리스너는 초기화가 끝난 뒤에만 붙일 수 있다
                        runCatching { tts.addEventListener(errorLogger) }
                        Handler(Looper.getMainLooper()).post { onReady(PleosVoiceOutput(tts)) }
                    }

                    override fun onFailure(errorType: ErrorType) {
                        runCatching { tts.release() }
                        onFailure("초기화 실패: $errorType")
                    }
                })
            }.onFailure { onFailure("초기화 실패: ${it.message}") }
        }

        private val errorLogger = object : EventListener {
            override fun onStart() {}
            override fun onReady() {}
            override fun onDone() {}
            override fun onStop() {}
            override fun onUpdatedRms(rms: Double) {}
            override fun onError(errorType: ErrorType) {
                Log.e(TAG, "Pleos TTS 오류: $errorType")
            }
        }
    }
}
