package com.example.pleosproj.weather

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** 기상청 동네예보 격자 좌표. 5km 간격이다. */
data class KmaGridPoint(val nx: Int, val ny: Int)

/**
 * 위경도를 기상청 격자(nx, ny)로 바꾼다.
 * 기상청 단기예보 API 활용가이드의 람베르트 정각원추도법 변환식을 그대로 옮겼다.
 */
object KmaGrid {
    private const val EARTH_RADIUS_KM = 6371.00877
    private const val GRID_KM = 5.0
    private const val STANDARD_LAT1 = 30.0
    private const val STANDARD_LAT2 = 60.0
    private const val ORIGIN_LON = 126.0
    private const val ORIGIN_LAT = 38.0
    private const val ORIGIN_X = 43.0
    private const val ORIGIN_Y = 136.0

    fun fromLatLng(lat: Double, lng: Double): KmaGridPoint {
        val rad = PI / 180.0
        val re = EARTH_RADIUS_KM / GRID_KM
        val slat1 = STANDARD_LAT1 * rad
        val slat2 = STANDARD_LAT2 * rad
        val olon = ORIGIN_LON * rad
        val olat = ORIGIN_LAT * rad

        val sn = ln(cos(slat1) / cos(slat2)) /
            ln(tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5))
        val sf = tan(PI * 0.25 + slat1 * 0.5).pow(sn) * cos(slat1) / sn
        val ro = re * sf / tan(PI * 0.25 + olat * 0.5).pow(sn)

        val ra = re * sf / tan(PI * 0.25 + lat * rad * 0.5).pow(sn)
        var theta = lng * rad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val x = floor(ra * sin(theta) + ORIGIN_X + 0.5).toInt()
        val y = floor(ro - ra * cos(theta) + ORIGIN_Y + 0.5).toInt()
        return KmaGridPoint(x, y)
    }
}
