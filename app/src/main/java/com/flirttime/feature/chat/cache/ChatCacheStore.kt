package com.flirttime.feature.chat.cache

import android.content.Context
import android.util.Log
import com.flirttime.feature.chat.model.ConversationUi
import com.flirttime.feature.chat.model.MessageUi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One outbound message waiting for the server to acknowledge it. Mirrors the iOS
 * `PendingMessageStore.PendingEntry`: the temp id is the client message id, so a retry
 * always reuses the same idempotency key.
 */
data class OutboxEntry(
    val tempId: String,
    val conversationId: String,
    val text: String,
    val replyToId: String? = null,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Lightweight JSON disk cache that stands in for the iOS Core Data store.
 *
 * The Android client has no local database, so the last successfully loaded inbox page,
 * the tail of every opened conversation and the outbound queue are mirrored as JSON in
 * `filesDir/chat_cache`. Writes are best-effort: a failed cache write never surfaces to
 * the user, it only means the screen falls back to the network next time.
 */
class ChatCacheStore(private val context: Context) {

    private val gson = Gson()

    private val cacheDir: File
        get() = File(context.filesDir, CACHE_DIR).apply { if (!exists()) mkdirs() }

    // region Conversations

    suspend fun saveConversations(conversations: List<ConversationUi>, archived: Boolean) =
        write(conversationFile(archived), gson.toJson(conversations))

    suspend fun loadConversations(archived: Boolean): List<ConversationUi> =
        readList(conversationFile(archived), CONVERSATION_LIST)

    // endregion

    // region Messages

    suspend fun saveMessages(conversationId: String, messages: List<MessageUi>) {
        if (conversationId.isBlank()) return
        val trimmed = messages
            .filterNot { it.isOptimisticTemporary }
            .takeLast(MESSAGE_CACHE_LIMIT)
        write(messageFile(conversationId), gson.toJson(trimmed))
    }

    suspend fun loadMessages(conversationId: String): List<MessageUi> {
        if (conversationId.isBlank()) return emptyList()
        return readList(messageFile(conversationId), MESSAGE_LIST)
    }

    suspend fun clearMessages(conversationId: String) {
        if (conversationId.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching { messageFile(conversationId).delete() }
                .onFailure { Log.w(TAG, "Unable to clear cached messages", it) }
        }
    }

    /**
     * Records when the user cleared this thread. The server keeps its copy (there is no
     * clear-chat endpoint), so the marker lets the thread hide everything up to that point.
     */
    suspend fun markCleared(conversationId: String, atMillis: Long = System.currentTimeMillis()) {
        if (conversationId.isBlank()) return
        write(clearedFile(conversationId), atMillis.toString())
    }

    /** Epoch millis of the last clear, or `0` when the thread was never cleared. */
    suspend fun clearedAt(conversationId: String): Long {
        if (conversationId.isBlank()) return 0L
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = clearedFile(conversationId)
                if (!file.exists()) 0L else file.readText().trim().toLongOrNull() ?: 0L
            }.getOrDefault(0L)
        }
    }

    // endregion

    // region Outbox

    suspend fun saveOutbox(entries: List<OutboxEntry>) =
        write(outboxFile(), gson.toJson(entries))

    suspend fun loadOutbox(): List<OutboxEntry> = readList(outboxFile(), OUTBOX_LIST)

    /** Adds or replaces one queued send; used before the first network attempt. */
    suspend fun enqueue(entry: OutboxEntry) {
        val merged = loadOutbox()
            .filterNot { it.tempId == entry.tempId } + entry
        saveOutbox(merged.sortedBy { it.createdAt })
    }

    suspend fun removeFromOutbox(tempId: String) {
        val remaining = loadOutbox().filterNot { it.tempId == tempId }
        saveOutbox(remaining)
    }

    suspend fun markAttempt(tempId: String) {
        val updated = loadOutbox().map {
            if (it.tempId == tempId) it.copy(attempts = it.attempts + 1) else it
        }
        saveOutbox(updated)
    }

    suspend fun pendingFor(conversationId: String): List<OutboxEntry> =
        loadOutbox().filter { it.conversationId == conversationId }

    /** Drops queued sends for a thread that was deleted / cleared. */
    suspend fun clearOutbox(conversationId: String) {
        saveOutbox(loadOutbox().filterNot { it.conversationId == conversationId })
    }

    // endregion

    // region Disk helpers

    private suspend fun write(target: File, payload: String) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir.mkdirs()
            target.writeText(payload)
        }.onFailure { Log.w(TAG, "Unable to write ${target.name}", it) }
    }

    private suspend inline fun <reified T> readList(
        target: File,
        typeToken: TypeToken<List<T>>
    ): List<T> = withContext(Dispatchers.IO) {
        runCatching {
            if (!target.exists()) return@runCatching emptyList<T>()
            val raw = target.readText()
            if (raw.isBlank()) return@runCatching emptyList<T>()
            gson.fromJson<List<T>>(raw, typeToken.type).orEmpty()
        }.getOrElse {
            Log.w(TAG, "Unable to read ${target.name}", it)
            emptyList()
        }
    }

    private fun conversationFile(archived: Boolean): File =
        File(cacheDir, if (archived) FILE_ARCHIVED else FILE_INBOX)

    private fun messageFile(conversationId: String): File =
        File(cacheDir, "$FILE_MESSAGES_PREFIX${sanitize(conversationId)}.json")

    private fun clearedFile(conversationId: String): File =
        File(cacheDir, "$FILE_CLEARED_PREFIX${sanitize(conversationId)}.txt")

    private fun outboxFile(): File = File(cacheDir, FILE_OUTBOX)

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    // endregion

    private companion object {
        const val TAG = "ChatCacheStore"
        const val CACHE_DIR = "chat_cache"
        const val FILE_INBOX = "conversations.json"
        const val FILE_ARCHIVED = "conversations_archived.json"
        const val FILE_MESSAGES_PREFIX = "messages_"
        const val FILE_CLEARED_PREFIX = "cleared_"
        const val FILE_OUTBOX = "outbox.json"
        const val MESSAGE_CACHE_LIMIT = 80

        val CONVERSATION_LIST = object : TypeToken<List<ConversationUi>>() {}
        val MESSAGE_LIST = object : TypeToken<List<MessageUi>>() {}
        val OUTBOX_LIST = object : TypeToken<List<OutboxEntry>>() {}
    }
}
