package com.flirttime.feature.chat

import com.flirttime.Resource
import com.flirttime.core.network.safeRawApiCall
import com.flirttime.feature.chat.media.ChatMediaUploader
import com.flirttime.feature.chat.model.ArchiveConversationRequest
import com.flirttime.feature.chat.model.BlockUserRequest
import com.flirttime.feature.chat.model.ChatMediaUpload
import com.flirttime.feature.chat.model.ChatMessageDto
import com.flirttime.feature.chat.model.ConversationRowDto
import com.flirttime.feature.chat.model.DeleteConversationRequest
import com.flirttime.feature.chat.model.DeleteMessageRequest
import com.flirttime.feature.chat.model.EditMessageRequest
import com.flirttime.feature.chat.model.ForwardMessagesRequest
import com.flirttime.feature.chat.model.MessagesPageDto
import com.flirttime.feature.chat.model.PinConversationRequest
import com.flirttime.feature.chat.model.PinMessageRequest
import com.flirttime.feature.chat.model.ReactionRequest
import com.flirttime.feature.chat.model.SendMessageRequest
import com.flirttime.feature.chat.model.UpdateSettingsRequest
import java.util.UUID

class ChatRepositoryImpl(
    private val api: ChatApiService,
    private val mediaUploader: ChatMediaUploader
) : ChatRepository {

    override suspend fun getConversations(
        page: Int,
        limit: Int,
        search: String?,
        archived: Boolean?
    ): Resource<List<ConversationRowDto>> = safeRawApiCall {
        val response = api.getConversations(
            page = page,
            limit = limit,
            search = search?.takeIf { it.isNotBlank() },
            archived = archived
        )
        val rows = response.resolvedRows()
        response.success.failureMessage(rows.isNotEmpty() || response.data != null)?.let {
            error(it)
        }
        rows
    }

    override suspend fun getMessages(
        conversationId: String,
        page: Int,
        limit: Int,
        before: String?
    ): Resource<MessagesPageDto> = safeRawApiCall {
        val response = api.getMessages(
            conversationId = conversationId,
            page = page,
            limit = limit,
            before = before
        )
        val data = response.data
        response.success.failureMessage(data != null)?.let { error(it) }
        data?.resolvedPage() ?: MessagesPageDto(conversationId = conversationId)
    }

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        clientMessageId: String,
        replyToId: String?
    ): Resource<ChatMessageDto> = safeRawApiCall {
        val response = api.sendMessage(
            conversationId = conversationId,
            body = SendMessageRequest(
                content = text,
                messageType = "text",
                type = "text",
                clientMessageId = clientMessageId,
                replyToId = replyToId?.takeIf { it.isNotBlank() }
            )
        )
        val message = response.data?.toMessage()
        response.success.failureMessage(message != null)?.let { error(it) }
        message ?: error(response.message ?: "Message could not be sent")
    }

    override suspend fun markConversationRead(
        conversationId: String,
        lastReadMessageId: String?
    ): Resource<Unit> = safeRawApiCall {
        val body = buildMap {
            put("conversationId", conversationId)
            if (!lastReadMessageId.isNullOrBlank()) {
                put("messageId", lastReadMessageId)
                put("lastReadMessageId", lastReadMessageId)
            }
        }
        api.markConversationRead(conversationId, body)
        Unit
    }

    override suspend fun uploadChatMedia(
        kind: String,
        contentType: String,
        fileName: String,
        bytes: ByteArray
    ): Resource<ChatMediaUpload> = mediaUploader.upload(
        kind = kind,
        contentType = contentType,
        fileName = fileName,
        bytes = bytes
    )

    override suspend fun sendMediaMessage(
        conversationId: String,
        kind: String,
        upload: ChatMediaUpload,
        clientMessageId: String,
        fileName: String,
        fileSizeBytes: Long,
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        caption: String?,
        replyToId: String?
    ): Resource<ChatMessageDto> = safeRawApiCall {
        val metadata = buildMediaMetadata(
            kind = kind,
            upload = upload,
            clientMessageId = clientMessageId,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            durationSeconds = durationSeconds,
            width = width,
            height = height
        )

        // Audio/video need a resolvable `content`; images accept the public URL or the key.
        val content = when (kind) {
            "image", "audio" -> caption?.takeIf { it.isNotBlank() }
                ?: upload.publicUrl
                ?: upload.mediaId
            else -> upload.mediaId
        }

        val response = api.sendMessage(
            conversationId = conversationId,
            body = SendMessageRequest(
                content = content,
                messageType = kind,
                type = kind,
                clientMessageId = clientMessageId,
                mediaIds = listOf(upload.mediaId),
                metadata = metadata,
                replyToId = replyToId?.takeIf { it.isNotBlank() }
            )
        )

        val message = response.data?.toMessage()
        response.success.failureMessage(message != null)?.let { error(it) }
        message ?: error(response.message ?: "Message could not be sent")
    }

    override suspend fun editMessage(
        conversationId: String,
        messageId: String,
        content: String
    ): Resource<Unit> = safeRawApiCall {
        val response = api.editMessage(
            conversationId = conversationId,
            messageId = messageId,
            body = EditMessageRequest(content = content)
        )
        response.success?.takeIf { !it }?.let { error(response.message ?: "Message could not be edited") }
        Unit
    }

    override suspend fun deleteMessage(
        conversationId: String,
        messageId: String,
        deleteForEveryone: Boolean
    ): Resource<Unit> = safeRawApiCall {
        api.deleteMessage(
            conversationId = conversationId,
            messageId = messageId,
            body = DeleteMessageRequest(deleteType = if (deleteForEveryone) "everyone" else "me")
        )
        Unit
    }

    override suspend fun reactToMessage(
        conversationId: String,
        messageId: String,
        emoji: String
    ): Resource<Any?> = safeRawApiCall {
        val response = api.reactToMessage(
            conversationId = conversationId,
            messageId = messageId,
            body = ReactionRequest(emoji = emoji)
        )
        response.success?.takeIf { !it }?.let { error(response.message ?: "Reaction failed") }
        response.resolvedReactions()
    }

    override suspend fun pinMessage(
        conversationId: String,
        messageId: String,
        pinned: Boolean,
        pinDuration: String?
    ): Resource<Unit> = safeRawApiCall {
        val response = api.pinMessage(
            conversationId = conversationId,
            messageId = messageId,
            body = PinMessageRequest(
                isPinned = pinned,
                pinned = pinned,
                // The backend only understands `scope` / `pinDuration` while pinning.
                scope = if (pinned) "me" else null,
                pinDuration = pinDuration?.takeIf { pinned && it.isNotBlank() }
            )
        )
        response.success?.takeIf { !it }?.let { error(response.message ?: "Pin failed") }
        Unit
    }

    override suspend fun forwardMessages(
        messageIds: List<String>,
        recipientIds: List<String>
    ): Resource<Unit> = safeRawApiCall {
        val ids = messageIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val peers = recipientIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) error("Missing message ids")
        if (peers.isEmpty()) error("Missing recipient ids")

        val response = api.forwardMessages(
            ForwardMessagesRequest(
                messageIds = ids,
                recipientIds = peers,
                clientMessageId = UUID.randomUUID().toString()
            )
        )
        response.success?.takeIf { !it }?.let { error(response.message ?: "Failed to forward message") }
        Unit
    }

    override suspend fun getForwardRecipients(
        page: Int,
        limit: Int,
        search: String?
    ): Resource<ForwardRecipientPage> = safeRawApiCall {
        val response = api.getForwardRecipients(
            page = page,
            limit = limit,
            search = search?.takeIf { it.isNotBlank() }
        )
        val rows = response.resolvedRecipients()
        response.success?.takeIf { !it }?.let { error(response.message ?: "Could not load contacts") }
        ForwardRecipientPage(
            recipients = rows,
            hasMore = response.resolvedHasMore(received = rows.size, requested = limit)
        )
    }

    override suspend fun updateConversationSettings(
        conversationId: String,
        isMuted: Boolean?,
        isPinned: Boolean?,
        isArchived: Boolean?
    ): Resource<Unit> = safeRawApiCall {
        if (conversationId.isBlank()) error("Missing conversation id")

        // Pin has a dedicated route; the settings endpoint ignores `isPinned`.
        if (isPinned != null) {
            val pinned = api.pinConversation(
                conversationId = conversationId,
                body = PinConversationRequest(isPinned = isPinned)
            )
            pinned.success?.takeIf { !it }
                ?.let { error(pinned.message ?: "Could not update the pin") }
        }

        // Archive lives behind its own route on the iOS client; some deployments only ship one
        // of the two paths, so try the documented one first and fall back to the legacy one.
        var archiveHandled = false
        if (isArchived != null) {
            val body = ArchiveConversationRequest(isArchived = isArchived)
            val archived = runCatching { api.archiveConversation(conversationId, body) }
                .recoverCatching { api.archiveConversationAlt(conversationId, body) }
                .getOrNull()
            if (archived != null) {
                archived.success?.takeIf { !it }
                    ?.let { error(archived.message ?: "Could not archive the chat") }
                archiveHandled = true
            }
        }

        val settingsMuted = isMuted
        val settingsArchived = if (isArchived != null && !archiveHandled) isArchived else null
        if (settingsMuted != null || settingsArchived != null) {
            val settings = api.updateConversationSettings(
                conversationId = conversationId,
                body = UpdateSettingsRequest(
                    isMuted = settingsMuted,
                    isArchived = settingsArchived
                )
            )
            settings.success?.takeIf { !it }
                ?.let { error(settings.message ?: "Could not update the chat settings") }
        }

        Unit
    }

    override suspend fun markConversationUnread(conversationId: String): Resource<Unit> =
        safeRawApiCall {
            if (conversationId.isBlank()) error("Missing conversation id")
            api.markConversationUnread(conversationId, mapOf("conversationId" to conversationId))
            Unit
        }

    override suspend fun deleteConversation(
        conversationId: String,
        forEveryone: Boolean
    ): Resource<Unit> = safeRawApiCall {
        if (conversationId.isBlank()) error("Missing conversation id")
        api.deleteConversation(
            conversationId = conversationId,
            body = DeleteConversationRequest(deleteType = if (forEveryone) "everyone" else "me")
        )
        Unit
    }

    override suspend fun blockUser(
        userId: String,
        conversationId: String?
    ): Resource<Unit> = safeRawApiCall {
        if (userId.isBlank()) error("Missing user id")
        val response = api.blockUser(
            userId = userId,
            body = BlockUserRequest(conversationId = conversationId?.takeIf { it.isNotBlank() })
        )
        response.success?.takeIf { !it }?.let { error(response.message ?: "Could not block the user") }
        Unit
    }

    override suspend fun unblockUser(userId: String): Resource<Unit> = safeRawApiCall {
        if (userId.isBlank()) error("Missing user id")
        val response = api.unblockUser(userId)
        response.success?.takeIf { !it }
            ?.let { error(response.message ?: "Could not unblock the user") }
        Unit
    }

    private fun buildMediaMetadata(
        kind: String,
        upload: ChatMediaUpload,
        clientMessageId: String,
        fileName: String,
        fileSizeBytes: Long,
        durationSeconds: Double?,
        width: Int?,
        height: Int?
    ): Map<String, Any> = buildMap {
        put("clientTempId", clientMessageId)
        when (kind) {
            "image" -> {
                put("source", "photo")
                upload.publicUrl?.let {
                    put("imageUrl", it)
                    put("mediaUrl", it)
                }
                upload.thumbnailUrl?.let { put("thumbnailUrl", it) }
                width?.let { put("width", it) }
                height?.let { put("height", it) }
            }
            "audio" -> {
                put("fileName", fileName)
                put("filename", fileName)
                put("fileSize", fileSizeBytes)
                put("filesize", fileSizeBytes.toString())
                put("mediaId", upload.mediaId)
                put("cachedS3Key", upload.mediaId)
                upload.publicUrl?.let { put("mediaUrl", it) }
                // FlirtTime reads the numeric duration first, the camelCase alias is a fallback.
                val seconds = durationSeconds ?: 0.0
                put("audio_duration", seconds)
                put("audioDuration", seconds)
                put("waveSeed", clientMessageId)
            }
            else -> {
                put("source", kind)
                upload.thumbnailUrl?.let { put("thumbnailUrl", it) }
                durationSeconds?.let { put("duration", it) }
            }
        }
    }

    /**
     * A chat envelope only fails when the server explicitly says so, or when it says
     * nothing at all and shipped no payload. `success` legitimately arrives `null`
     * on some chat endpoints.
     */
    private fun Boolean?.failureMessage(hasPayload: Boolean): String? = when {
        this == false -> "Request failed"
        this == null && !hasPayload -> "Request failed"
        else -> null
    }
}
