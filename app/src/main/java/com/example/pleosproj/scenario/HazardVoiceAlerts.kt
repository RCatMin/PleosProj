package com.example.pleosproj.scenario

import android.util.Log
import com.example.pleosproj.location.LocationFix
import com.example.pleosproj.location.LocationListener
import com.example.pleosproj.location.LocationSource
import com.example.pleosproj.location.ProximityChange
import com.example.pleosproj.ui.state.HazardKind
import com.example.pleosproj.voice.VoiceOutput

/**
 * 실제 내 위치(FusedLocation 또는 대체 구현)가 위험 지점 반경에 들어가면 음성으로 경고한다.
 * 시나리오 시계와 상관없이 위치만 보고 울리므로, GPS 경로 재생 스크립트로 위치를 옮기면 진짜로 울린다 (ADR 0005).
 */
class HazardVoiceAlerts(
    private val location: LocationSource,
    private val voice: VoiceOutput?,
    private val hazards: List<ScenarioHazard>,
    private val radiusMeters: Float = 300f,
    /** 화면 알림 등 다른 곳에 넘길 때 */
    private val onChange: (ScenarioHazard, ProximityChange) -> Unit = { _, _ -> },
    private val onLocation: (LocationFix) -> Unit = {},
) {
    private val byId = hazards.associateBy { it.id }

    private val listener = object : LocationListener {
        override fun onLocation(fix: LocationFix) = onLocation.invoke(fix)

        override fun onProximity(id: String, change: ProximityChange) {
            val h = byId[id] ?: return
            Log.i(TAG, "${h.title} $change (${location.name})")
            if (change == ProximityChange.ENTER) voice?.speak(message(h))
            onChange(h, change)
        }

        override fun onFailure(error: Throwable) {
            Log.e(TAG, "위치를 받을 수 없음: ${error.message}")
        }
    }

    fun start() {
        hazards.forEach { location.addProximity(it.id, it.position.lat, it.position.lng, radiusMeters) }
        location.start(listener)
    }

    fun stop() {
        hazards.forEach { location.removeProximity(it.id) }
        location.stop()
    }

    companion object {
        private const val TAG = "HazardAlerts"

        /** 반경에 들어섰을 때 읽어 줄 문장 */
        fun message(h: ScenarioHazard): String = when (h.kind) {
            HazardKind.FLOOD -> "곧 ${h.title}입니다. 침수 위험이 있으니 속도를 줄이세요."
            HazardKind.CLOSURE -> "곧 ${h.title}입니다. 도로가 통제되어 있습니다."
            HazardKind.ICE -> "곧 ${h.title}입니다. 결빙 주의 구간입니다."
            HazardKind.HEAVY_RAIN -> "곧 ${h.title}입니다. 물이 고여 있을 수 있으니 천천히 지나가세요."
            HazardKind.HEAVY_SNOW -> "곧 ${h.title}입니다. 눈길 주의 구간입니다."
            HazardKind.FOG -> "곧 ${h.title}입니다. 안개로 앞이 잘 보이지 않습니다."
            HazardKind.ACCIDENT -> "곧 ${h.title}입니다. 사고 구간입니다."
            HazardKind.CONSTRUCTION -> "곧 ${h.title}입니다. 공사 구간입니다."
        }
    }
}
