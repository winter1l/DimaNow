package com.example.dimanow.pipeline

object TesseractTsvParser {
    fun parse(tsv: String): List<OcrLine> = tsv.lineSequence()
        .drop(1)
        .mapNotNull { raw ->
            val columns = raw.split('\t', limit = 12)
            if (columns.size != 12 || columns[0] != "5" || columns[11].isBlank()) return@mapNotNull null
            Word(
                key = columns.slice(1..4).joinToString("|"),
                text = columns[11].trim(),
                left = columns[6].toInt(),
                top = columns[7].toInt(),
                right = columns[6].toInt() + columns[8].toInt(),
                bottom = columns[7].toInt() + columns[9].toInt(),
            )
        }
        .groupBy(Word::key)
        .values
        .flatMap { words -> splitDistantColumns(words) }
        .map { words ->
            OcrLine(
                text = words.joinToString(" ") { it.text },
                left = words.minOf { it.left },
                top = words.minOf { it.top },
                right = words.maxOf { it.right },
                bottom = words.maxOf { it.bottom },
            )
        }
        .sortedWith(compareBy(OcrLine::top, OcrLine::left))

    private fun splitDistantColumns(words: List<Word>): List<List<Word>> {
        val sorted = words.sortedBy(Word::left)
        if (sorted.size < 2) return listOf(sorted)
        val gapThreshold = maxOf(24, sorted.maxOf { it.bottom - it.top } * 2)
        val groups = mutableListOf<MutableList<Word>>()
        sorted.forEach { word ->
            val current = groups.lastOrNull()
            if (current == null || word.left - current.last().right > gapThreshold) {
                groups += mutableListOf(word)
            } else {
                current += word
            }
        }
        return groups
    }

    private data class Word(
        val key: String,
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
