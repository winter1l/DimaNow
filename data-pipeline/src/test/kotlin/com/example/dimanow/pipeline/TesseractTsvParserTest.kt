package com.example.dimanow.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TesseractTsvParserTest {
    @Test
    fun `같은 OCR 줄의 단어와 경계를 합친다`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            5	1	1	1	1	1	100	20	30	15	95.0	8.24
            5	1	1	1	1	2	135	20	40	15	93.0	(월)
        """.trimIndent()

        val line = TesseractTsvParser.parse(tsv).single()

        assertEquals("8.24 (월)", line.text)
        assertEquals(100, line.left)
        assertEquals(175, line.right)
        assertEquals(20, line.top)
        assertEquals(35, line.bottom)
    }

    @Test
    fun `한 OCR 줄에 잡힌 표의 먼 요일 칸은 별도 줄로 나눈다`() {
        val tsv = """
            level	page_num	block_num	par_num	line_num	word_num	left	top	width	height	conf	text
            5	1	1	1	1	1	100	20	45	15	95.0	8.24
            5	1	1	1	1	2	310	20	45	15	94.0	8.25
        """.trimIndent()

        val lines = TesseractTsvParser.parse(tsv)

        assertEquals(listOf("8.24", "8.25"), lines.map(OcrLine::text))
        assertEquals(listOf(100, 310), lines.map(OcrLine::left))
    }
}
