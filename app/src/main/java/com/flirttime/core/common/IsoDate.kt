package com.flirttime.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 helpers shared by Profile screens. Port of iOS `MomentsDate`.
 */
object IsoDate {

    private val PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss"
    )

    /** Parses full ISO timestamps, falling back to a plain `yyyy-MM-dd` day. */
    fun parse(raw: String?): Date? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (pattern in PATTERNS) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(text)
            }.getOrNull()?.let { return it }
        }
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(text.take(10))
        }.getOrNull()
    }

    /** "now", "5m", "2h", "1d", "1w" (iOS `MomentsDate.timeAgo`). Empty when the date can't be parsed. */
    fun timeAgo(raw: String?): String {
        val date = parse(raw) ?: return ""
        val seconds = maxOf(((System.currentTimeMillis() - date.time) / 1000).toInt(), 0)
        return when {
            seconds < 60 -> "now"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            seconds < 604800 -> "${seconds / 86400}d"
            else -> "${seconds / 604800}w"
        }
    }

    /** Compact counter (1.2k, 3.4m), iOS `Int.vibeCountText`. */
    fun compactCount(value: Int): String = when {
        value < 1000 -> maxOf(value, 0).toString()
        value < 1_000_000 -> compact(value / 1000.0, "k")
        else -> compact(value / 1_000_000.0, "m")
    }

    private fun compact(value: Double, suffix: String): String {
        val rounded = Math.floor(value * 10) / 10
        val text = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
        return text + suffix
    }
}
