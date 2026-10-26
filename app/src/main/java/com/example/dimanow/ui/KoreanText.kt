package com.example.dimanow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * D-134: a Korean sentence that wraps at spaces, not between any two syllables (`확인할 수 있어/요`, `게이트/에 대요`).
 * Compose 1.12 offers only phrase word breaks, which did not change Korean wrapping on the API 36 emulator even with a
 * Korean locale, so each word's characters are joined with U+2060 WORD JOINER before layout ([keepKoreanWords]).
 * Screen readers and tests get the sentence as written. Use it for sentences that wrap: list supporting lines, guide
 * lines, notices and state messages.
 */
@Composable
internal fun KoreanText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val shown = remember(text) { keepKoreanWords(text) }
    if (shown == text) {
        Text(text, modifier = modifier, style = style, color = color, textAlign = textAlign)
    } else {
        Box(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
            Text(shown, style = style, color = color, textAlign = textAlign)
        }
    }
}

/**
 * [text] with a WORD JOINER between neighbouring characters of a word that has Hangul in it, so lines break only at
 * spaces and after `·`, `/` or `~` (`세탁실·휴게실`). A word longer than the line still breaks, as the platform breaks
 * any run that does not fit. Text without Hangul comes back unchanged.
 */
internal fun keepKoreanWords(text: String): String {
    if (text.none(::isHangul)) return text
    val out = StringBuilder(text.length * 2)
    text.forEachIndexed { index, char ->
        if (index > 0) {
            val previous = text[index - 1]
            val insideWord = !previous.isWhitespace() && !char.isWhitespace() && previous !in BREAK_AFTER
            if (insideWord && (isHangul(previous) || isHangul(char))) out.append(WORD_JOINER)
        }
        out.append(char)
    }
    return out.toString()
}

internal const val WORD_JOINER = '⁠'
private val BREAK_AFTER = setOf('·', '/', '~')

private fun isHangul(char: Char): Boolean =
    char in '가'..'힣' || char in 'ᄀ'..'ᇿ' || char in '㄰'..'㆏'
