package com.example.dimanow.meal

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramCarouselParserTest {
    @Test
    fun `meal table slide is the image after the cover`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/instagram_carousel_official_shape.html")).readText()

        val result = InstagramCarouselParser().parse(html)

        assertEquals(listOf("https://example.invalid/cover.jpg", "https://example.invalid/meal-table.jpg"), result.imageUrls)
        assertEquals("https://example.invalid/meal-table.jpg", result.mealTableImageUrl)
    }

    @Test
    fun `parses current ServerJS wrapped and escaped carousel payload`() {
        val html = checkNotNull(javaClass.getResource("/fixtures/instagram_carousel_serverjs_shape.html")).readText()

        val result = InstagramCarouselParser().parse(html)

        assertEquals(
            listOf("https://example.invalid/cover-live.jpg", "https://example.invalid/meal-live.jpg"),
            result.imageUrls,
        )
    }
}
