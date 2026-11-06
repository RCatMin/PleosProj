package com.example.pleosproj.ui.state

/** 목적지 후보 한 곳. 검색 결과와 최근 목적지가 이 모양으로 화면에 온다 */
data class Place(
    val id: String,
    val name: String,
    /** 도로명 주소가 있으면 도로명, 없으면 지번 */
    val address: String,
    val position: GeoPoint,
    /** 내 위치에서의 직선거리(m). 모르면 null */
    val distanceMeters: Int?,
    /** 예: "지하철역", "주차장". 모르면 빈 문자열 */
    val category: String = "",
)

/** 검색이 안 됐을 때 이유. 화면은 이 값으로 안내 문구와 재시도 여부를 정한다 */
enum class SearchFailure {
    /** 검색 키가 없다 (앱 설정 문제, 재시도해도 안 됨) */
    KEY_MISSING,
    /** 검색 서비스 사용 설정이 꺼져 있거나 키가 거절됐다 (재시도해도 안 됨) */
    NOT_ALLOWED,
    /** 하루 사용량을 넘었다 (나중에 다시) */
    LIMIT,
    /** 네트워크가 끊겼거나 서버가 답하지 않았다 (재시도) */
    NETWORK,
}
