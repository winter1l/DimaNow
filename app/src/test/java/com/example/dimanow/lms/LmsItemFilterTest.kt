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
    fun readFilterKeepsHistoricalItemsAndSeparatesUnreadFromRead() {
        val all = listOf(unreadNotice, readMaterial)

        assertEquals(listOf("1주차 안내"), filterLmsItems(all, null, null, isRead = false).map { it.title })
        assertEquals(listOf("지난 수업 자료"), filterLmsItems(all, null, null, isRead = true).map { it.title })
        assertEquals(2, filterLmsItems(all, "audio", null, isRead = null).size)
    }
}
