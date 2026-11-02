package com.flirttime.feature.notification

import com.flirttime.feature.main.explore.ExploreTab

object NotificationPendingStore {
    var exploreTab: ExploreTab? = null
    var momentId: String? = null
    var momentSheet: String? = null
    var quickMomentId: String? = null

    fun consumeExploreTab(): ExploreTab? = exploreTab.also { exploreTab = null }

    fun consumeMoment(): Pair<String, String?>? {
        val id = momentId ?: return null
        val sheet = momentSheet
        momentId = null
        momentSheet = null
        return id to sheet
    }

    fun consumeQuickMomentId(): String? = quickMomentId.also { quickMomentId = null }
}
