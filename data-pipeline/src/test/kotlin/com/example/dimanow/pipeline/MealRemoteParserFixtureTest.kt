package com.example.dimanow.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealRemoteParserFixtureTest {
    @Test
    fun `official discovery shape preserves post source and hours`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/meal_discovery_official_shape.html")).readText()

        val post = MealDiscoveryParser().parse(html)

        assertEquals("[DIMA 학생식당] 8월 4주차 식단 안내 운영시간: 11:00 ~ 14:00 장소: 학생회관 식당", post?.title)
        assertEquals("https://www.instagram.com/p/current/", post?.sourceUrl)
        assertEquals("11:00 ~ 14:00", post?.hours)
    }

    @Test
    fun `public profile feed is the fallback when homepage window rolls over`() {
        val payload = checkNotNull(javaClass.getResource("/fixtures/meal_profile_feed_official_shape.json")).readText()

        val post = MealProfileFeedParser().parse(payload)

        assertEquals("https://www.instagram.com/p/DcaCs3yE6So/", post?.sourceUrl)
        assertEquals("11:00 ~ 14:00", post?.hours)
    }

    @Test
    fun `meal-looking discovery links outside the exact Instagram post origin are rejected`() {
        val title = "[DIMA 학생식당] 9월 2주차 식단 안내 운영시간: 11:00 ~ 14:00"

        assertNull(MealDiscoveryParser().parse("""<a href="https://169.254.169.254/latest">$title</a>"""))
        assertNull(MealDiscoveryParser().parse("""<a href="https://www.instagram.com.evil.example/p/fixture/">$title</a>"""))
        assertNull(MealDiscoveryParser().parse("""<a href="https://user@www.instagram.com/p/fixture/">$title</a>"""))
        assertNull(MealDiscoveryParser().parse("""<a href="https://www.instagram.com:8443/p/fixture/">$title</a>"""))
        assertEquals(
            "https://www.instagram.com/p/fixture/",
            MealDiscoveryParser().parse("""<a href="https://www.instagram.com/p/fixture/">$title</a>""")?.sourceUrl,
        )
    }

    @Test
    fun `carousel meal table is the slide after the cover`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/instagram_carousel_serverjs_shape.html")).readText()

        val carousel = InstagramCarouselParser().parse(html)

        assertEquals(
            listOf("https://example.invalid/cover-live.jpg", "https://example.invalid/meal-live.jpg"),
            carousel.imageUrls,
        )
        assertEquals("https://example.invalid/meal-live.jpg", carousel.mealTableImageUrl)
    }
}
