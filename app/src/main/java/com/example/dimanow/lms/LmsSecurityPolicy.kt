package com.example.dimanow.lms

import java.net.URI
import java.net.URLDecoder

object LmsUrlPolicy {
    val allowedHosts = setOf("lms.dima.ac.kr", "portal.dima.ac.kr")

    fun isAllowed(value: String): Boolean = runCatching {
        val uri = URI.create(value)
        uri.scheme == "https" && uri.host in allowedHosts && uri.userInfo == null &&
            uri.port in setOf(-1, 443)
    }.getOrDefault(false)

    fun isAllowedLoginNavigation(value: String): Boolean = isAllowed(value) || runCatching {
        val uri = URI.create(value)
        uri.scheme == "http" && uri.host == "sso.dima.ac.kr" &&
            uri.path in setOf("/sso/pmi-sso.jsp", "/sso/pmi-sso2.jsp") &&
            uri.userInfo == null && uri.port == 8080
    }.getOrDefault(false)

    fun requireAllowed(value: String): URI = URI.create(value).also {
        require(isAllowed(value)) { "허용되지 않은 LMS 주소입니다" }
    }

    fun upgradeOfficialHttp(value: String): String? = runCatching {
        val uri = URI.create(value)
        if (
            uri.scheme != "http" || uri.host !in allowedHosts || uri.userInfo != null ||
            uri.port !in setOf(-1, 80)
        ) return@runCatching null
        value.replaceFirst("http://", "https://")
    }.getOrNull()
}

object LmsAttachmentNaming {
    fun fromContentDisposition(header: String?, fallback: String): String {
        val extended = header
            ?.let { FILENAME_STAR.find(it)?.groupValues?.get(1) }
            ?.let(::decodeExtendedName)
        val basic = header
            ?.let { FILENAME.find(it)?.groupValues?.get(1) }
            ?.let(::unquote)
            ?.takeIf { it.isNotBlank() }
            ?.let { decodeOnce(it, plusAsSpace = shouldDecodePlusAsSpace(it)) }
        return sanitize(extended ?: basic ?: fallback)
    }

    private fun decodeExtendedName(raw: String): String? {
        val value = unquote(raw)
        val charsetEnd = value.indexOf('\'')
        val languageEnd = value.indexOf('\'', startIndex = charsetEnd + 1)
        if (charsetEnd <= 0 || languageEnd < 0) return null
        if (!value.substring(0, charsetEnd).equals("UTF-8", ignoreCase = true)) return null
        return value.substring(languageEnd + 1)
            .takeIf { it.isNotBlank() }
            ?.let { decodeOnce(it, plusAsSpace = false) }
    }

    private fun decodeOnce(value: String, plusAsSpace: Boolean): String = runCatching {
        URLDecoder.decode(
            if (plusAsSpace) value else value.replace("+", "%2B"),
            Charsets.UTF_8.name(),
        )
    }.getOrDefault(value)

    private fun shouldDecodePlusAsSpace(value: String): Boolean = PERCENT_ESCAPE.containsMatchIn(value)

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.lastIndex)
        } else {
            trimmed
        }
    }

    private fun sanitize(value: String): String = buildString(value.length) {
        var replacingUnsafeRun = false
        value.forEach { character ->
            val unsafe = character in PATH_CHARACTERS || character.isISOControl()
            if (unsafe) {
                if (!replacingUnsafeRun) append('_')
            } else {
                append(character)
            }
            replacingUnsafeRun = unsafe
        }
    }
        .trim(' ', '.')
        .take(160)
        .ifBlank { "첨부파일" }

    private const val PATH_CHARACTERS = "\\/:*?\"<>|"
    private val PERCENT_ESCAPE = Regex("%[0-9a-fA-F]{2}")
    private val FILENAME_STAR = Regex("filename\\*\\s*=\\s*(\"[^\"]*\"|[^;]+)", RegexOption.IGNORE_CASE)
    private val FILENAME = Regex("(?:^|;)\\s*filename\\s*=\\s*(\"[^\"]*\"|[^;]+)", RegexOption.IGNORE_CASE)
}
