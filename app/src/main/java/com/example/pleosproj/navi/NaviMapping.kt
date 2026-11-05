package com.example.pleosproj.navi

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.TrafficEventType
import com.example.pleosproj.ui.state.HazardKind
import ai.pleos.playground.navi.constants.TurnType
import com.example.pleosproj.ui.state.TurnKind

/** NaviHelper 상수를 앱 쪽 값으로 바꾼다 */
internal object NaviMapping {

    fun turnKind(t: TurnType): TurnKind = when (t) {
        TurnType.TURN_STRAIGHT -> TurnKind.STRAIGHT
        TurnType.TURN_RIGHT -> TurnKind.RIGHT
        TurnType.TURN_LEFT -> TurnKind.LEFT
        TurnType.TURN_KEEP_R -> TurnKind.KEEP_RIGHT
        TurnType.TURN_KEEP_L -> TurnKind.KEEP_LEFT
        TurnType.TURN_SHARP_R -> TurnKind.SHARP_RIGHT
        TurnType.TURN_SHARP_L -> TurnKind.SHARP_LEFT
        TurnType.TURN_UTURN -> TurnKind.U_TURN
        TurnType.TURN_ENTER_IC_R, TurnType.TURN_ENTER_IC_L, TurnType.TURN_ENTER_IC_C -> TurnKind.ENTER_HIGHWAY
        TurnType.TURN_EXIT_IC_R, TurnType.TURN_EXIT_IC_L, TurnType.TURN_EXIT_IC_C -> TurnKind.EXIT_HIGHWAY
        TurnType.TURN_TOLL -> TurnKind.TOLLGATE
        TurnType.TURN_TUNNEL -> TurnKind.TUNNEL
        TurnType.TURN_ARRIVE -> TurnKind.ARRIVE
        // 지하차도·고가 진입/회피는 방향만 본다
        else -> when {
            t.name.endsWith("_R") -> TurnKind.KEEP_RIGHT
            t.name.endsWith("_L") -> TurnKind.KEEP_LEFT
            t.name.endsWith("_C") -> TurnKind.STRAIGHT
            else -> TurnKind.UNKNOWN
        }
    }

    fun entersUnderpass(t: TurnType) = t.name.startsWith("TURN_ENTER_UNDER")

    fun heavy(t: TrafficCongestionType) = t == TrafficCongestionType.HEAVY

    fun cautionKind(t: CautionPointType): CautionKind? = when (t) {
        CautionPointType.SLIPPERY_ROAD_ALERT -> CautionKind.SLIPPERY_ROAD
        CautionPointType.FOG_AREA -> CautionKind.FOG
        CautionPointType.ACCIDENT_PRONE_AREA -> CautionKind.ACCIDENT_PRONE
        CautionPointType.ROAD_DAMAGE_ALERT -> CautionKind.ROAD_DAMAGE
        CautionPointType.HIGHWAY_CONSTRUCTION -> CautionKind.CONSTRUCTION
        CautionPointType.FALLING_ROCK_AREA -> CautionKind.FALLING_ROCK
        CautionPointType.DOWNHILL_CAUTION_AREA -> CautionKind.DOWNHILL
        // 단속 카메라·과속 방지턱 등은 날씨 위험과 관계없어 뺀다
        else -> null
    }

    fun eventKind(t: TrafficEventType, description: String): HazardKind = when {
        isClosure(description) -> HazardKind.CLOSURE
        t == TrafficEventType.ACCIDENT -> HazardKind.ACCIDENT
        t == TrafficEventType.REPAIRING || t == TrafficEventType.ROAD_DAMAGE -> HazardKind.CONSTRUCTION
        t == TrafficEventType.DISASTER -> if (description.contains("눈") || description.contains("결빙")) HazardKind.ICE else HazardKind.FLOOD
        else -> HazardKind.CONSTRUCTION
    }

    fun isClosure(description: String) = description.contains("통제") || description.contains("차단")
}
