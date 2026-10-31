package com.flirttime.feature.notification

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Supported Notification Types for FlirtTime.
 */
enum class NotificationType {
    MATCH,
    LIKE,
    COMPLIMENT,
    SUPER_LIKE,
    MESSAGE,
    PROFILE_VERIFIED,
    PHOTO_NOT_APPROVED,
    PROFILE_VISIT,
    MOMENT_COMMENT_REPLY,
    MOMENT_COMMENT_LIKE;

    companion object {
        fun fromString(type: String?): NotificationType {
            return when (type?.lowercase()?.trim()) {
                "match" -> MATCH
                "like" -> LIKE
                "compliment" -> COMPLIMENT
                "super_like", "superlike" -> SUPER_LIKE
                "message", "chat" -> MESSAGE
                "profile_verified", "verified" -> PROFILE_VERIFIED
                "photo_not_approved", "rejected", "photo_rejected" -> PHOTO_NOT_APPROVED
                "profile_visit", "visit" -> PROFILE_VISIT
                "moment_comment_reply", "comment_reply", "reply" -> MOMENT_COMMENT_REPLY
                "moment_comment_like", "comment_like" -> MOMENT_COMMENT_LIKE
                else -> MATCH
            }
        }
    }
}

/**
 * Temporal Grouping Sections.
 */
enum class NotificationSection(val displayTitle: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER("Earlier");

    companion object {
        fun fromString(section: String?): NotificationSection {
            return when (section?.lowercase()?.trim()) {
                "today" -> TODAY
                "yesterday" -> YESTERDAY
                else -> EARLIER
            }
        }
    }
}

/**
 * Individual Notification Domain Entity.
 */
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val description: String,
    val timestamp: String,
    val avatarUrl: String? = null,
    val isRead: Boolean = false,
    val section: NotificationSection
)

/**
 * Sealed UI Model for Sectioned List Rendering with DiffUtil.
 */
sealed class NotificationListItem {
    data class Header(val title: String) : NotificationListItem()
    data class Item(val notification: NotificationItem) : NotificationListItem()
}

// ------------------- API DTO Models & Custom Deserializer -------------------

data class NotificationActorDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

data class NotificationSenderDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

data class NotificationItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("isRead") val isRead: Boolean? = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("actor") val actor: NotificationActorDto? = null,
    @SerializedName("sender") val sender: NotificationSenderDto? = null
) {
    fun toDomain(section: NotificationSection): NotificationItem {
        val resolvedAvatar = actor?.profilePhoto?.takeIf { it.isNotBlank() }
            ?: sender?.profilePhotoUrl?.takeIf { it.isNotBlank() }
            ?: actor?.profilePhotoUrl?.takeIf { it.isNotBlank() }
            ?: sender?.profilePhoto?.takeIf { it.isNotBlank() }
            ?: image?.takeIf { it.isNotBlank() }
            ?: avatarUrl?.takeIf { it.isNotBlank() }
            ?: actor?.profilePicture?.takeIf { it.isNotBlank() }
            ?: actor?.avatar?.takeIf { it.isNotBlank() }
            ?: sender?.profilePicture?.takeIf { it.isNotBlank() }
            ?: sender?.avatar?.takeIf { it.isNotBlank() }

        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: actor?.name
            ?: sender?.name
            ?: "Notification"

        val resolvedDesc = body?.takeIf { it.isNotBlank() } ?: description.orEmpty()
        val formattedTime = formatRelativeTime(createdAt)

        return NotificationItem(
            id = id,
            type = NotificationType.fromString(type),
            title = resolvedTitle,
            description = resolvedDesc,
            timestamp = formattedTime,
            avatarUrl = resolvedAvatar,
            isRead = isRead ?: false,
            section = section
        )
    }

    private fun formatRelativeTime(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleanDate = if (createdAt.contains(".")) createdAt.substringBefore(".") else createdAt.replace("Z", "")
            val date = sdf.parse(cleanDate) ?: return ""
            val diffMillis = System.currentTimeMillis() - date.time
            if (diffMillis < 0) return "Just now"
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
            val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            createdAt
        }
    }
}

data class NotificationSectionDto(
    val title: String,
    val items: List<NotificationItemDto> = emptyList()
)

