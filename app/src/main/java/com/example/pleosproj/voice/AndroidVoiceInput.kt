package com.example.pleosproj.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** 안드로이드 SpeechRecognizer 구현. RECORD_AUDIO 권한이 있어야 한다. 메인 스레드에서 부른다. */
class AndroidVoiceInput private constructor(private val recognizer: SpeechRecognizer) : VoiceInput {

    override val name = "Android SpeechRecognizer"

    override fun listen(listener: VoiceInput.Listener) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) listener.onResult(text) else listener.onError("결과 없음")
            }

            override fun onPartialResults(partial: Bundle) {
                partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(listener::onPartial)
            }

            override fun onError(error: Int) = listener.onError("SpeechRecognizer 오류 $error")
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
    }

    override fun stop() = recognizer.stopListening()

    override fun release() = recognizer.destroy()

    companion object {
        fun create(context: Context, onReady: (AndroidVoiceInput) -> Unit, onFailure: (String) -> Unit) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onFailure("음성 인식 서비스 없음")
                return
            }
            onReady(AndroidVoiceInput(SpeechRecognizer.createSpeechRecognizer(context.applicationContext)))
        }
    }
}
