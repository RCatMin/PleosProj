package com.example.pleosproj.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pleosproj.location.FusedProbe
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
            in setOf("fused_check", "fused_init", "fused_last", "fused_start", "fused_stop") -> FusedProbe.run(context, cmd)
            else -> NaviProbe.run(cmd, intent.getStringExtra("keyword"))
        }
    }
}
