package com.example.pleosproj.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper

/**
 * 안드로이드 LocationManager 구현. Pleos FusedLocation을 쓸 수 없을 때 쓴다.
 * 반경 알림은 [ProximityTracker]로 위치가 올 때마다 직접 계산한다.
 * ACCESS_FINE_LOCATION 권한이 없으면 [start]에서 onFailure로 알린다.
 */
class AndroidLocationSource(context: Context) : LocationSource {

    override val name = "Android LocationManager"

    private val manager = context.applicationContext.getSystemService(LocationManager::class.java)
    private val tracker = ProximityTracker()
    private var listener: LocationListener? = null

    private val callback = android.location.LocationListener { location -> deliver(location) }

    private fun deliver(location: Location) {
        val l = listener ?: return
        l.onLocation(
            LocationFix(location.latitude, location.longitude, if (location.hasBearing()) location.bearing else null, location.time),
        )
        tracker.update(location.latitude, location.longitude).forEach { (id, change) -> l.onProximity(id, change) }
    }

    @SuppressLint("MissingPermission")
    override fun start(listener: LocationListener) {
        this.listener = listener
        try {
            val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
            else LocationManager.FUSED_PROVIDER
            manager.getLastKnownLocation(provider)?.let(::deliver)
            manager.requestLocationUpdates(provider, 1000L, 0f, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            listener.onFailure(e)
        }
    }

    override fun stop() {
        manager.removeUpdates(callback)
        listener = null
    }

    override fun addProximity(id: String, lat: Double, lng: Double, radiusMeters: Float) =
        tracker.add(id, lat, lng, radiusMeters)

    override fun removeProximity(id: String) = tracker.remove(id)
}
