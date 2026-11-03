package com.example.pleosproj.weather

import java.time.LocalDateTime

/** 강수 형태 (기상청 PTY 코드). */
enum class PrecipType { NONE, RAIN, RAIN_SNOW, SNOW, SHOWER, DRIZZLE, DRIZZLE_SNOW, SNOW_FLURRY;
    val hasSnow get() = this == RAIN_SNOW || this == SNOW || this == DRIZZLE_SNOW || this == SNOW_FLURRY
}

/** 초단기실황: 지금 이 격자의 관측값. */
data class WeatherNow(
    val grid: KmaGridPoint,
    val observedAt: LocalDateTime,
    val temperatureC: Double,
    /** 1시간 강수량(mm) */
    val rainMm: Double,
    val precip: PrecipType,
    val humidity: Int,
    val windSpeed: Double,
)

/** 초단기예보: 앞으로 6시간, 한 시간 단위 예보 한 칸. */
data class WeatherForecast(
    val grid: KmaGridPoint,
    val time: LocalDateTime,
    val temperatureC: Double,
    val rainMm: Double,
    val precip: PrecipType,
    /** 하늘 상태 1 맑음, 3 구름많음, 4 흐림 */
    val sky: Int,
    /** 낙뢰(kA). 0이면 없음 */
    val lightning: Double,
)

/** 기상특보 목록의 한 줄. 제목 문자열에서 종류와 발표/해제를 읽어 낸다. */
data class WeatherWarning(
    val issuedAt: LocalDateTime,
    val title: String,
    /** 예: "호우", "대설", "풍랑". 읽지 못하면 null */
    val kind: String?,
    /** 주의보 또는 경보 */
    val level: String?,
    /** 발표면 true, 해제면 false */
    val active: Boolean,
)
