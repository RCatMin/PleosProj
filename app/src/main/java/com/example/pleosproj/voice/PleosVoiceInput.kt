package com.example.pleosproj.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Pleos SpeechToText 구현 (ON_DEVICE 모드). */
class PleosVoiceInput private constructor(private val stt: SpeechToText) : VoiceInput {

    override val name = "Pleos SpeechToText"

    private val main = Handler(Looper.getMainLooper())
    private var current: ResultListener? = null

    override fun listen(listener: VoiceInput.Listener) {
        current?.let { runCatching { stt.removeListener(it) } }
        val l = object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                main.post { if (isFinal) listener.onResult(text) else listener.onPartial(text) }
            }
            override fun onUpdatedEpdData(start: Long, end: Long) {}
            override fun onStartedRecognition() {}
            override fun onEndedRecognition() {}
            override fun onReady() {}
            override fun onUpdatedRms(rms: Double) {}
            override fun onError(errorType: ErrorType) {
                main.post { listener.onError(errorType.toString()) }
            }
        }
        current = l
        stt.addListener(l)
        stt.request()
    }

    override fun stop() {
        runCatching { stt.stop() }
    }

    override fun release() {
        runCatching { stt.release() }
    }

    companion object {
        fun create(context: Context, onReady: (PleosVoiceInput) -> Unit, onFailure: (String) -> Unit) {
            val stt = runCatching { SpeechToText(context.applicationContext, Mode.ON_DEVICE) }
                .getOrElse { onFailure("생성 실패: ${it.message}"); return }
            runCatching {
                stt.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        Handler(Looper.getMainLooper()).post { onReady(PleosVoiceInput(stt)) }
                    }

                    override fun onFailure(errorType: ErrorType) {
                        runCatching { stt.release() }
                        onFailure("초기화 실패: $errorType")
                    }
                })
            }.onFailure { onFailure("초기화 실패: ${it.message}") }
        }
    }
}
