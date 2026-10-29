package com.flirttime.feature.main.explore

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
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
import com.flirttime.databinding.FragmentExploreBinding
import kotlinx.coroutines.launch
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
    private var currentTab: ExploreTab = ExploreTab.YOU_LIKED
    private lateinit var profileAdapter: ExploreProfileAdapter

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
        setupEmptyStateRefresh()
        observeViewModel()
        updateTabSelection(ExploreTab.YOU_LIKED)
    }

    private fun setupHeaderActions() {
        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }
    }

    private fun setupTabs() {
        binding.tabLikes.setOnClickListener {
            updateTabSelection(ExploreTab.LIKES)
        }

        binding.tabYouLiked.setOnClickListener {
            updateTabSelection(ExploreTab.YOU_LIKED)
        }

        binding.tabMatches.setOnClickListener {
            updateTabSelection(ExploreTab.MATCHES)
        }

        binding.tabCompliments.setOnClickListener {
            updateTabSelection(ExploreTab.COMPLIMENTS)
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefreshExplore.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.brand_red)
        )
        binding.swipeRefreshExplore.setOnRefreshListener {
            viewModel.refreshCurrentTab()
        }
    }

    private fun setupEmptyStateRefresh() {
        binding.btnRefreshExplore.setOnClickListener {
            viewModel.refreshCurrentTab()
        }
    }

    private fun setupGrid() {
        profileAdapter = ExploreProfileAdapter { profile ->
            AppNavigator.navigateTo(
                context = requireContext(),
                destination = Destination.UserProfile(
                    userId = profile.id,
                    username = profile.username,
                    age = profile.age,
                    profilePhotoUrl = profile.imageUrl,
                    city = profile.location
                )
            )
        }

        val spacing = dpToPx(12f)
        binding.rvExploreProfiles.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = profileAdapter
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
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
    }

    fun updateTabSelection(tab: ExploreTab) {
        currentTab = tab
        viewModel.selectTab(tab)

        val brandRed = ContextCompat.getColor(requireContext(), R.color.brand_red)
        val mutedGray = Color.parseColor("#8E8E93")
        val activeText = Color.parseColor("#1A1A1A")
        val semiboldFont = ResourcesCompat.getFont(requireContext(), R.font.fredokasemibold)
        val mediumFont = ResourcesCompat.getFont(requireContext(), R.font.fredokamedium)

        // 1. Likes
        val isLikes = tab == ExploreTab.LIKES
        bindTabState(
            iconView = binding.ivTabLikesIcon,
            labelView = binding.tvTabLikesLabel,
            indicatorView = binding.indicatorTabLikes,
            isActive = isLikes,
            activeIconColor = brandRed,
            inactiveIconColor = mutedGray,
            activeTextColor = activeText,
            inactiveTextColor = mutedGray,
            activeFont = semiboldFont,
            inactiveFont = mediumFont
        )

        // 2. You Liked
        val isYouLiked = tab == ExploreTab.YOU_LIKED
        bindTabState(
            iconView = binding.ivTabYouLikedIcon,
            labelView = binding.tvTabYouLikedLabel,
            indicatorView = binding.indicatorTabYouLiked,
            isActive = isYouLiked,
            activeIconColor = null,
            inactiveIconColor = mutedGray,
            activeTextColor = activeText,
            inactiveTextColor = mutedGray,
            activeFont = semiboldFont,
            inactiveFont = mediumFont
        )

        // 3. Matches
        val isMatches = tab == ExploreTab.MATCHES
        bindTabState(
            iconView = binding.ivTabMatchesIcon,
            labelView = binding.tvTabMatchesLabel,
            indicatorView = binding.indicatorTabMatches,
            isActive = isMatches,
            activeIconColor = brandRed,
            inactiveIconColor = mutedGray,
            activeTextColor = activeText,
            inactiveTextColor = mutedGray,
            activeFont = semiboldFont,
            inactiveFont = mediumFont
        )

        // 4. Compliments
        val isCompliments = tab == ExploreTab.COMPLIMENTS
        bindTabState(
            iconView = binding.ivTabComplimentsIcon,
            labelView = binding.tvTabComplimentsLabel,
            indicatorView = binding.indicatorTabCompliments,
            isActive = isCompliments,
            activeIconColor = brandRed,
            inactiveIconColor = mutedGray,
            activeTextColor = activeText,
            inactiveTextColor = mutedGray,
            activeFont = semiboldFont,
            inactiveFont = mediumFont
        )
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
                                profileAdapter.submitList(items)

                                if (items.isEmpty()) {
                                    binding.layoutEmptyExplore.visibility = View.VISIBLE
                                    updateEmptyStateText(currentTab)
                                } else {
                                    binding.layoutEmptyExplore.visibility = View.GONE
                                }
                            }
                            is Resource.Error -> {
                                binding.progressBarExplore.visibility = View.GONE
                                binding.swipeRefreshExplore.isRefreshing = false
                                if (profileAdapter.itemCount == 0) {
                                    binding.layoutEmptyExplore.visibility = View.VISIBLE
                                    updateEmptyStateText(currentTab)
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
                        updateBadgeView(binding.tvLikesBadge, badges[ExploreTab.LIKES] ?: 0)
                        updateBadgeView(binding.tvYouLikedBadge, badges[ExploreTab.YOU_LIKED] ?: 0)
                        updateBadgeView(binding.tvMatchesBadge, badges[ExploreTab.MATCHES] ?: 0)
                        updateBadgeView(binding.tvComplimentsBadge, badges[ExploreTab.COMPLIMENTS] ?: 0)
                    }
                }
            }
        }
    }

    private fun updateEmptyStateText(tab: ExploreTab) {
        when (tab) {
            ExploreTab.LIKES -> {
                binding.tvEmptyExploreEmoji.text = "❤️"
                binding.tvEmptyExploreTitle.text = "No Likes Yet"
                binding.tvEmptyExploreSubtitle.text = "People who swipe right on you will appear here"
            }
            ExploreTab.YOU_LIKED -> {
                binding.tvEmptyExploreEmoji.text = "👉"
                binding.tvEmptyExploreTitle.text = "No Profiles Liked"
                binding.tvEmptyExploreSubtitle.text = "Profiles you swipe right on will be saved here"
            }
            ExploreTab.MATCHES -> {
                binding.tvEmptyExploreEmoji.text = "🎉"
                binding.tvEmptyExploreTitle.text = "No Matches Yet"
                binding.tvEmptyExploreSubtitle.text = "When you and someone like each other, you'll match here"
            }
            ExploreTab.COMPLIMENTS -> {
                binding.tvEmptyExploreEmoji.text = "💬"
                binding.tvEmptyExploreTitle.text = "No Compliments Yet"
                binding.tvEmptyExploreSubtitle.text = "Compliments you send or receive will be listed here"
            }
        }
    }

    private fun updateBadgeView(badgeView: TextView, count: Int) {
        if (count > 0) {
            badgeView.visibility = View.VISIBLE
            badgeView.text = if (count > 99) "99+" else count.toString()
        } else {
            badgeView.visibility = View.GONE
        }
    }

    private fun bindTabState(
        iconView: ImageView,
        labelView: TextView,
        indicatorView: View,
        isActive: Boolean,
        activeIconColor: Int?,
        inactiveIconColor: Int,
        activeTextColor: Int,
        inactiveTextColor: Int,
        activeFont: android.graphics.Typeface?,
        inactiveFont: android.graphics.Typeface?
    ) {
        if (isActive) {
            if (activeIconColor != null) {
                iconView.setColorFilter(activeIconColor)
            } else {
                iconView.clearColorFilter()
            }
            labelView.setTextColor(activeTextColor)
            labelView.typeface = activeFont
            indicatorView.visibility = View.VISIBLE
        } else {
            iconView.setColorFilter(inactiveIconColor)
            labelView.setTextColor(inactiveTextColor)
            labelView.typeface = inactiveFont
            indicatorView.visibility = View.INVISIBLE
        }
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
