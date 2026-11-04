package com.example.pleosproj.location

import android.content.Context
import android.util.Log

/**
 * 위치 구현을 고른다 (ADR 0006). Pleos FusedLocation을 먼저 쓰고,
 * 지원하지 않거나 초기화·동작 중 실패하면 안드로이드 LocationManager로 바꾼다.
 * 바뀌어도 등록해 둔 반경 알림과 리스너는 그대로 옮겨 간다.
 */
class Locations(
    private val context: Context,
    /** 확인용: false면 처음부터 안드로이드 구현을 쓴다 */
    private val preferPleos: Boolean = true,
) : LocationSource {

    private var current: LocationSource = pick()
    private var listener: LocationListener? = null
    private val zones = linkedMapOf<String, Triple<Double, Double, Float>>()

    override val name get() = current.name

    private fun pick(): LocationSource {
        if (preferPleos) {
            runCatching { return PleosLocationSource(context).prepare() }
                .onFailure { Log.w(TAG, "Pleos FusedLocation 사용 불가, 안드로이드 기본 위치로 바꿈: ${it.message}") }
        }
        return AndroidLocationSource(context)
    }

    private val relay = object : LocationListener {
        override fun onLocation(fix: LocationFix) { listener?.onLocation(fix) }
        override fun onProximity(id: String, change: ProximityChange) { listener?.onProximity(id, change) }
        override fun onFailure(error: Throwable) {
            if (current is PleosLocationSource) fallBack(error) else listener?.onFailure(error)
        }
    }

    private fun fallBack(error: Throwable) {
        Log.w(TAG, "${current.name} 실패, 안드로이드 기본 위치로 바꿈: ${error.message}")
        current.stop()
        (current as? PleosLocationSource)?.release()
        current = AndroidLocationSource(context)
        zones.forEach { (id, z) -> current.addProximity(id, z.first, z.second, z.third) }
        Log.i(TAG, "위치 구현: ${current.name}")
        if (listener != null) current.start(relay)
    }

    override fun start(listener: LocationListener) {
        this.listener = listener
        Log.i(TAG, "위치 구현: ${current.name}")
        current.start(relay)
    }

    override fun stop() {
        current.stop()
        listener = null
    }

    override fun addProximity(id: String, lat: Double, lng: Double, radiusMeters: Float) {
        zones[id] = Triple(lat, lng, radiusMeters)
        current.addProximity(id, lat, lng, radiusMeters)
    }

    override fun removeProximity(id: String) {
        zones.remove(id)
        current.removeProximity(id)
    }

    fun release() {
        stop()
        (current as? PleosLocationSource)?.release()
    }

    private companion object {
        const val TAG = "Locations"
    }
}
