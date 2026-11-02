package com.flirttime.feature.main.explore

import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.main.profile.UserProfileViewModel
import com.flirttime.feature.subscription.PremiumActivity
import com.flirttime.feature.subscription.PremiumStatus
import com.flirttime.feature.subscription.SubscriptionPopUp
import com.flirttime.feature.subscription.SubscriptionPopUpType
import com.flirttime.databinding.FragmentExploreBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

enum class ExploreTab {
    LIKES,
    YOU_LIKED,
    MATCHES,
    COMPLIMENTS
}

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExploreViewModel by viewModel()
    private val userProfileViewModel: UserProfileViewModel by inject()
    private var currentTab: ExploreTab = ExploreTab.YOU_LIKED
    private lateinit var profileAdapter: ExploreProfileAdapter
    private var shouldResetScrollToTop: Boolean = true

    private fun resetScrollToTop() {
        if (_binding == null) return
        binding.rvExploreProfiles.stopScroll()
        val layoutManager = binding.rvExploreProfiles.layoutManager as? GridLayoutManager
        layoutManager?.scrollToPositionWithOffset(0, 0)
        binding.rvExploreProfiles.post {
            if (_binding != null) {
                (binding.rvExploreProfiles.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeaderActions()
        setupTabs()
        setupGrid()
        setupPullToRefresh()
        setupUpsellBanner()
        setupEmptyStateRefresh()
        observeViewModel()
        updateTabSelection(ExploreTab.YOU_LIKED)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCounts()
    }

    private fun setupHeaderActions() {
        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }
    }

    private fun setupTabs() {
        binding.segmentHeader.onSelect = { tab -> updateTabSelection(tab) }
    }

    private fun setupUpsellBanner() {
        // iOS `page.onUpsellTap` → openPremium(source: tab == .compliments ? .compliment : .superLike)
        val openPremium = {
            PremiumActivity.start(
                requireContext(),
                if (currentTab == ExploreTab.COMPLIMENTS) SubscriptionPopUpType.COMPLIMENT
                else SubscriptionPopUpType.SUPER_LIKE
            )
        }
        binding.layoutUpsellBanner.setOnClickListener { openPremium() }
        binding.btnUpsellUnlock.setOnClickListener { openPremium() }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefreshExplore.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.brand_red)
        )
        binding.swipeRefreshExplore.setOnRefreshListener {
            shouldResetScrollToTop = true
            viewModel.refreshCurrentTab()
        }
    }

    private fun setupEmptyStateRefresh() {
        binding.btnRefreshExplore.setOnClickListener {
            shouldResetScrollToTop = true
            viewModel.refreshCurrentTab()
        }
    }

    private fun setupGrid() {
        profileAdapter = ExploreProfileAdapter { profile ->
            if (profile.isLocked) {
                // iOS: likes → .superLike popup, compliments → .chat popup (skipped for Premium members).
                if (!PremiumStatus.isPremium(userProfileViewModel)) {
                    SubscriptionPopUp.show(
                        requireContext(),
                        if (currentTab == ExploreTab.COMPLIMENTS) SubscriptionPopUpType.CHAT
                        else SubscriptionPopUpType.SUPER_LIKE
                    )
                }
                return@ExploreProfileAdapter
            }

            if (currentTab == ExploreTab.COMPLIMENTS) {
                viewModel.markRead(profile)
            }

            val seededMyAction = when {
                currentTab == ExploreTab.YOU_LIKED -> if (profile.isSuperLike) "super_like" else "like"
                profile.isSuperLike -> "super_like"
                else -> null
            }
            val isMatchedUser = (currentTab == ExploreTab.MATCHES) || profile.isMatched

            AppNavigator.navigateTo(
                context = requireContext(),
                destination = Destination.UserProfile(
                    userId = profile.id,
                    username = profile.username,
                    age = profile.age,
                    profilePhotoUrl = profile.imageUrl,
                    coverPhotoUrl = profile.coverUrl,
                    city = profile.location,
                    isMatched = isMatchedUser,
                    myAction = seededMyAction
                )
            )
        }

        val spacing = dpToPx(12f)
        binding.rvExploreProfiles.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = profileAdapter
            itemAnimator = null // Smooth scrolling without item flicker
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position == RecyclerView.NO_POSITION) return
                    val column = position % 2

                    if (column == 0) {
                        outRect.right = spacing / 2
                    } else {
                        outRect.left = spacing / 2
                    }
                    outRect.bottom = spacing
                }
            })

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layout = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val visibleItemCount = layout.childCount
                    val totalItemCount = layout.itemCount
                    val firstVisibleItemPosition = layout.findFirstVisibleItemPosition()

                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 4 && totalItemCount > 0) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }

        // Instant 0ms display on initial view setup if cached data exists
        val initialCached = viewModel.getCachedItems(ExploreTab.YOU_LIKED)
        if (!initialCached.isNullOrEmpty()) {
            profileAdapter.submitList(initialCached) {
                resetScrollToTop()
            }
            binding.progressBarExplore.visibility = View.GONE
            binding.layoutEmptyExplore.visibility = View.GONE
        }
    }

    fun updateTabSelection(tab: ExploreTab) {
        currentTab = tab
        viewModel.selectTab(tab)

        // Always reset scroll to top when user selects or switches tab
        shouldResetScrollToTop = true
        resetScrollToTop()

        // Instantaneous 0ms switch if cached
        val cached = viewModel.getCachedItems(tab)
        if (!cached.isNullOrEmpty()) {
            profileAdapter.submitList(cached) {
                resetScrollToTop()
                shouldResetScrollToTop = false
            }
            binding.progressBarExplore.visibility = View.GONE
            binding.layoutEmptyExplore.visibility = View.GONE
        } else {
            resetScrollToTop()
        }

        binding.segmentHeader.select(tab, animated = isResumed)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observe Profile Items State
                launch {
                    viewModel.exploreState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                if (profileAdapter.itemCount == 0) {
                                    binding.progressBarExplore.visibility = View.VISIBLE
                                    binding.layoutEmptyExplore.visibility = View.GONE
                                }
                            }
                            is Resource.Success -> {
                                binding.progressBarExplore.visibility = View.GONE
                                binding.swipeRefreshExplore.isRefreshing = false
                                val items = resource.data
                                profileAdapter.submitList(items) {
                                    if (shouldResetScrollToTop) {
                                        shouldResetScrollToTop = false
                                        resetScrollToTop()
                                    }
                                }

                                if (items.isEmpty()) {
                                    binding.layoutEmptyExplore.visibility = View.VISIBLE
                                    updateEmptyStateUI(currentTab)
                                } else {
                                    binding.layoutEmptyExplore.visibility = View.GONE
                                }
                            }
                            is Resource.Error -> {
                                binding.progressBarExplore.visibility = View.GONE
                                binding.swipeRefreshExplore.isRefreshing = false
                                if (profileAdapter.itemCount == 0) {
                                    binding.layoutEmptyExplore.visibility = View.VISIBLE
                                    updateEmptyStateUI(currentTab)
                                }
                                Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 2. Observe Pull-to-refresh state
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.swipeRefreshExplore.isRefreshing = refreshing
                    }
                }

                // 3. Observe Tab Badge Counts
                launch {
                    viewModel.badgeCounts.collect { badges ->
                        ExploreTab.entries.forEach { tab ->
                            binding.segmentHeader.setCount(tab, badges[tab] ?: 0)
                        }
                    }
                }

                // 4. Observe Upsell Banner
                launch {
                    viewModel.upsellText.collect { text ->
                        if (!text.isNullOrBlank()) {
                            binding.layoutUpsellBanner.visibility = View.VISIBLE
                            binding.tvUpsellMessage.text = text
                        } else {
                            binding.layoutUpsellBanner.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun updateEmptyStateUI(tab: ExploreTab) {
        binding.ivEmptyExploreIcon.setImageResource(tab.iconRes)
        binding.tvEmptyExploreTitle.text = tab.emptyTitle
        binding.tvEmptyExploreSubtitle.text = tab.emptyMessage
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
