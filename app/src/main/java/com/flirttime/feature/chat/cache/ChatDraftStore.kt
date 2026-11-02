package com.flirttime.feature.chat.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Unsent composer text for one thread. */
data class ChatDraft(
    val text: String,
    val replyToId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Per-thread composer drafts, mirroring the iOS `ConversationDraftStore`.
 *
 * The draft is written when the thread leaves the foreground and dropped as soon as the
 * message is sent, so returning to a conversation restores whatever was half typed.
 */
class ChatDraftStore(private val context: Context) {

    private val gson = Gson()

    private val cacheDir: File
        get() = File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }

    private val file: File
        get() = File(cacheDir, FILE_DRAFTS)

    suspend fun save(conversationId: String, text: String, replyToId: String? = null) {
        if (conversationId.isBlank()) return
        val trimmed = text.trimEnd()
        if (trimmed.isBlank()) {
            clear(conversationId)
            return
        }
        persist { drafts ->
            drafts[conversationId] = ChatDraft(text = trimmed, replyToId = replyToId)
        }
    }

    suspend fun load(conversationId: String): ChatDraft? {
        if (conversationId.isBlank()) return null
        return read()[conversationId]
    }

    suspend fun clear(conversationId: String) {
        if (conversationId.isBlank()) return
        persist { drafts -> drafts.remove(conversationId) }
    }

    suspend fun clearAll() = write("{}")

    private suspend fun persist(transform: (MutableMap<String, ChatDraft>) -> Unit) {
        val drafts = read()
        transform(drafts)
        write(gson.toJson(drafts))
    }

    private suspend fun read(): MutableMap<String, ChatDraft> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching mutableMapOf()
            val raw = file.readText()
            if (raw.isBlank()) {
                mutableMapOf()
            } else {
                gson.fromJson<Map<String, ChatDraft>>(raw, DRAFT_MAP.type)?.toMutableMap()
                    ?: mutableMapOf()
            }
        }.getOrElse {
            Log.w(TAG, "Unable to read drafts", it)
            mutableMapOf()
        }
    }

    private suspend fun write(payload: String) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir.mkdirs()
            file.writeText(payload)
        }.onFailure { Log.w(TAG, "Unable to write drafts", it) }
    }

    private companion object {
        const val TAG = "ChatDraftStore"
        const val CACHE_DIR = "chat_cache"
        const val FILE_DRAFTS = "drafts.json"

        val DRAFT_MAP = object : TypeToken<Map<String, ChatDraft>>() {}
    }
}
