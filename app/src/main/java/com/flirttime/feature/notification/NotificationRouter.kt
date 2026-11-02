package com.flirttime.feature.notification

import android.app.Activity
import android.content.Context
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.chat.notification.ChatDeepLink
import com.flirttime.feature.main.explore.ExploreTab
import com.flirttime.feature.subscription.PremiumActivity

object NotificationRouter {

    fun open(context: Context, item: NotificationItem) {
        open(context, item.routeType, item.payload)
    }

    fun open(context: Context, type: NotificationRouteType, payload: Map<String, String>) {
        when (type) {
            NotificationRouteType.LIKE,
            NotificationRouteType.FAVORITE -> openExplore(context, ExploreTab.LIKES)
            NotificationRouteType.MATCH -> openExplore(context, ExploreTab.MATCHES)
            NotificationRouteType.COMPLIMENT -> openExplore(context, ExploreTab.COMPLIMENTS)
            NotificationRouteType.MESSAGE -> openChat(context, payload)
            NotificationRouteType.CALL,
            NotificationRouteType.MISSED_CALL ->
                AppNavigator.navigateTo(context, Destination.Main(targetTab = 3))
            NotificationRouteType.MOMENT_LIKE -> openMoments(context, first(payload, "momentId", "navTargetId", "entityId"))
            NotificationRouteType.MOMENT_COMMENT,
            NotificationRouteType.MOMENT_COMMENT_LIKE ->
                openMoments(context, first(payload, "momentId", "navTargetId", "entityId"), "comments")
            NotificationRouteType.MOMENT_GIFT ->
                openMoments(context, first(payload, "momentId", "navTargetId", "entityId"), "gifts")
            NotificationRouteType.MOMENT_REACTION -> {
                NotificationPendingStore.quickMomentId =
                    first(payload, "quickMomentId", "momentId", "navTargetId", "entityId")
                AppNavigator.navigateTo(context, Destination.Main(targetTab = 4))
            }
            NotificationRouteType.PROFILE_VISIT -> {
                val visitorId = first(payload, "visitorId", "navTargetId", "entityId", "actor_id", "senderId")
                if (visitorId.isNullOrBlank()) {
                    AppNavigator.navigateTo(context, Destination.Main(targetTab = 4))
                } else {
                    val name = first(payload, "visitorName", "senderName")
                        ?.takeUnless { it.equals("Someone", ignoreCase = true) }
                    AppNavigator.navigateTo(
                        context,
                        Destination.UserProfile(userId = visitorId, name = name)
                    )
                }
            }
            NotificationRouteType.GESTURE_VERIFIED,
            NotificationRouteType.IMAGE_VERIFIED,
            NotificationRouteType.PROFILE_IMAGE_VERIFIED ->
                AppNavigator.navigateTo(context, Destination.Main(targetTab = 4))
            NotificationRouteType.GESTURE_UNVERIFIED ->
                AppNavigator.navigateTo(context, Destination.GestureVerification)
            NotificationRouteType.PROFILE_IMAGE_UNVERIFIED ->
                AppNavigator.navigateTo(context, Destination.ProfileVerification)
            NotificationRouteType.IMAGE_UNVERIFIED,
            NotificationRouteType.INCOMPLETE ->
                AppNavigator.navigateTo(context, Destination.EditProfile)
            NotificationRouteType.MEMBERSHIP_UPGRADE -> PremiumActivity.start(context)
            NotificationRouteType.UNKNOWN -> Unit
        }
        if (context is Activity && context is NotificationActivity) {
            context.finish()
        }
    }

    private fun openExplore(context: Context, tab: ExploreTab) {
        NotificationPendingStore.exploreTab = tab
        AppNavigator.navigateTo(context, Destination.Main(targetTab = 1))
    }

    private fun openMoments(context: Context, momentId: String?, sheet: String? = null) {
        NotificationPendingStore.momentId = momentId
        NotificationPendingStore.momentSheet = sheet
        AppNavigator.navigateTo(context, Destination.Main(targetTab = 0))
    }

    private fun openChat(context: Context, payload: Map<String, String>) {
        val conversationId = first(payload, "conversationId", "conversation_id", "chatId", "navTargetId", "entityId")
        if (conversationId.isNullOrBlank()) {
            AppNavigator.navigateTo(context, Destination.Main(targetTab = 3))
            return
        }
        context.startActivity(
            ChatDeepLink.buildChatIntent(
                context = context,
                conversationId = conversationId,
                title = first(payload, "senderName", "title"),
                peerUserId = first(payload, "senderId", "user_id", "actor_id")
            )
        )
    }

    private fun first(payload: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = payload[key]?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
