package com.example.pleosproj.app

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.EventListener as TtsEvents
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log

/**
 * 디버그 전용 확인 도구 (PLE-12): 콘솔에서 "준비 중"인 SDK가 에뮬레이터에서 실제로 동작하는지 본다.
 * `adb logcat -s SdkStatus`로 결과를 본다.
 */
object SdkStatusProbe {

    private const val TAG = "SdkStatus"
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var vehicle: Vehicle? = null
    private val front = WindowArea(WindowValue.FRONT_WINDSHIELD)

    fun run(context: Context, cmd: String, mode: String?) {
        Log.i(TAG, ">> $cmd ${mode.orEmpty()}")
        runCatching {
            when (cmd) {
                "tts_check" -> checkTts(context, mode)
                "stt_check" -> checkStt(context, mode)
                "vehicle_check" -> checkVehicle(context)
                "control_check" -> checkControl(context)
                else -> Log.w(TAG, "모르는 명령: $cmd")
            }
        }.onFailure { Log.e(TAG, "'$cmd' 실패: ${it::class.java.simpleName}: ${it.message}") }
    }

    private fun checkTts(context: Context, mode: String?) {
        tts?.release()
        val m = ai.pleos.playground.tts.constant.Mode.valueOf(mode ?: "ON_DEVICE")
        val t = TextToSpeech(context.applicationContext, m).also { tts = it }
        t.initialize(object : TtsInit {
            override fun onSuccess() {
                Log.i(TAG, "<< TTS 초기화 성공 ($m)")
                t.addEventListener(object : TtsEvents {
                    override fun onStart() { Log.i(TAG, "<< TTS onStart") }
                    override fun onReady() { Log.i(TAG, "<< TTS onReady") }
                    override fun onDone() { Log.i(TAG, "<< TTS onDone") }
                    override fun onError(errorType: TtsError) { Log.e(TAG, "<< TTS onError $errorType") }
                    override fun onStop() { Log.i(TAG, "<< TTS onStop") }
                    override fun onUpdatedRms(rms: Double) {}
                })
                t.speak("전방 오백 미터, 침수 위험 구간입니다.", false)
            }
            override fun onFailure(errorType: TtsError) { Log.e(TAG, "<< TTS 초기화 실패 $errorType ($m)") }
        })
    }

    private fun checkStt(context: Context, mode: String?) {
        stt?.release()
        val m = ai.pleos.playground.stt.constant.Mode.valueOf(mode ?: "ON_DEVICE")
        val s = SpeechToText(context.applicationContext, m).also { stt = it }
        s.initialize(object : SttInit {
            override fun onSuccess() { Log.i(TAG, "<< STT 초기화 성공 ($m)") }
            override fun onFailure(errorType: SttError) { Log.e(TAG, "<< STT 초기화 실패 $errorType ($m)") }
        })
    }

    private fun vehicle(context: Context): Vehicle =
        vehicle ?: Vehicle(context.applicationContext).also { it.initialize(); vehicle = it }

    private fun checkVehicle(context: Context) {
        val wiper = vehicle(context).getWiper()
        wiper.getCurrentWiperState(front, { Log.i(TAG, "<< 와이퍼 상태 $it") }, { Log.e(TAG, "<< 와이퍼 상태 실패: ${it.message}") })
        wiper.getCurrentWiperSwitch(front, { Log.i(TAG, "<< 와이퍼 스위치 $it") }, { Log.e(TAG, "<< 와이퍼 스위치 실패: ${it.message}") })
        wiper.registerWiperState(front, object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) { Log.i(TAG, "<< 와이퍼 변경 $state") }
            override fun onFailed(exception: Exception) { Log.e(TAG, "<< 와이퍼 구독 실패: ${exception.message}") }
        })
    }

    private fun checkControl(context: Context) {
        // 차량 제어(Vehicle Control): 제어 권한 없이 호출해 어떤 응답이 오는지만 본다
        vehicle(context).getWiper().setWiperSwitch(
            front, WiperSwitch.CONTINUOUS_LEVEL_1,
            { area, sw -> Log.i(TAG, "<< 와이퍼 제어 성공 $area $sw") },
            { Log.e(TAG, "<< 와이퍼 제어 실패: ${it::class.java.simpleName}: ${it.message}") },
        )
    }
}
