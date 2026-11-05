package com.example.pleosproj.ui.permissions

internal enum class AppAccess(val title: String, val purpose: String, val permissions: List<String>) {
    SPEED("차량 속도", "차량의 주행 속도 읽기", listOf("android.car.permission.CAR_SPEED")),
    LOCATION("위치", "현재 위치와 위험 지점 확인", listOf(
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")),
    MICROPHONE("마이크", "음성 명령 입력", listOf("android.permission.RECORD_AUDIO"));

    val historyKey get() = "access-${name.lowercase(java.util.Locale.ROOT)}-requested"
}

internal enum class AccessAction { NONE, REQUEST, SETTINGS }

// 화면에 표시하는 권한 상태다. SDK 데이터와 공유하는 ui/state 모델은 변경하지 않는다.
internal data class AccessStatus(
    val access: AppAccess,
    val granted: Boolean,
    val requested: Boolean,
    val showRationale: Boolean,
    val approximateLocation: Boolean = false,
) {
    val action get() = when {
        granted -> AccessAction.NONE
        requested && !showRationale -> AccessAction.SETTINGS
        else -> AccessAction.REQUEST
    }
    val description get() = when {
        granted -> "허용됨"
        approximateLocation -> "대략적인 위치만 허용됨"
        else -> "허용되지 않음"
    }
}

// 업데이트 전 권한 거부는 존중하고, 새로 추가된 요청만 최초 안내에 포함한다.
internal fun initialAccessRequests(statuses: List<AccessStatus>): List<AppAccess> =
    statuses.filter { !it.granted && !it.requested }.map { it.access }
