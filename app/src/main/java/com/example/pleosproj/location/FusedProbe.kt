package com.example.pleosproj.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log

/**
 * PLE-12 확인용: Pleos FusedLocation이 에뮬레이터에서 쓸 수 있는지 로그로 확인한다.
 * `adb logcat -s FusedProbe`로 본다.
 */
object FusedProbe {

    private const val TAG = "FusedProbe"
    private var fused: FusedLocation? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = Log.i(TAG, "<< onChanged $location").let { }
        override fun onError(exception: Exception) = Log.e(TAG, "<< onError", exception).let { }
    }

    fun run(context: Context, command: String) {
        Log.i(TAG, ">> $command")
        runCatching {
            val f = fused ?: FusedLocation(context.applicationContext).also { fused = it }
            when (command) {
                "fused_check" -> Log.i(TAG, "isSupported=${f.isSupported()}")
                "fused_init" -> f.initialize().also { Log.i(TAG, "초기화 성공") }
                "fused_last" -> f.getLastKnownLocation(listener)
                "fused_start" -> f.registerFusedLocationCallback(listener)
                "fused_stop" -> f.unregisterFusedLocationCallback(listener)
                else -> Log.w(TAG, "모르는 명령: $command")
            }
        }.onFailure { Log.e(TAG, "'$command' 실패", it) }
    }
}
