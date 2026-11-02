package com.flirttime.feature.chat.detail

import android.content.Context

/**
 * Mirrors the iOS `RecentReactionsManager`: recently used emoji are prepended to the
 * default set, and only the first [MAX_VISIBLE] are shown in the options sheet.
 */
internal object RecentReactions {

    private const val PREFS = "chat_reactions"
    private const val KEY_RECENT = "recent_reactions"
    private const val MAX_RECENT = 8
    private const val MAX_VISIBLE = 15

    val defaults = listOf("👍", "🔥", "💥", "🍀", "😍", "🙏", "😊", "🤗")

    fun visible(context: Context): List<String> =
        (stored(context) + defaults).distinct().take(MAX_VISIBLE)

    fun remember(context: Context, emoji: String) {
        if (emoji.isBlank()) return
        val updated = (listOf(emoji) + stored(context)).distinct().take(MAX_RECENT)
        prefs(context).edit().putString(KEY_RECENT, updated.joinToString(SEPARATOR)).apply()
    }

    private fun stored(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val SEPARATOR = "|"
}
