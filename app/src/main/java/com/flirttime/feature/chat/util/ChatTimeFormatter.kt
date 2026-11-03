package com.flirttime.feature.chat.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Timestamp helpers for the chat UI.
 *
 * The chat backend mixes ISO-8601 with and without fractional seconds (up to microseconds)
 * and occasionally a plain `yyyy-MM-dd HH:mm:ss`. `java.time` is off-limits on minSdk 24, so
 * parsing is done by extracting the numeric components with a regex.
 */
object ChatTimeFormatter {

    private val ISO_REGEX = Regex(
        "(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?"
    )

    private const val ISO_OUTPUT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    fun parse(raw: String?): Date? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        val match = ISO_REGEX.find(value) ?: return null
        val groups = match.groupValues
        return try {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                clear()
                set(
                    groups[1].toInt(),
                    groups[2].toInt() - 1,
                    groups[3].toInt(),
                    groups[4].toInt(),
                    groups[5].toInt(),
                    groups[6].toInt()
                )
                val fraction = groups[7]
                if (fraction.isNotEmpty()) {
                    val millis = fraction.padEnd(3, '0').take(3).toInt()
                    set(Calendar.MILLISECOND, millis)
                }
            }
            calendar.time
        } catch (_: Exception) {
            null
        }
    }

    fun formatIsoUtc(date: Date): String =
        SimpleDateFormat(ISO_OUTPUT_PATTERN, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)

    /** Inbox row label: `14:32`, `Yesterday`, `Mon`, or `12/09/25`. */
    fun inboxLabel(raw: String?, now: Long = System.currentTimeMillis()): String {
        val date = parse(raw) ?: return ""
        val calendar = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()

        val sameDay = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return format(date, "HH:mm")

        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "Yesterday"

        val weekAgo = now - 6L * 24 * 60 * 60 * 1000
        if (date.time >= weekAgo) return format(date, "EEE")

        return format(date, "dd/MM/yy")
    }

    /**
     * iOS-style relative inbox / call-history label:
     * `Just now`, `5 min ago`, `20 hr ago`, `3 d ago`, `1 wk ago`, `2 mo ago`.
     */
    fun inboxRelativeLabel(raw: String?, now: Long = System.currentTimeMillis()): String {
        val date = parse(raw) ?: return ""
        val seconds = ((now - date.time) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60L -> "Just now"
            seconds < 3600L -> {
                val mins = (seconds / 60L).toInt().coerceAtLeast(1)
                "$mins min ago"
            }
            seconds < 86_400L -> {
                val hours = (seconds / 3600L).toInt().coerceAtLeast(1)
                "$hours hr ago"
            }
            seconds < 604_800L -> {
                val days = (seconds / 86_400L).toInt().coerceAtLeast(1)
                "$days d ago"
            }
            seconds < 2_592_000L -> {
                val weeks = (seconds / 604_800L).toInt().coerceAtLeast(1)
                "$weeks wk ago"
            }
            seconds < 31_536_000L -> {
                val months = (seconds / 2_592_000L).toInt().coerceAtLeast(1)
                "$months mo ago"
            }
            else -> {
                val years = (seconds / 31_536_000L).toInt().coerceAtLeast(1)
                "$years yr ago"
            }
        }
    }

    /** Bubble-side label — always time-of-day for today, otherwise a short date. */
    fun messageTimeLabel(raw: String?): String {
        val date = parse(raw) ?: return ""
        val calendar = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        val sameDay = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) format(date, "HH:mm") else format(date, "dd MMM, HH:mm")
    }

    /** Sticky separator label: `Today`, `Yesterday`, `Monday`, `12 September 2025`. */
    fun dateSeparatorLabel(raw: String?): String {
        val date = parse(raw) ?: return ""
        val calendar = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        ) {
            return "Today"
        }
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
        ) {
            return "Yesterday"
        }
        val daysApart = (today.timeInMillis - date.time) / (24L * 60 * 60 * 1000)
        return if (daysApart in 0..6) format(date, "EEEE") else format(date, "d MMMM yyyy")
    }

    /** Day bucket key used to decide whether a separator row is needed. */
    fun dayKey(raw: String?): String {
        val date = parse(raw) ?: return ""
        return format(date, "yyyy-MM-dd")
    }

    private fun format(date: Date, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}
