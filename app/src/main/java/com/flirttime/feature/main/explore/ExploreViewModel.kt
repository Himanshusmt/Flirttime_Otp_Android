package com.flirttime.feature.main.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.feature.main.MainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val mainRepository: MainRepository
) : ViewModel() {

    companion object {
        // Hot in-memory cache shared across fragment instances for instant screen opening
        private val persistentTabItems = mutableMapOf<ExploreTab, MutableList<ExploreProfileItem>>()
        private val persistentCursors = mutableMapOf<ExploreTab, String?>()
        private val persistentHasMore = mutableMapOf<ExploreTab, Boolean>()
        private val persistentIsLocked = mutableMapOf<ExploreTab, Boolean>()
        private val persistentBadgeCounts = mutableMapOf<ExploreTab, Int>()
    }

    private val _currentTab = MutableStateFlow(ExploreTab.YOU_LIKED)
    val currentTab: StateFlow<ExploreTab> = _currentTab.asStateFlow()

    // Start with cached data if available for instant 0ms rendering
    private val _exploreState = MutableStateFlow<Resource<List<ExploreProfileItem>>>(
        persistentTabItems[ExploreTab.YOU_LIKED]?.let { Resource.Success(it) } ?: Resource.Loading
    )
    val exploreState: StateFlow<Resource<List<ExploreProfileItem>>> = _exploreState.asStateFlow()

    private val _isCurrentTabLocked = MutableStateFlow(persistentIsLocked[ExploreTab.YOU_LIKED] ?: false)
    val isCurrentTabLocked: StateFlow<Boolean> = _isCurrentTabLocked.asStateFlow()

    private val _upsellText = MutableStateFlow<String?>(null)
    val upsellText: StateFlow<String?> = _upsellText.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _badgeCounts = MutableStateFlow<Map<ExploreTab, Int>>(persistentBadgeCounts.toMap())
    val badgeCounts: StateFlow<Map<ExploreTab, Int>> = _badgeCounts.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val tabGenerations = mutableMapOf<ExploreTab, Int>()
    private var isFetchingNextPage = false

    init {
        loadCounts()
        // 1. Immediately load the default active tab (You Liked)
        val hasCachedDefault = !persistentTabItems[ExploreTab.YOU_LIKED].isNullOrEmpty()
        loadTab(ExploreTab.YOU_LIKED, isSilentBackground = hasCachedDefault)

        // 2. Pre-fetch remaining tabs in background so tab switching is instant (matches iOS ExploreTab.allCases.forEach(loadTab))
        viewModelScope.launch(Dispatchers.IO) {
            listOf(ExploreTab.LIKES, ExploreTab.MATCHES, ExploreTab.COMPLIMENTS).forEach { tab ->
                if (persistentTabItems[tab].isNullOrEmpty()) {
                    loadTab(tab, isSilentBackground = true)
                }
            }
        }
    }

    fun getCachedItems(tab: ExploreTab): List<ExploreProfileItem>? {
        return persistentTabItems[tab]
    }

    fun selectTab(tab: ExploreTab) {
        val previousTab = _currentTab.value
        _currentTab.value = tab
        updateUpsellAndLockState(tab)

        val cached = persistentTabItems[tab]
        if (!cached.isNullOrEmpty()) {
            // Instant display from cache!
            _exploreState.value = Resource.Success(cached)
            // Silently check for fresh items in background if switching tabs
            if (previousTab != tab) {
                loadTab(tab, isSilentBackground = true)
            }
        } else {
            loadTab(tab, isSilentBackground = false)
        }
    }

    fun refreshCurrentTab() {
        val tab = _currentTab.value
        persistentCursors.remove(tab)
        persistentHasMore.remove(tab)
        loadCounts()
        loadTab(tab, isPullToRefresh = true)
    }

    fun loadCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            mainRepository.getInteractionCounts().collect { resource ->
                if (resource is Resource.Success) {
                    val data = resource.data
                    val updatedBadges = _badgeCounts.value.toMutableMap()
                    updatedBadges[ExploreTab.LIKES] = data.effectiveLikes
                    updatedBadges[ExploreTab.YOU_LIKED] = data.effectiveYouLiked
                    updatedBadges[ExploreTab.MATCHES] = data.effectiveMatches
                    updatedBadges[ExploreTab.COMPLIMENTS] = data.effectiveCompliments
                    persistentBadgeCounts.putAll(updatedBadges)
                    _badgeCounts.value = updatedBadges
                }
            }
        }
    }

    private fun loadTab(tab: ExploreTab, isSilentBackground: Boolean = false, isPullToRefresh: Boolean = false) {
        val nextGen = (tabGenerations[tab] ?: 0) + 1
        tabGenerations[tab] = nextGen

        if (isPullToRefresh) {
            _isRefreshing.value = true
        } else if (!isSilentBackground && persistentTabItems[tab].isNullOrEmpty()) {
            _exploreState.value = Resource.Loading
        }

        val apiType = tab.toApiType()

        viewModelScope.launch(Dispatchers.IO) {
            mainRepository.getInteractions(type = apiType, limit = 20, cursor = null).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        if (tabGenerations[tab] != nextGen) return@collect
                        val data = resource.data
                        val isLocked = data.effectiveIsLocked
                        val rawItems = data.effectiveItems
                        val nextCursor = data.pagination?.effectiveCursor
                        val hasMore = data.pagination?.effectiveHasMore ?: (nextCursor != null)

                        // Update badge count from response totalCount if available
                        val updatedBadges = _badgeCounts.value.toMutableMap()
                        updatedBadges[tab] = data.effectiveTotalCount
                        persistentBadgeCounts.putAll(updatedBadges)
                        _badgeCounts.value = updatedBadges

                        val profileItems = rawItems.map { it.toExploreProfileItem(tab, isLocked) }.toMutableList()
                        persistentTabItems[tab] = profileItems
                        persistentCursors[tab] = nextCursor
                        persistentHasMore[tab] = hasMore
                        persistentIsLocked[tab] = isLocked

                        // Pre-fetch images to disk cache for instantaneous scrolling
                        profileItems.forEach { item ->
                            ShimmerHelper.prefetchImage(item.imageUrl)
                        }

                        if (_currentTab.value == tab) {
                            updateUpsellAndLockState(tab)
                            _exploreState.value = Resource.Success(profileItems)
                        }
                        _isRefreshing.value = false
                    }
                    is Resource.Error -> {
                        if (tabGenerations[tab] != nextGen) return@collect
                        _isRefreshing.value = false
                        if (_currentTab.value == tab && persistentTabItems[tab].isNullOrEmpty()) {
                            _exploreState.value = Resource.Error(resource.message)
                        }
                        _errorMessage.emit(resource.message)
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun loadNextPage() {
        val tab = _currentTab.value
        val cursor = persistentCursors[tab]
        val hasMore = persistentHasMore[tab] ?: false

        if (!hasMore || cursor.isNullOrBlank() || isFetchingNextPage) return

        isFetchingNextPage = true
        val apiType = tab.toApiType()

        viewModelScope.launch(Dispatchers.IO) {
            mainRepository.getInteractions(type = apiType, limit = 20, cursor = cursor).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val data = resource.data
                        val isLocked = data.effectiveIsLocked
                        val rawItems = data.effectiveItems
                        val nextCursor = data.pagination?.effectiveCursor
                        val newHasMore = data.pagination?.effectiveHasMore ?: (nextCursor != null)

                        val newProfileItems = rawItems.map { it.toExploreProfileItem(tab, isLocked) }
                        val existing = persistentTabItems[tab] ?: mutableListOf()
                        val existingIds = existing.map { it.id }.toSet()
                        val uniqueNew = newProfileItems.filter { it.id !in existingIds }
                        existing.addAll(uniqueNew)
                        persistentTabItems[tab] = existing
                        persistentCursors[tab] = nextCursor
                        persistentHasMore[tab] = newHasMore

                        // Pre-fetch next page images
                        uniqueNew.forEach { item ->
                            ShimmerHelper.prefetchImage(item.imageUrl)
                        }

                        if (_currentTab.value == tab) {
                            _exploreState.value = Resource.Success(existing)
                        }
                        isFetchingNextPage = false
                    }
                    is Resource.Error -> {
                        isFetchingNextPage = false
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun markRead(item: ExploreProfileItem) {
        val interactionId = item.interactionId ?: item.id
        if (interactionId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            mainRepository.markInteractionRead(interactionId).collect { resource ->
                if (resource is Resource.Success) {
                    loadCounts()
                }
            }
        }
    }

    private fun updateUpsellAndLockState(tab: ExploreTab) {
        val isLocked = persistentIsLocked[tab] ?: false
        _isCurrentTabLocked.value = isLocked

        _upsellText.value = when {
            tab == ExploreTab.LIKES && isLocked -> "See everyone who likes you and match instantly"
            tab == ExploreTab.COMPLIMENTS && isLocked -> "Read your compliments and reply right away"
            else -> null
        }
    }
}

fun ExploreTab.toApiType(): String = when (this) {
    ExploreTab.LIKES -> "likes"
    ExploreTab.YOU_LIKED -> "you_liked"
    ExploreTab.MATCHES -> "matches"
    ExploreTab.COMPLIMENTS -> "compliments"
}
