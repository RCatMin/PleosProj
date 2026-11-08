package com.example.pleosproj.risk

import com.example.pleosproj.risk.RiskWeights as W
import kotlin.math.roundToLong

/** 위험도 계산 (ADR 0003). 상태가 없는 순수 계산이다. */
object RiskEngine {

    /** 한 구간의 위험도(0~1). 지날 수 없는 구간은 1이다. */
    fun segmentScore(c: SegmentConditions, vehicle: VehicleSignals = VehicleSignals()): Double {
        if (c.closed) return 1.0
        val base = W.WEATHER * weather(c) +
            W.WARNING * warning(c.warning) +
            W.VULNERABILITY * vulnerability(c, vehicle.bodyType) +
            W.ICING * icing(c, vehicle.outsideTempC)
        return (base * vehicleFactor(c, vehicle)).coerceIn(0.0, 1.0)
    }

    /** 경로 위험도: 구간 점수를 통과 시간으로 가중 평균한다. 위험한 구간에 오래 머물수록 커진다. */
    fun assess(route: RouteInput, vehicle: VehicleSignals = VehicleSignals()): RouteAssessment {
        val scores = route.segments.map { segmentScore(it.conditions, vehicle) }
        val duration = route.segments.sumOf { it.travelSeconds }
        val score = if (duration <= 0.0) 0.0
        else route.segments.zip(scores).sumOf { (s, score) -> score * s.travelSeconds } / duration
        return RouteAssessment(
            id = route.id,
            score = score,
            band = band(score),
            durationSeconds = duration,
            excluded = route.segments.any { it.conditions.closed },
            segmentScores = scores,
        )
    }

    /**
     * 추천 규칙
     * 1. 통제 구간이 있는 경로는 뺀다.
     * 2. 최단 경로(뺀 경로 포함)보다 20% 이상 오래 걸리지 않는 경로에서 점수가 가장 낮은 것을 고른다.
     *    그런 경로가 없으면 남은 경로 전체에서 고른다. 점수가 같으면 빠른 경로를 고른다.
     * 3. 남은 경로가 모두 0.6 이상이면 출발 연기를 권한다.
     */
    fun recommend(routes: List<RouteInput>, vehicle: VehicleSignals = VehicleSignals()): Recommendation {
        val assessments = routes.map { assess(it, vehicle) }
        val open = assessments.filterNot { it.excluded }
        if (open.isEmpty()) return Recommendation(assessments, recommendedId = null, suggestPostpone = true)

        val fastest = assessments.minOf { it.durationSeconds }
        val eligible = open.filter { it.durationSeconds <= fastest * W.MAX_DETOUR_RATIO }.ifEmpty { open }
        // 소수점 오차로 순서가 뒤바뀌지 않게 점수를 반올림해서 비교한다
        val best = eligible.minWith(compareBy({ (it.score * 1e6).roundToLong() }, { it.durationSeconds }))
        return Recommendation(
            assessments = assessments,
            recommendedId = best.id,
            suggestPostpone = open.all { it.score >= W.POSTPONE_SCORE },
        )
    }

    /**
     * 위험 지점 하나의 단계. 경로 점수와 같은 계산을 쓰되, 결빙 취약 지점이 영하(또는 젖은 채 2℃ 이하)면
     * 최소 "보통"으로 본다. 결빙은 가중치가 작아서 눈이 오지 않으면 구간 점수만으로는 "낮음"에 머물지만,
     * 그 지점을 지나는 운전자에게는 미리 알려야 하기 때문이다.
     */
    fun pointBand(c: SegmentConditions, vehicle: VehicleSignals = VehicleSignals()): RiskBand {
        val band = band(segmentScore(c, vehicle))
        return if (icing(c, vehicle.outsideTempC) >= 0.7 && band < RiskBand.MEDIUM) RiskBand.MEDIUM else band
    }

    fun band(score: Double): RiskBand = when {
        score >= W.BAND_HIGH -> RiskBand.HIGH
        score >= W.BAND_MEDIUM -> RiskBand.MEDIUM
        score >= W.BAND_LOW -> RiskBand.LOW
        else -> RiskBand.NONE
    }

    // --- 항목별 점수 (0~1) ---

    /** 비는 강수량에 비례, 눈은 내리기만 해도 0.6부터 */
    internal fun weather(c: SegmentConditions): Double {
        val rain = (c.rainMmPerHour / W.HEAVY_RAIN_MM).coerceIn(0.0, 1.0)
        val snow = if (c.snowing) 0.6 + 0.4 * rain else 0.0
        return maxOf(rain, snow)
    }

    internal fun warning(level: WarningLevel): Double = when (level) {
        WarningLevel.NONE -> 0.0
        WarningLevel.ADVISORY -> 0.6
        WarningLevel.WARNING -> 1.0
    }

    /** 침수 취약 지점은 비가 올 때만 위험하다. 차고가 낮은 세단과 배터리가 있는 전기차는 더 민감하게 본다. */
    internal fun vulnerability(c: SegmentConditions, body: BodyType): Double {
        val base = when (c.vulnerability) {
            Vulnerability.NONE -> return 0.0
            Vulnerability.UNDERPASS -> 1.0
            Vulnerability.RIVERSIDE -> 0.8
            Vulnerability.PAST_FLOOD -> 0.7
        }
        val rainFactor = (c.rainMmPerHour / 20.0).coerceIn(0.0, 1.0)
        val bodyFactor = when (body) {
            BodyType.SEDAN -> 1.0
            BodyType.SUV -> 0.8
            BodyType.EV -> 1.1
        }
        return (base * rainFactor * bodyFactor).coerceIn(0.0, 1.0)
    }

    /** 결빙 취약 구간에서 기온이 낮을수록. 차에서 읽은 외기온도가 있으면 그 값을 쓴다. */
    internal fun icing(c: SegmentConditions, outsideTempC: Double?): Double {
        if (!c.icingZone) return 0.0
        val temp = outsideTempC ?: c.temperatureC
        val wet = c.rainMmPerHour > 0.0 || c.snowing
        return when {
            temp <= 0.0 -> 1.0
            temp <= 2.0 -> if (wet) 0.7 else 0.4
            else -> 0.0
        }
    }

    /**
     * 차량 보정. 와이퍼로 실제 비의 세기를 짐작해 예보와 다르면 점수를 올리거나 내린다.
     * 와이퍼를 모르면 보정하지 않는다.
     */
    internal fun vehicleFactor(c: SegmentConditions, vehicle: VehicleSignals): Double {
        val forecast = c.rainMmPerHour
        val factor = when (vehicle.wiper) {
            null -> 1.0
            WiperLevel.HIGH -> if (forecast < 10.0) 1.5 else 1.2
            WiperLevel.LOW -> if (forecast < 3.0) 1.2 else 1.0
            WiperLevel.INTERMITTENT -> 1.0
            WiperLevel.OFF -> if (forecast >= 3.0) 0.8 else 1.0
        }
        return factor.coerceIn(W.VEHICLE_FACTOR_MIN, W.VEHICLE_FACTOR_MAX)
    }
}
