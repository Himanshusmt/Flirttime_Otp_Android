package com.flirttime.feature.main.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.main.matches.interest.InterestPersonItem
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.quickmoments.QuickMomentsTrayResponseData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class DiscoveryActionError(open val profile: MatchProfile, open val message: String) {
    data class DailyLikesExceeded(override val profile: MatchProfile, override val message: String) : DiscoveryActionError(profile, message)
    data class InsufficientCoins(override val profile: MatchProfile, override val message: String) : DiscoveryActionError(profile, message)
    data class SwipeFailed(override val profile: MatchProfile, override val message: String) : DiscoveryActionError(profile, message)
}

sealed class DiscoveryActionResult {
    data class Success(val profile: MatchProfile, val action: String, val data: DiscoveryActionResponseData) : DiscoveryActionResult()
    data class MatchCelebration(val profile: MatchProfile, val matchId: String?) : DiscoveryActionResult()
}

data class DiscoveryDeckState(
    val profiles: List<MatchProfile>,
    val isAppend: Boolean = false
)

class MatchesViewModel(
    private val matchesRepository: MatchesRepository
) : ViewModel() {

    // 1. Candidate profiles for Swipe Cards (People Tab)
    private val _peopleState = MutableStateFlow<Resource<DiscoveryDeckState>>(Resource.Loading)
    val peopleState: StateFlow<Resource<DiscoveryDeckState>> = _peopleState.asStateFlow()

    // 2. Pagination State for Cards
    private var currentPeoplePage = 1
    private var hasMorePeople = true
    private var isFetchingNextPage = false
    private val allLoadedProfiles = mutableListOf<MatchProfile>()

    // 3. Profiles for Selected Interest (Interests Tab)
    private val _interestPeopleState = MutableStateFlow<Resource<List<InterestPersonItem>>>(Resource.Loading)
    val interestPeopleState: StateFlow<Resource<List<InterestPersonItem>>> = _interestPeopleState.asStateFlow()

    private var currentInterestId: String? = null

    // 4. Quick Moments Tray for Top Stories on Matches
    private val _quickMomentsTrayState = MutableStateFlow<Resource<QuickMomentsTrayResponseData>?>(null)
    val quickMomentsTrayState: StateFlow<Resource<QuickMomentsTrayResponseData>?> = _quickMomentsTrayState.asStateFlow()

    // 5. Header Quick Info States
    private val _coinBalanceState = MutableStateFlow<Int>(40)
    val coinBalanceState: StateFlow<Int> = _coinBalanceState.asStateFlow()

    private val _notificationCountState = MutableStateFlow<Int>(0)
    val notificationCountState: StateFlow<Int> = _notificationCountState.asStateFlow()

    // 6. Active Filters
    private val _activeFilters = MutableStateFlow<DiscoveryFiltersDto?>(null)
    val activeFilters: StateFlow<DiscoveryFiltersDto?> = _activeFilters.asStateFlow()

    // 7. Action Events (Optimistic Rollback & Celebration)
    private val _actionResultEvent = MutableSharedFlow<DiscoveryActionResult>()
    val actionResultEvent: SharedFlow<DiscoveryActionResult> = _actionResultEvent.asSharedFlow()

    private val _actionErrorEvent = MutableSharedFlow<DiscoveryActionError>()
    val actionErrorEvent: SharedFlow<DiscoveryActionError> = _actionErrorEvent.asSharedFlow()

    init {
        loadInitialPeople()
        loadQuickMomentsTray()
        loadHeaderInfo()
        loadSavedFilters()
    }

    private fun loadSavedFilters() {
        viewModelScope.launch {
            matchesRepository.getDiscoveryFilters().collect { resource ->
                if (resource is Resource.Success && resource.data != null) {
                    val saved = resource.data
                    // Only apply if any filter value is actually set
                    val hasFilter = !saved.genders.isNullOrEmpty() ||
                        saved.ageMin != null ||
                        saved.ageMax != null ||
                        saved.maxDistanceKm != null ||
                        !saved.lookingFor.isNullOrEmpty() ||
                        !saved.interestIds.isNullOrEmpty() ||
                        saved.verifiedOnly == true ||
                        saved.onlineOnly == true
                    if (hasFilter) {
                        _activeFilters.value = saved
                    }
                }
            }
        }
    }

    /**
     * Loads header info: wallet coins & unread notifications.
     */
    fun loadHeaderInfo() {
        viewModelScope.launch {
            matchesRepository.getWalletBalance().collectLatest { res ->
                if (res is Resource.Success) {
                    _coinBalanceState.value = res.data.balance
                }
            }
        }
        viewModelScope.launch {
            matchesRepository.getNotificationCounts().collectLatest { res ->
                if (res is Resource.Success) {
                    _notificationCountState.value = res.data
                }
            }
        }
    }

    /**
     * Loads the Quick Moments tray for the Matches screen.
     * GET /api/v1/quick-moments/tray
     */
    fun loadQuickMomentsTray() {
        viewModelScope.launch {
            matchesRepository.getQuickMomentsTray().collect { resource ->
                _quickMomentsTrayState.value = resource
            }
        }
    }

    /**
     * Loads the first page of candidate discovery profiles.
     * Default feed: GET /api/v1/discovery/people
     * Filtered search: POST /api/v1/discovery/search
     */
    fun loadInitialPeople(forceRefresh: Boolean = false) {
        if (!forceRefresh && allLoadedProfiles.isNotEmpty()) return

        currentPeoplePage = 1
        hasMorePeople = true
        isFetchingNextPage = false
        allLoadedProfiles.clear()

        val filters = _activeFilters.value
        viewModelScope.launch {
            _peopleState.value = Resource.Loading

            if (filters != null) {
                // If custom search filters applied: use searchDiscoveryCards
                matchesRepository.searchDiscoveryCards(page = 1, limit = 10, filters = filters).collect { searchRes ->
                    when (searchRes) {
                        is Resource.Success -> {
                            val profilesList = searchRes.data.getEffectiveProfiles()
                            hasMorePeople = searchRes.data.pagination?.hasMore ?: (profilesList.size >= 10)
                            val profiles = profilesList.map { it.toMatchProfile() }
                            allLoadedProfiles.clear()
                            allLoadedProfiles.addAll(profiles)
                            _peopleState.value = Resource.Success(DiscoveryDeckState(profiles, isAppend = false))
                        }
                        is Resource.Error -> {
                            allLoadedProfiles.clear()
                            _peopleState.value = Resource.Error(searchRes.message)
                        }
                        Resource.Loading -> Unit
                    }
                }
            } else {
                // Default feed: use proven GET /api/v1/discovery/people
                loadPeopleDefault(page = 1)
            }
        }
    }

    private suspend fun loadPeopleDefault(page: Int) {
        matchesRepository.getDiscoveryPeople(page = page, limit = 10, interestId = null).collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    val users = resource.data.getEffectiveUsers()
                    hasMorePeople = resource.data.pagination?.hasMore ?: (users.size >= 10)
                    val profiles = users.map { it.toMatchProfile() }
                    allLoadedProfiles.clear()
                    allLoadedProfiles.addAll(profiles)
                    _peopleState.value = Resource.Success(DiscoveryDeckState(profiles, isAppend = false))
                }
                is Resource.Error -> {
                    if (allLoadedProfiles.isEmpty()) {
                        _peopleState.value = Resource.Error(resource.message)
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Prefetches next page of candidate profiles when < 3 cards remain in the stack.
     */
    fun loadNextPeoplePage() {
        if (!hasMorePeople || isFetchingNextPage) return

        isFetchingNextPage = true
        val nextPage = currentPeoplePage + 1
        val filters = _activeFilters.value

        viewModelScope.launch {
            if (filters != null) {
                matchesRepository.searchDiscoveryCards(page = nextPage, limit = 10, filters = filters).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val profilesList = resource.data.getEffectiveProfiles()
                            currentPeoplePage = nextPage
                            hasMorePeople = resource.data.pagination?.hasMore ?: (profilesList.size >= 10)
                            val newProfiles = profilesList.map { it.toMatchProfile() }
                            allLoadedProfiles.addAll(newProfiles)
                            _peopleState.value = Resource.Success(DiscoveryDeckState(newProfiles, isAppend = true))
                            isFetchingNextPage = false
                        }
                        is Resource.Error -> {
                            isFetchingNextPage = false
                        }
                        Resource.Loading -> Unit
                    }
                }
            } else {
                loadNextPeopleDefault(nextPage)
            }
        }
    }

    private suspend fun loadNextPeopleDefault(nextPage: Int) {
        matchesRepository.getDiscoveryPeople(page = nextPage, limit = 10, interestId = null).collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    val users = resource.data.getEffectiveUsers()
                    currentPeoplePage = nextPage
                    hasMorePeople = resource.data.pagination?.hasMore ?: (users.size >= 10)
                    val newProfiles = users.map { it.toMatchProfile() }
                    if (newProfiles.isNotEmpty()) {
                        allLoadedProfiles.addAll(newProfiles)
                        _peopleState.value = Resource.Success(DiscoveryDeckState(newProfiles, isAppend = true))
                    }
                    isFetchingNextPage = false
                }
                is Resource.Error -> {
                    isFetchingNextPage = false
                }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Loads discovery candidates filtered by a specific interest.
     * GET /api/v1/discovery/people?interestId=<INTEREST_ID>&page=1&limit=10
     */
    fun loadPeopleForInterest(interestId: String) {
        currentInterestId = interestId

        viewModelScope.launch {
            matchesRepository.getDiscoveryPeople(page = 1, limit = 10, interestId = interestId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _interestPeopleState.value = Resource.Loading
                    }
                    is Resource.Success -> {
                        val responseData = resource.data
                        val users = responseData.getEffectiveUsers()
                        val peopleItems = users.map { it.toInterestPersonItem(interestId) }
                        _interestPeopleState.value = Resource.Success(peopleItems)
                    }
                    is Resource.Error -> {
                        _interestPeopleState.value = Resource.Error(resource.message)
                    }
                }
            }
        }
    }

    /**
     * Submits a discovery action when a card is swiped or action button is tapped.
     * Actions: "like", "nope", "super_like"
     * Handles optimistic rollback, coins update, daily like quota check, and match celebrations.
     */
    fun submitAction(profile: MatchProfile, rawAction: String) {
        val normalizedAction = when (rawAction.lowercase()) {
            "like" -> "like"
            "nope" -> "nope"
            "super_like" -> "super_like"
            else -> rawAction
        }

        viewModelScope.launch {
            matchesRepository.submitDiscoveryAction(profile.id, normalizedAction).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val data = resource.data
                        // Update coins if provided
                        data.remainingCoins?.let { coins ->
                            _coinBalanceState.value = coins
                        }

                        // Check Match Celebration
                        if (data.isMatched == true) {
                            _actionResultEvent.emit(DiscoveryActionResult.MatchCelebration(profile, data.matchId))
                        }
                        _actionResultEvent.emit(DiscoveryActionResult.Success(profile, normalizedAction, data))
                    }
                    is Resource.Error -> {
                        val msg = resource.message
                        val errBody = resource.errorBody.orEmpty()

                        val isDailyLimit = resource.code == 429 ||
                                msg.contains("DAILY_LIKE_LIMIT_REACHED", ignoreCase = true) ||
                                errBody.contains("DAILY_LIKE_LIMIT_REACHED", ignoreCase = true)

                        val isInsufficientCoins = resource.code == 402 || resource.code == 403 ||
                                msg.contains("INSUFFICIENT_COINS", ignoreCase = true) ||
                                errBody.contains("INSUFFICIENT_COINS", ignoreCase = true)

                        when {
                            isDailyLimit -> {
                                _actionErrorEvent.emit(
                                    DiscoveryActionError.DailyLikesExceeded(
                                        profile,
                                        "You have reached your daily like limit. Upgrade to continue liking!"
                                    )
                                )
                            }
                            isInsufficientCoins -> {
                                _actionErrorEvent.emit(
                                    DiscoveryActionError.InsufficientCoins(
                                        profile,
                                        "You need more coins to send a Super Like!"
                                    )
                                )
                            }
                            else -> {
                                _actionErrorEvent.emit(
                                    DiscoveryActionError.SwipeFailed(profile, msg)
                                )
                            }
                        }
                    }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    /**
     * Saves and applies discovery filter preferences.
     */
    fun applyFilters(filters: DiscoveryFiltersDto) {
        _activeFilters.value = filters
        viewModelScope.launch {
            matchesRepository.saveDiscoveryFilters(filters).collect { }
        }
        loadInitialPeople(forceRefresh = true)
    }

    /**
     * Resets discovery filter preferences to defaults.
     */
    fun resetFilters() {
        _activeFilters.value = null
        viewModelScope.launch {
            matchesRepository.resetDiscoveryFilters().collect { }
        }
        loadInitialPeople(forceRefresh = true)
    }
}
