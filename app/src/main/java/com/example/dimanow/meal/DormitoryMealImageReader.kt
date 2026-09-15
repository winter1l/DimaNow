package com.example.dimanow.meal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Prepares a selected photo for the existing dormitory submission gateway. */
class DormitoryMealImageReader(private val context: Context) {
    suspend fun read(uri: Uri): DormitoryMealImage = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readWithLimit() }
            ?: error("식단 사진을 읽지 못했어요.")
        require(bytes.isNotEmpty()) { "사진이 비어 있어요. 다른 사진을 선택해 주세요." }
        ensureActive()
        // A provider's MIME/extension can be missing or incorrect. Inspect the actual file.
        when {
            bytes.startsWith(0xff, 0xd8, 0xff) -> DormitoryMealImage(bytes, "image/jpeg", "jpg")
            bytes.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ->
                DormitoryMealImage(bytes, "image/png", "png")
            bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" ->
                DormitoryMealImage(bytes, "image/webp", "webp")
            bytes.isHeif() -> {
                val result = convertHeif(bytes)
                ensureActive()
                result
            }
            else -> error("JPG, PNG, WebP, HEIC 사진을 선택해 주세요.")
        }
    }

    private fun convertHeif(bytes: ByteArray): DormitoryMealImage {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val pixels = info.size.width.toLong() * info.size.height
                require(pixels in 1..MAX_DECODE_PIXELS) {
                    "사진 해상도가 너무 높아요. 2,400만 화소 이하의 사진을 선택해 주세요."
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                // ImageDecoder applies encoded orientation. Keep full resolution and do not rotate again.
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            throw invalid
        } catch (failure: Exception) {
            throw IllegalArgumentException("HEIC 사진을 읽지 못했어요. 다른 사진을 선택해 주세요.", failure)
        }
        return try {
            val output = LimitedImageOutput()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "사진을 변환하지 못했어요." }
            DormitoryMealImage(output.toByteArray(), "image/jpeg", "jpg")
        } finally {
            bitmap.recycle()
        }
    }

    private fun InputStream.readWithLimit(): ByteArray {
        val output = LimitedImageOutput()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class LimitedImageOutput : OutputStream() {
        private val bytes = ByteArrayOutputStream(64 * 1024)
        override fun write(value: Int) {
            require(bytes.size() < MAX_IMAGE_BYTES) { "15MB 이하의 사진을 선택해 주세요." }
            bytes.write(value)
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            require(length <= MAX_IMAGE_BYTES - bytes.size()) { "15MB 이하의 사진을 선택해 주세요." }
            bytes.write(buffer, offset, length)
        }
        fun toByteArray(): ByteArray = bytes.toByteArray()
    }

    private fun ByteArray.startsWith(vararg signature: Int) = size >= signature.size &&
        signature.indices.all { (this[it].toInt() and 0xff) == signature[it] }

    private fun ByteArray.ascii(offset: Int, length: Int) = String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.isHeif(): Boolean {
        if (size < 16 || ascii(4, 4) != "ftyp") return false
        val boxLength = ByteBuffer.wrap(this, 0, 4).int.toLong() and 0xffffffffL
        if (boxLength < 16 || boxLength > size || boxLength > 4096 || boxLength % 4 != 0L) return false
        val brands = listOf(ascii(8, 4)) + (16 until boxLength.toInt() step 4).map { ascii(it, 4) }
        return brands.any { it in HEIF_BRANDS } && brands.none { it == "avif" || it == "avis" }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        const val MAX_DECODE_PIXELS = 24_000_000L
        val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")
    }
}
