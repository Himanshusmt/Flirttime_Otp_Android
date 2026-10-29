package com.flirttime.feature.main.explore

import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
import com.flirttime.databinding.DialogCommonConfirmationBinding
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

    private fun setupUpsellBanner() {
        binding.layoutUpsellBanner.setOnClickListener {
            showUnlockPremiumDialog()
        }
        binding.btnUpsellUnlock.setOnClickListener {
            showUnlockPremiumDialog()
        }
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
                // Matches iOS behavior: locked cards show unlock subscription prompt
                showUnlockPremiumDialog(
                    if (currentTab == ExploreTab.COMPLIMENTS) {
                        "Read your compliments and reply right away with FlirtTime Premium!"
                    } else {
                        "See everyone who likes you and match instantly with FlirtTime Premium!"
                    }
                )
                return@ExploreProfileAdapter
            }

            if (currentTab == ExploreTab.COMPLIMENTS) {
                viewModel.markRead(profile)
            }

            AppNavigator.navigateTo(
                context = requireContext(),
                destination = Destination.UserProfile(
                    userId = profile.id,
                    username = profile.username,
                    age = profile.age,
                    profilePhotoUrl = profile.imageUrl,
                    coverPhotoUrl = profile.coverUrl,
                    city = profile.location
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

        val brandRed = ContextCompat.getColor(requireContext(), R.color.brand_red)
        val mutedGray = Color.parseColor("#8E8E93")
        val activeText = Color.parseColor("#1A1A1A")
        val semiboldFont = ResourcesCompat.getFont(requireContext(), R.font.fredokasemibold)
        val mediumFont = ResourcesCompat.getFont(requireContext(), R.font.fredokamedium)

        // 1. Likes Tab
        val isLikes = tab == ExploreTab.LIKES
        binding.ivTabLikesIcon.setImageResource(R.drawable.ic_tab_likes)
        binding.ivTabLikesIcon.setColorFilter(if (isLikes) brandRed else mutedGray)
        binding.tvTabLikesLabel.setTextColor(if (isLikes) activeText else mutedGray)
        binding.tvTabLikesLabel.typeface = if (isLikes) semiboldFont else mediumFont
        binding.indicatorTabLikes.visibility = if (isLikes) View.VISIBLE else View.INVISIBLE

        // 2. You Liked Tab (Uses dedicated active/inactive drawables to preserve the white arrow)
        val isYouLiked = tab == ExploreTab.YOU_LIKED
        binding.ivTabYouLikedIcon.clearColorFilter()
        binding.ivTabYouLikedIcon.setImageResource(
            if (isYouLiked) R.drawable.ic_tab_you_liked_active else R.drawable.ic_tab_you_liked_inactive
        )
        binding.tvTabYouLikedLabel.setTextColor(if (isYouLiked) activeText else mutedGray)
        binding.tvTabYouLikedLabel.typeface = if (isYouLiked) semiboldFont else mediumFont
        binding.indicatorTabYouLiked.visibility = if (isYouLiked) View.VISIBLE else View.INVISIBLE

        // 3. Matches Tab
        val isMatches = tab == ExploreTab.MATCHES
        binding.ivTabMatchesIcon.setImageResource(R.drawable.ic_tab_matches)
        binding.ivTabMatchesIcon.setColorFilter(if (isMatches) brandRed else mutedGray)
        binding.tvTabMatchesLabel.setTextColor(if (isMatches) activeText else mutedGray)
        binding.tvTabMatchesLabel.typeface = if (isMatches) semiboldFont else mediumFont
        binding.indicatorTabMatches.visibility = if (isMatches) View.VISIBLE else View.INVISIBLE

        // 4. Compliments Tab
        val isCompliments = tab == ExploreTab.COMPLIMENTS
        binding.ivTabComplimentsIcon.setImageResource(R.drawable.ic_tab_compliments)
        binding.ivTabComplimentsIcon.setColorFilter(if (isCompliments) brandRed else mutedGray)
        binding.tvTabComplimentsLabel.setTextColor(if (isCompliments) activeText else mutedGray)
        binding.tvTabComplimentsLabel.typeface = if (isCompliments) semiboldFont else mediumFont
        binding.indicatorTabCompliments.visibility = if (isCompliments) View.VISIBLE else View.INVISIBLE
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
                        updateBadgeView(binding.tvLikesBadge, badges[ExploreTab.LIKES] ?: 0)
                        updateBadgeView(binding.tvYouLikedBadge, badges[ExploreTab.YOU_LIKED] ?: 0)
                        updateBadgeView(binding.tvMatchesBadge, badges[ExploreTab.MATCHES] ?: 0)
                        updateBadgeView(binding.tvComplimentsBadge, badges[ExploreTab.COMPLIMENTS] ?: 0)
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
        val brandRed = ContextCompat.getColor(requireContext(), R.color.brand_red)
        when (tab) {
            ExploreTab.LIKES -> {
                binding.ivEmptyExploreIcon.setImageResource(R.drawable.ic_tab_likes)
                binding.ivEmptyExploreIcon.setColorFilter(brandRed)
                binding.tvEmptyExploreTitle.text = "No likes yet"
                binding.tvEmptyExploreSubtitle.text = "Keep your profile fresh. People who like you will show up here."
            }
            ExploreTab.YOU_LIKED -> {
                binding.ivEmptyExploreIcon.clearColorFilter()
                binding.ivEmptyExploreIcon.setImageResource(R.drawable.ic_tab_you_liked_active)
                binding.tvEmptyExploreTitle.text = "You haven't liked anyone yet"
                binding.tvEmptyExploreSubtitle.text = "Start swiping on Discover and the people you like will appear here."
            }
            ExploreTab.MATCHES -> {
                binding.ivEmptyExploreIcon.setImageResource(R.drawable.ic_tab_matches)
                binding.ivEmptyExploreIcon.setColorFilter(brandRed)
                binding.tvEmptyExploreTitle.text = "No matches yet"
                binding.tvEmptyExploreSubtitle.text = "When you and someone like each other, it's a match. They'll appear here."
            }
            ExploreTab.COMPLIMENTS -> {
                binding.ivEmptyExploreIcon.setImageResource(R.drawable.ic_tab_compliments)
                binding.ivEmptyExploreIcon.setColorFilter(brandRed)
                binding.tvEmptyExploreTitle.text = "No compliments yet"
                binding.tvEmptyExploreSubtitle.text = "Sweet words from your admirers will land here."
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

    private fun showUnlockPremiumDialog(customMessage: String? = null) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogCommonConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.tvDialogTitle.text = "Unlock FlirtTime Premium"
        dialogBinding.tvDialogMessage.visibility = View.VISIBLE
        dialogBinding.tvDialogMessage.text = customMessage
            ?: "See everyone who liked your profile, send unlimited compliments, and connect instantly!"

        dialogBinding.btnPositive.text = "Unlock Now"
        dialogBinding.btnNegative.text = "Maybe Later"

        dialogBinding.btnPositive.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "Opening Premium Plans...", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.btnNegative.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
