package com.example.dimanow.lms

import java.net.URI
import java.net.URLDecoder

object LmsUrlPolicy {
    val allowedHosts = setOf("lms.dima.ac.kr", "portal.dima.ac.kr")

    fun isAllowed(value: String): Boolean = runCatching {
        val uri = URI.create(value)
        uri.scheme == "https" && uri.host in allowedHosts && uri.userInfo == null
    }.getOrDefault(false)

    fun requireAllowed(value: String): URI = URI.create(value).also {
        require(isAllowed(value)) { "허용되지 않은 LMS 주소입니다" }
    }
}

object LmsAttachmentNaming {
    fun fromContentDisposition(header: String?, fallback: String): String {
        val extended = header?.let { FILENAME_STAR.find(it)?.groupValues?.get(1) }
            ?.substringAfter("''", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        val basic = header?.let { FILENAME.find(it)?.groupValues?.get(1) }
        return sanitize(extended ?: basic ?: fallback)
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cc}]"), "_")
        .trim(' ', '.')
        .take(160)
        .ifBlank { "첨부파일" }

    private val FILENAME_STAR = Regex("filename\\*\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
    private val FILENAME = Regex("filename\\s*=\\s*\"?([^\";]+)", RegexOption.IGNORE_CASE)
}
