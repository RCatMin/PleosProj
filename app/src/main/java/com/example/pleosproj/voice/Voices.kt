package com.example.pleosproj.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 음성 출력·입력 구현을 고른다 (ADR 0006). Pleos SDK를 먼저 초기화해 보고,
 * 실패하거나 [timeoutMillis] 안에 답이 없으면 안드로이드 기본 기능으로 바꾼다. 어떤 구현을 골랐는지 로그에 남긴다.
 * 콜백은 메인 스레드에서 한 번만 불린다. 음성 출력이 모두 안 되면 [SilentVoiceOutput]을 넘긴다.
 */
object Voices {

    private const val TAG = "Voices"
    private const val timeoutMillis = 5_000L

    fun output(context: Context, preferPleos: Boolean = true, onReady: (VoiceOutput) -> Unit) {
        val fallback = { reason: String ->
            Log.w(TAG, "Pleos TextToSpeech 사용 불가, 안드로이드 TTS로 바꿈: $reason")
            AndroidVoiceOutput.create(context, onReady = { chosen(it, onReady) }, onFailure = {
                Log.e(TAG, "안드로이드 TTS도 실패: $it")
                chosen(SilentVoiceOutput, onReady)
            })
        }
        if (!preferPleos) return fallback("확인용으로 끔")
        firstOf(
            start = { ok, fail -> PleosVoiceOutput.create(context, ok, fail) },
            onReady = { chosen(it, onReady) },
            onFallback = fallback,
            discard = { it.release() },
        )
    }

    fun input(context: Context, preferPleos: Boolean = true, onReady: (VoiceInput) -> Unit) {
        val fallback = { reason: String ->
            Log.w(TAG, "Pleos SpeechToText 사용 불가, 안드로이드 음성 인식으로 바꿈: $reason")
            AndroidVoiceInput.create(context, onReady = { chosen(it, onReady) }, onFailure = { Log.e(TAG, "안드로이드 음성 인식도 실패: $it") })
        }
        if (!preferPleos) return fallback("확인용으로 끔")
        firstOf(
            start = { ok, fail -> PleosVoiceInput.create(context, ok, fail) },
            onReady = { chosen(it, onReady) },
            onFallback = fallback,
            discard = { it.release() },
        )
    }

    private fun <T> chosen(impl: T, onReady: (T) -> Unit) {
        Log.i(TAG, "음성 구현: ${(impl as? VoiceOutput)?.name ?: (impl as VoiceInput).name}")
        onReady(impl)
    }

    /** Pleos 초기화 결과와 시간 초과 중 먼저 온 쪽 하나만 따른다. 늦게 성공한 Pleos 구현은 바로 해제한다 */
    private fun <T> firstOf(
        start: (ok: (T) -> Unit, fail: (String) -> Unit) -> Unit,
        onReady: (T) -> Unit,
        onFallback: (String) -> Unit,
        discard: (T) -> Unit,
    ) {
        val main = Handler(Looper.getMainLooper())
        var settled = false
        val timeout = Runnable { if (!settled) { settled = true; onFallback("${timeoutMillis}ms 안에 초기화 응답 없음") } }
        main.postDelayed(timeout, timeoutMillis)
        start(
            { impl -> main.post { if (settled) discard(impl) else { settled = true; main.removeCallbacks(timeout); onReady(impl) } } },
            { reason -> main.post { if (!settled) { settled = true; main.removeCallbacks(timeout); onFallback(reason) } } },
        )
    }
}
