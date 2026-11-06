package com.example.pleosproj.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pleosproj.navi.NaviProbe

/**
 * 디버그 빌드에서만 들어가는 확인용 수신기 (PLE-12).
 * 예: adb shell am broadcast --user current -a com.example.pleosproj.NAVI_PROBE --es cmd init
 */
class NaviProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val cmd = intent.getStringExtra("cmd") ?: return) {
            "init" -> NaviProbe.initialize(context)
            "release" -> NaviProbe.release()
            in setOf("tts_check", "stt_check", "vehicle_check", "control_check") ->
                SdkStatusProbe.run(context, cmd, intent.getStringExtra("mode"))
            in setOf("vehicle_watch", "vehicle_wiper", "vehicle_stop") -> VehicleWatchProbe.run(context, cmd)
            in RoutingProbe.commands ->
                RoutingProbe.run(intent.getStringExtra("from"), intent.getStringExtra("to"), intent.getStringExtra("name"))
            in DemoVoiceProbe.commands -> DemoVoiceProbe.run(context, cmd, intent.getStringExtra("speed"))
            in LiveProbe.commands -> LiveProbe.run(context, cmd, intent.getStringExtra("rain"), intent.getStringExtra("voice"))
            in LocationVoiceProbe.commands ->
                LocationVoiceProbe.run(context, cmd, intent.getStringExtra("mode"), intent.getStringExtra("text"))
            else -> NaviProbe.run(cmd, intent.getStringExtra("keyword"))
        }
    }
}
