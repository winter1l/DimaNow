package com.example.dimanow.meal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

class DormitoryMealImageReaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Optional local acceptance fixture: never bundle a user's photo in either APK. */
    @Test fun selectedHeicBecomesAnUploadableJpegWithoutLosingFullResolution() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("heicFixture") == "true")
        val source = File(context.filesDir, "heic-acceptance/input.heic")
        check(source.isFile) { "Stage the explicitly supplied local HEIC fixture first" }
        val image = DormitoryMealImageReader(context).read(Uri.fromFile(source))
        assertEquals("image/jpeg", image.mimeType)
        assertEquals("jpg", image.extension)
        assertEquals(0xff, image.bytes[0].toInt() and 0xff)
        assertEquals(0xd8, image.bytes[1].toInt() and 0xff)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
        // The supplied image's HEIF ispe metadata is 4000 x 3000 (12 MP).
        assertEquals(4000, bounds.outWidth)
        assertEquals(3000, bounds.outHeight)
        assertTrue(image.bytes.size in 1..15 * 1024 * 1024)
        File(source.parentFile, "converted.jpg").writeBytes(image.bytes)
        val original = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val converted = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        try {
            // Compare actual small-print pixels against the decoded source, not OCR guesses.
            val originalDetail = Bitmap.createBitmap(original, 800, 2460, 1800, 230)
            val convertedDetail = Bitmap.createBitmap(converted, 800, 2460, 1800, 230)
            try {
                File(source.parentFile, "source-detail.png").outputStream().use {
                    originalDetail.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                File(source.parentFile, "converted-detail.png").outputStream().use {
                    convertedDetail.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val before = IntArray(1800 * 230)
                val after = IntArray(before.size)
                originalDetail.getPixels(before, 0, 1800, 0, 0, 1800, 230)
                convertedDetail.getPixels(after, 0, 1800, 0, 0, 1800, 230)
                val averageError = before.indices.sumOf { index ->
                    kotlin.math.abs(Color.red(before[index]) - Color.red(after[index])) +
                        kotlin.math.abs(Color.green(before[index]) - Color.green(after[index])) +
                        kotlin.math.abs(Color.blue(before[index]) - Color.blue(after[index]))
                }.toDouble() / (before.size * 3)
                assertTrue("Small-print mean channel error: $averageError / 255", averageError < 3.0)
                File(source.parentFile, "result.txt").writeText(
                    "width=${bounds.outWidth}\nheight=${bounds.outHeight}\nbytes=${image.bytes.size}\nsmallPrintMeanError=$averageError\n",
                )
            } finally {
                originalDetail.recycle()
                convertedDetail.recycle()
            }
        } finally {
            original.recycle()
            converted.recycle()
        }
    }

    @Test fun existingFormatsKeepTheirOriginalBytesEvenWithMisleadingFileNames() = runBlocking {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            listOf(
                Triple(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg"),
                Triple(Bitmap.CompressFormat.PNG, "image/png", "png"),
                Triple(Bitmap.CompressFormat.WEBP_LOSSLESS, "image/webp", "webp"),
            ).forEach { (format, mime, extension) ->
                val file = File(context.cacheDir, "actual-$extension.heic")
                try {
                    file.outputStream().use { bitmap.compress(format, 100, it) }
                    val image = DormitoryMealImageReader(context).read(Uri.fromFile(file))
                    assertEquals(mime, image.mimeType)
                    assertEquals(extension, image.extension)
                    assertArrayEquals(file.readBytes(), image.bytes)
                } finally { file.delete() }
            }
        } finally { bitmap.recycle() }
    }

    @Test fun corruptHeicFailsBeforeAnUploadCanBePrepared() = runBlocking {
        val file = File(context.cacheDir, "corrupt.heic")
        try {
            file.writeBytes(byteArrayOf(0, 0, 0, 16) + "ftypheic".toByteArray() + ByteArray(4))
            try {
                DormitoryMealImageReader(context).read(Uri.fromFile(file))
                fail("Corrupt HEIC must not produce an upload image")
            } catch (failure: IllegalArgumentException) {
                assertTrue(failure.message.orEmpty().contains("HEIC 사진을 읽지 못했어요"))
            }
        } finally { file.delete() }
    }

    @Test fun oversizedInputIsRejectedBeforeDecoding() = runBlocking {
        val file = File(context.cacheDir, "oversized.heic")
        try {
            java.io.RandomAccessFile(file, "rw").use { it.setLength(15L * 1024 * 1024 + 1) }
            try {
                DormitoryMealImageReader(context).read(Uri.fromFile(file))
                fail("Oversized input must not produce an upload image")
            } catch (failure: IllegalArgumentException) {
                assertTrue(failure.message.orEmpty().contains("15MB"))
            }
        } finally { file.delete() }
    }

    @Test fun emptyInputHasAnActionableError() = runBlocking {
        val file = File(context.cacheDir, "empty.heic")
        try {
            file.writeBytes(byteArrayOf())
            try {
                DormitoryMealImageReader(context).read(Uri.fromFile(file))
                fail("Empty input must not produce an upload image")
            } catch (failure: IllegalArgumentException) {
                assertTrue(failure.message.orEmpty().contains("사진이 비어 있어요"))
            }
        } finally { file.delete() }
    }
}