@JsonAdapter(NotificationListDeserializer::class)
data class NotificationListResponseData(
    val sections: List<NotificationSectionDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

data class NotificationCountsData(
    @SerializedName("unread") val unread: Int? = null,
    @SerializedName("unreadCount") val unreadCount: Int? = null,
    @SerializedName("count") val count: Int? = null
) {
    fun getEffectiveUnread(): Int = unread ?: unreadCount ?: count ?: 0
}

/**
 * Resilient Gson Deserializer supporting array of sections, map of sections, or root keys.
 */
class NotificationListDeserializer : JsonDeserializer<NotificationListResponseData> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): NotificationListResponseData {
        if (!json.isJsonObject) {
            return NotificationListResponseData()
        }

        val jsonObject = json.asJsonObject
        val paginationObj = if (jsonObject.has("pagination") && jsonObject.get("pagination").isJsonObject) {
            jsonObject.getAsJsonObject("pagination")
        } else null

        val nextCursor = jsonObject.get("nextCursor")?.takeIf { !it.isJsonNull }?.asString
            ?: paginationObj?.get("nextCursor")?.takeIf { !it.isJsonNull }?.asString
        val hasMore = jsonObject.get("hasMore")?.takeIf { !it.isJsonNull }?.asBoolean
            ?: paginationObj?.get("hasMore")?.takeIf { !it.isJsonNull }?.asBoolean
            ?: false

        val sectionsList = mutableListOf<NotificationSectionDto>()

        val sectionsElem = jsonObject.get("sections")
        if (sectionsElem != null && !sectionsElem.isJsonNull) {
            if (sectionsElem.isJsonArray) {
                // Format A: [ { "title": "today", "items": [...] } ]
                for (elem in sectionsElem.asJsonArray) {
                    if (elem.isJsonObject) {
                        val secObj = elem.asJsonObject
                        val title = secObj.get("title")?.asString
                            ?: secObj.get("key")?.asString
                            ?: secObj.get("name")?.asString
                            ?: "Earlier"
                        val itemsArray = secObj.get("items")?.asJsonArray ?: JsonArray()
                        val items = mutableListOf<NotificationItemDto>()
                        for (itemElem in itemsArray) {
                            items.add(context.deserialize(itemElem, NotificationItemDto::class.java))
                        }
                        sectionsList.add(NotificationSectionDto(title = title, items = items))
                    }
                }
            } else if (sectionsElem.isJsonObject) {
                // Format B: { "today": [...], "yesterday": [...], "earlier": [...] }
                for ((key, value) in sectionsElem.asJsonObject.entrySet()) {
                    if (value.isJsonArray) {
                        val items = mutableListOf<NotificationItemDto>()
                        for (itemElem in value.asJsonArray) {
                            items.add(context.deserialize(itemElem, NotificationItemDto::class.java))
                        }
                        sectionsList.add(NotificationSectionDto(title = key, items = items))
                    }
                }
            }
        }

        // Format C: Root level date keys if sections was not present
        if (sectionsList.isEmpty()) {
            for (key in listOf("today", "yesterday", "earlier", "Today", "Yesterday", "Earlier")) {
                val arrayElem = jsonObject.get(key)
                if (arrayElem != null && arrayElem.isJsonArray) {
                    val items = mutableListOf<NotificationItemDto>()
                    for (itemElem in arrayElem.asJsonArray) {
                        items.add(context.deserialize(itemElem, NotificationItemDto::class.java))
                    }
                    sectionsList.add(NotificationSectionDto(title = key, items = items))
                }
            }
        }

        // Format D: Fallback to root "notifications" or "items" if sectionsList is still empty
        if (sectionsList.isEmpty()) {
            val itemsArray = jsonObject.get("notifications")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: jsonObject.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
            if (itemsArray != null && itemsArray.size() > 0) {
                val items = mutableListOf<NotificationItemDto>()
                for (itemElem in itemsArray) {
                    items.add(context.deserialize(itemElem, NotificationItemDto::class.java))
                }
                sectionsList.add(NotificationSectionDto(title = "Earlier", items = items))
            }
        }

        return NotificationListResponseData(
            sections = sectionsList,
            nextCursor = nextCursor,
            hasMore = hasMore
        )
    }
}
