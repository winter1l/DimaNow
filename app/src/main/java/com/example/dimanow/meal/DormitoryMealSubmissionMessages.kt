package com.example.dimanow.meal

/**
 * Friendly 해요체 wording for dormitory meal submission outcomes (D-094(14) voice).
 *
 * The server contract is unchanged: `REJECTED` still carries a short optional reason from the
 * pipeline or Gemini validation. Known pipeline reasons map to fixed text; any other reason is
 * reduced to one short sanitized line, and anything that looks like raw JSON or exception text
 * falls back to the generic retake guidance.
 */
internal object DormitoryMealSubmissionMessages {
    const val REJECTED_LEAD = "사진을 게시하지 못했어요."
    const val RETAKE = "식단표 전체가 보이게 다시 찍어 올려 주세요"
    const val DUPLICATE = "이미 이번 주 식단이 있어요"
    const val ERROR = "사진을 처리하지 못했어요. 잠시 후 다시 시도해 주세요"
    const val MAX_REASON_LENGTH = 60

    private val KNOWN_REASONS = mapOf(
        // GeminiDormitoryMealPayloadBuilder.DormitoryMealWeekMismatchException
        "이번 주 기숙사 식단표가 아니에요" to "이번 주 기숙사 식단표가 아니에요. 이번 주 식단표를 찍어 올려 주세요",
        // DormitoryMealSubmissionProcessor: payload failed validation
        "식단표의 날짜와 메뉴가 잘 보이도록 다시 촬영해 주세요" to "날짜와 메뉴를 읽지 못했어요. $RETAKE",
        // GeminiDormitoryMealClient: validation failed without a reason
        "기숙사 식단표 전체가 보이도록 다시 촬영해 주세요" to RETAKE,
    )

    private val RAW_TEXT_MARKERS = listOf(
        "{", "}", "[", "]", "<", ">", "\\", "://", "Exception", "Error", "error:", "null",
    )
    private val RETAKE_HINTS = listOf("촬영", "찍어", "찍으", "다시")

    const val UPLOAD_NETWORK = "식단 사진을 올리지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."

    /** Maps the upload gateway's own reason (rate limit, daily budget, duplicate week, file checks) to plain words. */
    fun uploadFailure(message: String?): String {
        val text = message.orEmpty()
        return when {
            "처리 한도" in text -> "오늘은 올라온 사진이 많아 잠시 쉬고 있어요. 내일 다시 올려 주세요."
            "잠시 후 다시 시도" in text && "올리지 못했" !in text -> "방금 올린 사진이 있어요. 10분 뒤에 다시 올려 주세요."
            "이미 등록" in text -> "이번 주 기숙사 식단이 이미 올라와 있어요."
            "15MB" in text -> "15MB 이하의 사진을 올려 주세요."
            "해상도" in text || "너무 작" in text -> "식단표가 잘 보이는 원본 사진을 올려 주세요."
            "형식" in text -> "JPG, PNG, WebP, HEIC 사진을 올려 주세요."
            else -> UPLOAD_NETWORK
        }
    }

    fun rejected(reason: String?): String {
        val line = reason
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?.replace(Regex("\\s+"), " ")
            ?.trimEnd('.', '!', '。', ' ')
            .orEmpty()
        KNOWN_REASONS[line]?.let { return "$REJECTED_LEAD $it" }
        if (!isPresentable(line)) return "$REJECTED_LEAD $RETAKE"
        val friendly = shorten(toHaeyo(line))
        return if (RETAKE_HINTS.any(friendly::contains)) {
            "$REJECTED_LEAD $friendly"
        } else {
            "$REJECTED_LEAD $friendly. $RETAKE"
        }
    }

    private fun isPresentable(line: String): Boolean =
        line.isNotBlank() &&
            line.any { it in '가'..'힣' } &&
            line.none { it.isISOControl() } &&
            RAW_TEXT_MARKERS.none(line::contains)

    private fun shorten(line: String): String {
        if (line.length <= MAX_REASON_LENGTH) return line
        val cut = line.take(MAX_REASON_LENGTH - 1)
        val wordEnd = cut.lastIndexOf(' ').takeIf { it >= MAX_REASON_LENGTH / 2 } ?: cut.length
        return cut.take(wordEnd).trimEnd(',', ' ') + "…"
    }

    /** Converts the common 합니다체 sentence endings Gemini tends to use into 해요체. */
    private fun toHaeyo(line: String): String {
        FORMAL_ENDINGS.firstOrNull { (formal, _) -> line.endsWith(formal) }?.let { (formal, polite) ->
            return line.dropLast(formal.length) + polite
        }
        if (line.endsWith("습니다")) {
            val stem = line.dropLast("습니다".length)
            // 잘렸습니다, 보였습니다, 했습니다: a past-tense ㅆ syllable takes 어요.
            if (stem.lastOrNull()?.let { finalConsonantIndex(it) == FINAL_SSANG_SIOT } == true) return stem + "어요"
        }
        if (line.endsWith("입니다")) {
            val stem = line.dropLast("입니다".length)
            val hasFinalConsonant = stem.lastOrNull()?.let { finalConsonantIndex(it) > 0 } == true
            return stem + if (hasFinalConsonant) "이에요" else "예요"
        }
        return line
    }

    private fun finalConsonantIndex(syllable: Char): Int =
        if (syllable in '가'..'힣') (syllable - '가') % 28 else -1

    private const val FINAL_SSANG_SIOT = 20

    private val FORMAL_ENDINGS = listOf(
        "아닙니다" to "아니에요",
        "없습니다" to "없어요",
        "있습니다" to "있어요",
        "않습니다" to "않아요",
        "됩니다" to "돼요",
        "합니다" to "해요",
        "주십시오" to "주세요",
    )
}
