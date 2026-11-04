package com.example.pleosproj.location

/** 한 번 받은 내 위치. heading은 모르면 null. */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val heading: Float? = null,
    val timeMillis: Long = System.currentTimeMillis(),
)

/** 위험 지점 반경에 들어가거나 나간 일 */
enum class ProximityChange { ENTER, EXIT }

interface LocationListener {
    fun onLocation(fix: LocationFix)
    fun onProximity(id: String, change: ProximityChange) {}
    /** 구현이 더 동작할 수 없을 때. 자동 선택기는 이걸 받고 대체 구현으로 바꾼다 */
    fun onFailure(error: Throwable) {}
}

/**
 * 내 위치와 위험 지점 반경 알림 (ADR 0006).
 * Pleos FusedLocation을 먼저 쓰고, 안 되면 안드로이드 LocationManager로 바꾼다. 고르는 건 [Locations].
 */
interface LocationSource {
    /** 로그·화면에 남길 구현 이름 */
    val name: String

    fun start(listener: LocationListener)
    fun stop()

    /** [id] 지점 반경 [radiusMeters] 안으로 들어가거나 나가면 [LocationListener.onProximity]로 알린다 */
    fun addProximity(id: String, lat: Double, lng: Double, radiusMeters: Float)
    fun removeProximity(id: String)
}
