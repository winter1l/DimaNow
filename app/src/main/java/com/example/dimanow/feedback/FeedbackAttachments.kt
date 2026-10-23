package com.example.dimanow.feedback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A screenshot ready to send: re-encoded as JPEG, so no EXIF (location, device) survives (D-120). */
class FeedbackScreenshot(val bytes: ByteArray, val width: Int, val height: Int)

const val FEEDBACK_MAX_SCREENSHOTS = 3
private const val SCREENSHOT_LONG_SIDE = 1600
private const val SCREENSHOT_MAX_BYTES = 1_900_000
private const val LOG_MAX_CHARS = 15_000

/**
 * Reads a picked image, scales its long side down to 1600 px and encodes it as JPEG without metadata. Null when the
 * image cannot be read or stays too large.
 */
suspend fun prepareFeedbackScreenshot(context: Context, uri: Uri): FeedbackScreenshot? = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val long = maxOf(info.size.width, info.size.height)
            if (long > SCREENSHOT_LONG_SIDE) {
                val scale = SCREENSHOT_LONG_SIDE.toFloat() / long
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 15
        } while (bytes.size > SCREENSHOT_MAX_BYTES && quality >= 40)
        FeedbackScreenshot(bytes, bitmap.width, bitmap.height).takeIf { bytes.size <= SCREENSHOT_MAX_BYTES }
            .also { bitmap.recycle() }
    }.getOrNull()
}

/**
 * This app's own recent warnings and errors (Android only lets an app read its own log), with personal values
 * masked by [redactFeedbackLog]. The student sees exactly this text before sending.
 */
suspend fun collectFeedbackLog(): String = withContext(Dispatchers.IO) {
    val raw = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "400", "*:W").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }.getOrDefault("")
    redactFeedbackLog(raw).ifBlank { "최근 경고·오류 기록이 없어요." }
}

private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
private val URL = Regex("""(https?://[A-Za-z0-9.-]+)(?::\d+)?[^\s"'<>)]*""")
private val IPV4 = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")
private val IPV6 = Regex("""\b(?:[0-9A-Fa-f]{1,4}:){3,7}[0-9A-Fa-f]{1,4}\b""")
private val PHONE = Regex("""\b01[016789]-?\d{3,4}-?\d{4}\b""")
private val LONG_NUMBER = Regex("""\b\d{6,}\b""")
private val TOKEN = Regex("""[A-Za-z0-9+/_=-]{24,}""")
private val KEY_VALUE = Regex("""(?i)\b(password|passwd|pw|token|secret|cookie|session|auth[a-z]*|id|user(name)?|학번|이름)\s*[:=]\s*\S+""")

/**
 * Masks what could name a person or open an account in a log: e-mail addresses, phone numbers, long digit runs
 * (student numbers), IP addresses, URL paths and queries (the host stays), long tokens, and `key=value` pairs for
 * passwords, ids, cookies and the like. Keeps the last [LOG_MAX_CHARS] characters. Pure, so it is tested directly.
 */
fun redactFeedbackLog(text: String): String = text
    .replace(KEY_VALUE) { "${it.groupValues[1]}=<가림>" }
    .replace(EMAIL, "<이메일>")
    .replace(URL) { "${it.groupValues[1]}/…" }
    .replace(IPV6, "<IP>")
    .replace(IPV4, "<IP>")
    .replace(PHONE, "<전화번호>")
    .replace(TOKEN, "<토큰>")
    .replace(LONG_NUMBER, "<숫자>")
    .trim()
    .takeLast(LOG_MAX_CHARS)
