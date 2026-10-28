package com.example.dimanow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatedKeyedColumnTest {
    @Test
    fun `a removed row keeps its place while it folds away`() {
        assertEquals(listOf("a", "b", "c"), keyedRowOrder(shown = listOf("a", "b", "c"), current = listOf("a", "c")))
        assertEquals(listOf("a", "b", "c"), keyedRowOrder(shown = listOf("a", "b", "c"), current = listOf("b", "c")))
        assertEquals(listOf("a", "b", "c"), keyedRowOrder(shown = listOf("a", "b", "c"), current = listOf("a", "b")))
        // The last row leaving leaves nothing current; it still shows until its fold ends.
        assertEquals(listOf("a"), keyedRowOrder(shown = listOf("a"), current = emptyList()))
    }

    @Test
    fun `new and returning rows take their place in the current order`() {
        // 되돌리기 brings a date back between its neighbours.
        assertEquals(listOf("a", "b", "c"), keyedRowOrder(shown = listOf("a", "c"), current = listOf("a", "b", "c")))
        // A row that is still leaving stays after the row it followed, before a row added in the same change.
        assertEquals(listOf("a", "b", "d", "c"), keyedRowOrder(shown = listOf("a", "b", "c"), current = listOf("a", "d", "c")))
        assertEquals(listOf("x", "a"), keyedRowOrder(shown = emptyList(), current = listOf("x", "a")))
    }
}
