package com.example.pleosproj.location

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry

/**
 * 반경 알림을 거리 계산으로 직접 처리한다. 안드로이드 기본 구현에서 쓴다.
 * 위치가 경계에서 흔들릴 때 들어감·나감이 반복되지 않게, 나갈 때는 반경보다 [exitMarginMeters] 더 멀어져야 한다.
 */
class ProximityTracker(private val exitMarginMeters: Double = 20.0) {

    private data class Zone(val center: LatLng, val radius: Double, var inside: Boolean = false)

    private val zones = linkedMapOf<String, Zone>()

    @Synchronized
    fun add(id: String, lat: Double, lng: Double, radiusMeters: Float) {
        zones[id] = Zone(LatLng(lat, lng), radiusMeters.toDouble())
    }

    @Synchronized
    fun remove(id: String) {
        zones.remove(id)
    }

    /** 새 위치를 넣고, 상태가 바뀐 지점만 돌려준다 */
    @Synchronized
    fun update(lat: Double, lng: Double): List<Pair<String, ProximityChange>> {
        val here = LatLng(lat, lng)
        return zones.mapNotNull { (id, z) ->
            val d = RouteGeometry.distanceMeters(here, z.center)
            when {
                !z.inside && d <= z.radius -> { z.inside = true; id to ProximityChange.ENTER }
                z.inside && d > z.radius + exitMarginMeters -> { z.inside = false; id to ProximityChange.EXIT }
                else -> null
            }
        }
    }
}
