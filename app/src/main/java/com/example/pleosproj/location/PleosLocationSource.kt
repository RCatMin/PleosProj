package com.example.pleosproj.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log

/** Pleos FusedLocation 구현. 반경 알림도 SDK의 addProximityAlert를 그대로 쓴다. */
class PleosLocationSource(context: Context) : LocationSource {

    override val name = "Pleos FusedLocation"

    private val fused = FusedLocation(context.applicationContext)
    private var listener: LocationListener? = null

    /** SDK가 돌려준 알림 ID → 우리 지점 ID */
    private val alertIds = mutableMapOf<String, String>()

    /** SDK가 아직 등록을 확인해 주지 않은 지점은 거리 계산으로 직접 감시한다 */
    private val pending = ProximityTracker()

    /** 쓸 수 있는지 확인하고 초기화한다. 안 되면 예외를 던진다 */
    fun prepare(): PleosLocationSource {
        check(fused.isSupported()) { "FusedLocation을 지원하지 않는 기기" }
        fused.initialize()
        return this
    }

    private val callback = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            val l = listener ?: return
            l.onLocation(LocationFix(location.latitude, location.longitude, location.heading, location.timestampMillis))
            pending.update(location.latitude, location.longitude).forEach { (id, change) -> l.onProximity(id, change) }
        }

        override fun onError(exception: Exception) {
            Log.e(TAG, "FusedLocation 오류", exception)
            listener?.onFailure(exception)
        }
    }

    override fun start(listener: LocationListener) {
        this.listener = listener
        fused.registerFusedLocationCallback(callback)
        fused.getLastKnownLocation(callback)
    }

    override fun stop() {
        runCatching { fused.unregisterFusedLocationCallback(callback) }
        alertIds.keys.toList().forEach { runCatching { fused.removeProximityAlert(it) } }
        alertIds.clear()
        listener = null
    }

    override fun addProximity(id: String, lat: Double, lng: Double, radiusMeters: Float) {
        pending.add(id, lat, lng, radiusMeters)
        val request = ProximityAlertRequest(lat, lng, radiusMeters, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
        fused.addProximityAlert(request, object : ProximityAlertListener {
            override fun onRegistered(alertId: String) {
                Log.i(TAG, "반경 알림 등록: $id → $alertId")
                synchronized(alertIds) { alertIds[alertId] = id }
                pending.remove(id)
            }

            override fun onTransition(event: ProximityAlertEvent) {
                Log.i(TAG, "반경 알림: ${event.alertId} ${event.transition}")
                val ourId = synchronized(alertIds) { alertIds[event.alertId] } ?: return
                val change = if (event.transition == ProximityTransition.ENTER) ProximityChange.ENTER else ProximityChange.EXIT
                listener?.onProximity(ourId, change)
            }

            override fun onError(exception: Exception) {
                Log.e(TAG, "반경 알림 등록 실패: $id", exception)
            }
        })
    }

    override fun removeProximity(id: String) {
        pending.remove(id)
        val alertId = synchronized(alertIds) { alertIds.entries.firstOrNull { it.value == id }?.key } ?: return
        runCatching { fused.removeProximityAlert(alertId) }
        synchronized(alertIds) { alertIds.remove(alertId) }
    }

    fun release() = runCatching { fused.release() }

    private companion object {
        const val TAG = "Locations"
    }
}
