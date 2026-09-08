package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Test

class LmsItemFilterTest {
    private val unreadNotice = LmsItem(
        id = "notice-1",
        courseId = "audio",
        courseName = "음향기초실습",
        kind = LmsItemKind.NOTICE,
        title = "1주차 안내",
        detailUrl = "https://lms.dima.ac.kr/notice/1",
        isRead = false,
    )
    private val readMaterial = LmsItem(
        id = "material-1",
        courseId = "audio",
        courseName = "음향기초실습",
        kind = LmsItemKind.MATERIAL,
        title = "지난 수업 자료",
        detailUrl = "https://lms.dima.ac.kr/material/1",
        isRead = true,
    )

    @Test
    fun courseAndKindNarrowTheListWhileReadStateNeverHidesAnything() {
        val all = listOf(unreadNotice, readMaterial)

        // D-058: 읽음/안읽음 필터는 제거됐다. 읽은 항목도 목록에서 사라지지 않는다.
        assertEquals(2, filterLmsItems(all, "audio", null).size)
        assertEquals(listOf("1주차 안내"), filterLmsItems(all, null, LmsItemKind.NOTICE).map { it.title })
        assertEquals(listOf("지난 수업 자료"), filterLmsItems(all, "audio", LmsItemKind.MATERIAL).map { it.title })
        assertEquals(emptyList<String>(), filterLmsItems(all, "video", null).map { it.title })
    }
}
