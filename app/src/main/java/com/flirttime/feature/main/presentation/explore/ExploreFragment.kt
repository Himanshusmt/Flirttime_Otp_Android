package com.flirttime.feature.main.presentation.explore

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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentExploreBinding

enum class ExploreTab {
    LIKES,
    YOU_LIKED,
    MATCHES,
    COMPLIMENTS
}

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

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
        updateTabSelection(ExploreTab.YOU_LIKED)
    }

    private fun setupHeaderActions() {
        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }

        binding.btnSearch.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Search)
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

    private fun setupGrid() {
        profileAdapter = ExploreProfileAdapter { profile ->
            Toast.makeText(requireContext(), "Selected ${profile.username}", Toast.LENGTH_SHORT).show()
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
        }

        loadProfilesForTab(ExploreTab.YOU_LIKED)
    }

    fun updateTabSelection(tab: ExploreTab) {
        currentTab = tab

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
            activeIconColor = null, // uses default multi-color vector (red heart + white arrow)
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

        loadProfilesForTab(tab)
    }

    private fun loadProfilesForTab(tab: ExploreTab) {
        val allProfiles = MockExploreDataProvider.getExploreProfiles()
        when (tab) {
            ExploreTab.YOU_LIKED -> profileAdapter.submitList(allProfiles)
            ExploreTab.LIKES -> profileAdapter.submitList(allProfiles.take(4))
            ExploreTab.MATCHES -> profileAdapter.submitList(allProfiles.take(3))
            ExploreTab.COMPLIMENTS -> profileAdapter.submitList(allProfiles.take(2))
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

    fun setYouLikedBadgeCount(count: Int) {
        if (count > 0) {
            binding.tvYouLikedBadge.visibility = View.VISIBLE
            binding.tvYouLikedBadge.text = if (count > 99) "99+" else count.toString()
        } else {
            binding.tvYouLikedBadge.visibility = View.GONE
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
