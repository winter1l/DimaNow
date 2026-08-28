package com.example.dimanow.meal

import org.junit.Assert.assertEquals
import org.junit.Test

class MealDiscoveryParserTest {
    @Test
    fun `latest matching official cafeteria post retains source and hours`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/meal_discovery_official_shape.html")).readText()

        val post = MealDiscoveryParser().parse(html)

        assertEquals("[DIMA 학생식당] 8월 4주차 식단 안내 운영시간: 11:00 ~ 14:00 장소: 학생회관 식당", post?.title)
        assertEquals("https://www.instagram.com/p/current/", post?.sourceUrl)
        assertEquals("11:00 ~ 14:00", post?.hours)
    }

    @Test
    fun `official public profile feed discovers cafeteria post after homepage window rolls over`() {
        val json = checkNotNull(javaClass.getResource("/fixtures/meal_profile_feed_official_shape.json")).readText()

        val post = MealProfileFeedParser().parse(json)

        assertEquals("[DIMA 학생식당] 8월 4주차 식단 안내 운영시간: 11:00 ~ 14:00", post?.title)
        assertEquals("https://www.instagram.com/p/DcaCs3yE6So/", post?.sourceUrl)
        assertEquals("11:00 ~ 14:00", post?.hours)
    }
}
