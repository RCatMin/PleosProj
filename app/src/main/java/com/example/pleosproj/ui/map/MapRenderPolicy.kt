package com.example.pleosproj.ui.map

internal object MapRenderPolicy {
    const val MAX_FPS = 30
    const val BUILDINGS_MIN_ZOOM = 15.0

    fun showBuildings(guidance: Boolean, driverView: Boolean, zoom: Double, gesturing: Boolean) =
        guidance && driverView && zoom >= BUILDINGS_MIN_ZOOM && !gesturing
}

// 늦어진 프레임을 몰아서 처리하지 않고 현재 화면 시각의 위치만 그린다.
internal class MapFramePacer {
    private val intervalNanos = 1_000_000_000L / MapRenderPolicy.MAX_FPS
    private var nextFrameNanos: Long? = null

    fun shouldRender(nowNanos: Long): Boolean {
        val next = nextFrameNanos
        // VSync 시각이 1ms 이내로 앞당겨져도 두 프레임 간격을 유지한다.
        if (next != null && nowNanos < next - 1_000_000L) return false
        // 기한을 누적해 60Hz 화면의 작은 시각 오차로 갱신이 20fps까지 떨어지지 않게 한다.
        nextFrameNanos = if (next == null) nowNanos + intervalNanos
            else nowNanos + intervalNanos - (nowNanos - next) % intervalNanos
        return true
    }
}
