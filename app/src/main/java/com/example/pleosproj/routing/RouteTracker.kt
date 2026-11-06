package com.example.pleosproj.routing

import com.example.pleosproj.risk.LatLng
import com.example.pleosproj.risk.RouteGeometry

/** 위치를 경로에 맞춘 결과 */
data class Track(
    /** 출발점부터 경로선을 따라 온 거리(m) */
    val traveled: Double,
    /** 경로선에서 떨어진 거리(m) */
    val offMeters: Double,
    /** 경로를 벗어났다고 판단했는지 (재탐색할 때) */
    val offRoute: Boolean,
    val arrived: Boolean,
)

/**
 * 내 위치를 경로선에 맞춰 얼마나 왔는지, 경로를 벗어났는지, 도착했는지 판단한다.
 * GPS가 잠깐 튀는 것에 흔들리지 않게, [offRouteMeters]보다 멀리 [offRouteSeconds] 넘게 있어야 이탈로 본다.
 */
class RouteTracker(
    private val route: RoutePlan,
    private val offRouteMeters: Double = 50.0,
    private val offRouteSeconds: Double = 10.0,
    private val arriveMeters: Double = 30.0,
) {
    private val length = RouteGeometry.lengthMeters(route.line)
    private var traveled = 0.0
    private var offSince: Double? = null

    /** [seconds]는 단조 증가하는 시각(초) */
    fun update(position: LatLng, seconds: Double): Track {
        val (along, off) = RouteGeometry.project(route.line, position)
        if (off <= offRouteMeters) {
            offSince = null
            // 신호가 흔들려 조금 뒤로 붙는 것은 무시하고, 앞으로 간 만큼만 반영한다
            if (along > traveled - 30) traveled = maxOf(traveled, along)
        } else if (offSince == null) {
            offSince = seconds
        }
        val offRoute = offSince?.let { seconds - it >= offRouteSeconds } ?: false
        val nearEnd = RouteGeometry.distanceMeters(position, route.line.last()) <= arriveMeters
        return Track(traveled, off, offRoute, arrived = nearEnd || length - traveled <= arriveMeters)
    }
}
