package com.example.pleosproj.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class KmaGridTest {

    // 기대값은 기상청 단기예보 활용가이드의 격자 위경도 목록 값이다
    @Test
    fun 서울_종로구는_60_127() {
        assertEquals(KmaGridPoint(60, 127), KmaGrid.fromLatLng(37.5735, 126.9790))
    }

    @Test
    fun 서울_강남구는_61_126() {
        assertEquals(KmaGridPoint(61, 126), KmaGrid.fromLatLng(37.5172, 127.0473))
    }

    @Test
    fun 성남_수정구는_63_124() {
        assertEquals(KmaGridPoint(63, 124), KmaGrid.fromLatLng(37.4501, 127.1456))
    }
}
