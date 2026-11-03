package com.example.pleosproj.app

import android.content.Context
import android.util.Log
import com.example.pleosproj.vehicle.VehicleSignalReader

/** 디버그 전용 (PLE-9): 차량 신호가 바뀔 때마다 로그로 남긴다. `adb logcat -s VehicleWatch` */
object VehicleWatchProbe {
    private var reader: VehicleSignalReader? = null

    fun run(context: Context, cmd: String) {
        when (cmd) {
            "vehicle_watch" -> {
                if (reader != null) return Log.i("VehicleWatch", "이미 보는 중").let { }
                reader = VehicleSignalReader(context) { Log.i("VehicleWatch", "<< $it (정차=${it.parked})") }.also { it.start() }
                Log.i("VehicleWatch", ">> 차량 신호 보기 시작")
            }
            "vehicle_wiper" -> reader?.readWiperSwitch()
            "vehicle_stop" -> { reader?.stop(); reader = null; Log.i("VehicleWatch", ">> 중지") }
        }
    }
}
