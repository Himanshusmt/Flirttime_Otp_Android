package com.flirttime.feature.main.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.main.explore.ExploreProfileItem
import com.flirttime.feature.main.explore.toExploreProfileItem
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

    private val _currentTab = MutableStateFlow(ExploreTab.YOU_LIKED)
    val currentTab: StateFlow<ExploreTab> = _currentTab.asStateFlow()

    private val _exploreState = MutableStateFlow<Resource<List<ExploreProfileItem>>>(Resource.Loading)
    val exploreState: StateFlow<Resource<List<ExploreProfileItem>>> = _exploreState.asStateFlow()

    private val _isCurrentTabLocked = MutableStateFlow(false)
    val isCurrentTabLocked: StateFlow<Boolean> = _isCurrentTabLocked.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _badgeCounts = MutableStateFlow<Map<ExploreTab, Int>>(emptyMap())
    val badgeCounts: StateFlow<Map<ExploreTab, Int>> = _badgeCounts.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    // In-memory cache per tab for snappy tab switching & cursor pagination
    private val tabItems = mutableMapOf<ExploreTab, MutableList<ExploreProfileItem>>()
    private val tabCursors = mutableMapOf<ExploreTab, String?>()
    private val tabHasMore = mutableMapOf<ExploreTab, Boolean>()
    private val tabIsLocked = mutableMapOf<ExploreTab, Boolean>()
    private var isFetchingNextPage = false

    init {
        // Initial load for default tab
        loadTab(ExploreTab.YOU_LIKED)
        // Background fetch badge counts for other tabs
        fetchInitialBadgeCounts()
    }

    fun selectTab(tab: ExploreTab) {
        if (_currentTab.value == tab && tabItems[tab] != null) return
        _currentTab.value = tab

        val cached = tabItems[tab]
        if (cached != null) {
            _isCurrentTabLocked.value = tabIsLocked[tab] ?: false
            _exploreState.value = Resource.Success(cached)
        } else {
            loadTab(tab)
        }
    }

    fun refreshCurrentTab() {
        val tab = _currentTab.value
        tabItems.remove(tab)
        tabCursors.remove(tab)
        tabHasMore.remove(tab)
        tabIsLocked.remove(tab)
        loadTab(tab, isPullToRefresh = true)
    }

    fun loadTab(tab: ExploreTab, isPullToRefresh: Boolean = false) {
        val apiType = tab.toApiType()
        if (isPullToRefresh) {
            _isRefreshing.value = true
        } else if (tabItems[tab].isNullOrEmpty()) {
            _exploreState.value = Resource.Loading
        }

        viewModelScope.launch {
            mainRepository.getInteractions(type = apiType, limit = 20, cursor = null).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val data = resource.data
                        val isLocked = data.isLocked ?: false
                        val count = data.totalCount ?: 0
                        val rawItems = data.items.orEmpty()
                        val nextCursor = data.pagination?.nextCursor
                        val hasMore = data.pagination?.hasMore ?: (nextCursor != null)

                        // Update badges
                        val currentBadges = _badgeCounts.value.toMutableMap()
                        currentBadges[tab] = count
                        _badgeCounts.value = currentBadges

                        val profileItems = rawItems.map { it.toExploreProfileItem(isLocked) }.toMutableList()
                        tabItems[tab] = profileItems
                        tabCursors[tab] = nextCursor
                        tabHasMore[tab] = hasMore
                        tabIsLocked[tab] = isLocked

                        if (_currentTab.value == tab) {
                            _isCurrentTabLocked.value = isLocked
                            _exploreState.value = Resource.Success(profileItems)
                        }
                        _isRefreshing.value = false
                    }
                    is Resource.Error -> {
                        _isRefreshing.value = false
                        if (_currentTab.value == tab && tabItems[tab].isNullOrEmpty()) {
                            _exploreState.value = Resource.Error(resource.message)
                        }
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun loadNextPage() {
        val tab = _currentTab.value
        val cursor = tabCursors[tab]
        val hasMore = tabHasMore[tab] ?: false

        if (!hasMore || cursor.isNullOrBlank() || isFetchingNextPage) return

        isFetchingNextPage = true
        val apiType = tab.toApiType()

        viewModelScope.launch {
            mainRepository.getInteractions(type = apiType, limit = 20, cursor = cursor).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val data = resource.data
                        val isLocked = data.isLocked ?: false
                        val rawItems = data.items.orEmpty()
                        val nextCursor = data.pagination?.nextCursor
                        val newHasMore = data.pagination?.hasMore ?: (nextCursor != null)

                        val newProfileItems = rawItems.map { it.toExploreProfileItem(isLocked) }
                        val existing = tabItems[tab] ?: mutableListOf()
                        existing.addAll(newProfileItems)
                        tabItems[tab] = existing
                        tabCursors[tab] = nextCursor
                        tabHasMore[tab] = newHasMore

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

    private fun fetchInitialBadgeCounts() {
        viewModelScope.launch {
            val tabsToFetch = listOf(ExploreTab.LIKES, ExploreTab.MATCHES, ExploreTab.COMPLIMENTS)
            tabsToFetch.forEach { tab ->
                mainRepository.getInteractions(type = tab.toApiType(), limit = 1, cursor = null).collect { resource ->
                    if (resource is Resource.Success) {
                        val count = resource.data.totalCount ?: 0
                        val currentBadges = _badgeCounts.value.toMutableMap()
                        currentBadges[tab] = count
                        _badgeCounts.value = currentBadges
                    }
                }
            }
        }
    }
}

fun ExploreTab.toApiType(): String = when (this) {
    ExploreTab.LIKES -> "likes"
    ExploreTab.YOU_LIKED -> "you_liked"
    ExploreTab.MATCHES -> "matches"
    ExploreTab.COMPLIMENTS -> "compliments"
}
