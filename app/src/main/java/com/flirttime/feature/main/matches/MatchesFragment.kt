package com.flirttime.feature.main.matches

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.core.image.CropResult
import com.flirttime.core.image.ImageCropperHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.core.ui.dialog.CommonConfirmationDialog
import com.flirttime.databinding.DialogMatchCelebrationBinding
import com.flirttime.databinding.FragmentMatchesBinding
import com.flirttime.feature.main.matches.interest.InterestAdapter
import com.flirttime.feature.main.matches.interest.InterestPeopleAdapter
import com.flirttime.feature.main.matches.interest.MockInterestsProvider
import com.flirttime.feature.quickmoments.AddNewMomentBottomSheetDialogFragment
import com.flirttime.feature.quickmoments.QuickMomentsCardAdapter
import com.flirttime.feature.quickmoments.QuickMomentsViewerDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MatchesFragment : Fragment(), SwipeCardStackView.OnCardSwipeListener {

    private var _binding: FragmentMatchesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MatchesViewModel by viewModel()

    private lateinit var interestAdapter: InterestAdapter
    private lateinit var interestPeopleAdapter: InterestPeopleAdapter
    private lateinit var quickMomentsAdapter: QuickMomentsCardAdapter
    private lateinit var cropperHelper: ImageCropperHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cropperHelper = ImageCropperHelper.register(this) { result ->
            if (result is CropResult.Success) {
                Toast.makeText(requireContext(), "Uploading story...", Toast.LENGTH_SHORT).show()
                viewModel.createImageQuickMoment(requireContext(), result.croppedUri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCardStackView()
        setupPillTabs()
        setupQuickMomentsSection()
        setupInterestsSection()
        setupBottomActions()
        setupScreenSwipeArea()
        setupEmptyStateRefresh()
        observeViewModel()

        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(
                requireContext(),
                Destination.Notification
            )
        }

        binding.btnFilter.setOnClickListener {
            showFiltersBottomSheet()
        }
    }

    private fun setupQuickMomentsSection() {
        quickMomentsAdapter = QuickMomentsCardAdapter(
            onAddNewClick = {
                showAddQuickMomentBottomSheet()
            },
            onStoryClick = { storyCard ->
                val viewer = QuickMomentsViewerDialogFragment.newInstance(storyCard)
                viewer.onDeleteClick = { storyId ->
                    viewModel.deleteQuickMoment(storyId) {
                        Toast.makeText(requireContext(), "Story deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                viewer.onReactClick = { storyId, emoji ->
                    viewModel.reactToQuickMoment(storyId, emoji)
                }
                viewer.onReplyClick = { storyId, message ->
                    viewModel.replyToQuickMoment(storyId, message)
                }
                viewer.show(childFragmentManager, "StoryViewer")
            }
        )

        binding.rvQuickMomentsCards.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = quickMomentsAdapter
            setHasFixedSize(true)
        }

        binding.swipeRefreshQuickMoments.setColorSchemeResources(R.color.brand_red)
        binding.swipeRefreshQuickMoments.setOnRefreshListener {
            viewModel.loadQuickMomentsCards(forceRefresh = true)
        }
    }

        private fun showAddQuickMomentBottomSheet() {
        val sheet = AddNewMomentBottomSheetDialogFragment.newInstance()
        sheet.onMomentCreated = {
            viewModel.loadQuickMomentsCards()
        }
        sheet.show(childFragmentManager, AddNewMomentBottomSheetDialogFragment.TAG)
    }

    private fun setupEmptyStateRefresh() {
        binding.btnRefreshPeople.setOnClickListener {
            binding.layoutEmptyPeople.visibility = View.GONE
            viewModel.loadInitialPeople(forceRefresh = true)
        }
    }

    private fun setupInterestsSection() {
        // Section 1: Discover by Interests Grid
        val interests = MockInterestsProvider.getInterests()
        val defaultInterest = interests.firstOrNull { it.isSelected } ?: interests.first()

        interestAdapter = InterestAdapter(interests) { selectedInterest ->
            binding.tvPeopleLoveTitle.text = "People who love ${selectedInterest.name}"
            viewModel.loadPeopleForInterest(selectedInterest.id)
        }
        binding.rvInterests.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = interestAdapter
        }

        binding.btnSeeAllInterests.setOnClickListener {
            Toast.makeText(requireContext(), "See All Interests", Toast.LENGTH_SHORT).show()
        }

        // Section 2: People who love {Interest}
        binding.tvPeopleLoveTitle.text = "People who love ${defaultInterest.name}"
        interestPeopleAdapter = InterestPeopleAdapter(emptyList()) { person ->
            AppNavigator.navigateTo(
                context = requireContext(),
                destination = Destination.UserProfile(
                    userId = person.id,
                    name = person.name,
                    profilePhotoUrl = person.imageUrl
                )
            )
        }

        binding.rvPeopleWhoLove.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = interestPeopleAdapter
        }

        binding.btnSeeAllPeople.setOnClickListener {
            val currentInterestName = interestAdapter.getSelectedItem()?.name ?: "Cooking"
            Toast.makeText(requireContext(), "See All People who love $currentInterestName", Toast.LENGTH_SHORT).show()
        }

        // Trigger initial load for the default selected interest
        viewModel.loadPeopleForInterest(defaultInterest.id)
    }

    private fun showFiltersBottomSheet() {
        val filterSheet = FiltersBottomSheetDialogFragment.newInstance(viewModel.activeFilters.value)
        filterSheet.onApplyFilters = { filterDto ->
            viewModel.applyFilters(filterDto)
            Toast.makeText(requireContext(), "Applying search filters...", Toast.LENGTH_SHORT).show()
        }
        filterSheet.onResetFilters = {
            viewModel.resetFilters()
            Toast.makeText(requireContext(), "Filters reset to default", Toast.LENGTH_SHORT).show()
        }
        filterSheet.show(childFragmentManager, FiltersBottomSheetDialogFragment.TAG)
    }

    private fun setupCardStackView() {
        binding.cardStackView.listener = this
        binding.cardStackView.onCardClickListener = { profile ->
            AppNavigator.navigateTo(
                context = requireContext(),
                destination = Destination.UserProfile(
                    userId = profile.id,
                    username = profile.name,
                    age = profile.age,
                    profilePhotoUrl = profile.imageUrl
                )
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observe candidate dating profiles (People Tab)
                launch {
                    viewModel.peopleState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                if (!binding.cardStackView.hasCards()) {
                                    binding.progressBarPeople.visibility = View.VISIBLE
                                    binding.layoutEmptyPeople.visibility = View.GONE
                                }
                            }
                            is Resource.Success -> {
                                binding.progressBarPeople.visibility = View.GONE
                                val deckState = resource.data

                                if (deckState.isAppend) {
                                    binding.cardStackView.appendList(deckState.profiles)
                                } else {
                                    binding.cardStackView.submitList(deckState.profiles)
                                }

                                val hasCardsNow = binding.cardStackView.hasCards()
                                binding.layoutEmptyPeople.visibility = if (hasCardsNow) View.GONE else View.VISIBLE
                            }
                            is Resource.Error -> {
                                binding.progressBarPeople.visibility = View.GONE
                                if (!binding.cardStackView.hasCards()) {
                                    binding.layoutEmptyPeople.visibility = View.VISIBLE
                                }
                                Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 2. Observe Candidates filtered by interest (Interests Tab)
                launch {
                    viewModel.interestPeopleState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.progressBarInterestPeople.visibility = View.VISIBLE
                                binding.tvEmptyInterestPeople.visibility = View.GONE
                            }
                            is Resource.Success -> {
                                binding.progressBarInterestPeople.visibility = View.GONE
                                val people = resource.data
                                interestPeopleAdapter.updateList(people)
                                binding.tvEmptyInterestPeople.visibility = if (people.isEmpty()) View.VISIBLE else View.GONE
                            }
                            is Resource.Error -> {
                                binding.progressBarInterestPeople.visibility = View.GONE
                                binding.tvEmptyInterestPeople.visibility = View.VISIBLE
                            }
                        }
                    }
                }

                // 3. Observe Action Results (Celebration & Feedback)
                launch {
                    viewModel.actionResultEvent.collect { event ->
                        when (event) {
                            is DiscoveryActionResult.MatchCelebration -> {
                                showMatchCelebrationDialog(event.profile, event.matchId)
                            }
                            is DiscoveryActionResult.Success -> {
                                when (event.action) {
                                    "like" -> {
                                        if (event.data.isMatched != true) {
                                            Toast.makeText(requireContext(), "Profile Liked ❤️", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "super_like" -> {
                                        if (event.data.isMatched != true) {
                                            Toast.makeText(requireContext(), "Super Liked ⭐", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Observe Action Errors (Optimistic Card Rollback & Quota Dialogs)
                launch {
                    viewModel.actionErrorEvent.collect { errorEvent ->
                        binding.cardStackView.restoreCard(errorEvent.profile)
                        when (errorEvent) {
                            is DiscoveryActionError.DailyLikesExceeded -> {
                                CommonConfirmationDialog.show(
                                    context = requireContext(),
                                    title = "Daily Likes Limit Reached",
                                    message = errorEvent.message,
                                    positiveText = "Upgrade Now",
                                    negativeText = "Maybe Later",
                                    onPositiveClick = {
                                        Toast.makeText(requireContext(), "Redirecting to Upgrade...", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            is DiscoveryActionError.InsufficientCoins -> {
                                CommonConfirmationDialog.show(
                                    context = requireContext(),
                                    title = "Insufficient Coins",
                                    message = errorEvent.message,
                                    positiveText = "Get Coins",
                                    negativeText = "Cancel",
                                    onPositiveClick = {
                                        Toast.makeText(requireContext(), "Redirecting to Coin Shop...", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            is DiscoveryActionError.SwipeFailed -> {
                                Toast.makeText(requireContext(), errorEvent.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 5. Observe Coin Balance
                launch {
                    viewModel.coinBalanceState.collectLatest { coins ->
                        binding.tvCoinCount.text = coins.toString()
                    }
                }

                // 6. Observe Quick Moments Cards
                launch {
                    viewModel.quickMomentsCardsState.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.progressBarQuickMoments.visibility = if (quickMomentsAdapter.itemCount == 0 && !binding.swipeRefreshQuickMoments.isRefreshing) View.VISIBLE else View.GONE
                            }
                            is Resource.Success -> {
                                binding.progressBarQuickMoments.visibility = View.GONE
                                binding.swipeRefreshQuickMoments.isRefreshing = false
                                quickMomentsAdapter.submitList(resource.data)
                            }
                            is Resource.Error -> {
                                binding.progressBarQuickMoments.visibility = View.GONE
                                binding.swipeRefreshQuickMoments.isRefreshing = false
                                Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 7. Observe Quick Moment Creation State
                launch {
                    viewModel.quickMomentCreationState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> Unit
                            is Resource.Success -> {
                                Toast.makeText(requireContext(), "Quick Moment posted!", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupPillTabs() {
        binding.pillPeople.setOnClickListener {
            selectPillTab(isPeople = true, isQuickMoments = false, isInterests = false)
        }
        binding.pillQuickMoments.setOnClickListener {
            selectPillTab(isPeople = false, isQuickMoments = true, isInterests = false)
        }
        binding.pillInterests.setOnClickListener {
            selectPillTab(isPeople = false, isQuickMoments = false, isInterests = true)
        }
    }

    private fun selectPillTab(isPeople: Boolean, isQuickMoments: Boolean, isInterests: Boolean) {
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val dark = Color.parseColor("#262626")

        binding.pillPeople.setBackgroundResource(if (isPeople) R.drawable.bg_tab_pill_selected else R.drawable.bg_tab_pill_unselected)
        binding.pillPeople.setTextColor(if (isPeople) white else dark)

        binding.pillQuickMoments.setBackgroundResource(if (isQuickMoments) R.drawable.bg_tab_pill_selected else R.drawable.bg_tab_pill_unselected)
        binding.pillQuickMoments.setTextColor(if (isQuickMoments) white else dark)

        binding.pillInterests.setBackgroundResource(if (isInterests) R.drawable.bg_tab_pill_selected else R.drawable.bg_tab_pill_unselected)
        binding.pillInterests.setTextColor(if (isInterests) white else dark)

        binding.layoutPeopleContent.visibility = if (isPeople) View.VISIBLE else View.GONE
        binding.swipeRefreshQuickMoments.visibility = if (isQuickMoments) View.VISIBLE else View.GONE
        binding.layoutInterestsContent.visibility = if (isInterests) View.VISIBLE else View.GONE

        if (isQuickMoments) {
            viewModel.loadQuickMomentsCards()
        }
    }

    private fun setupBottomActions() {
        // Left: Not Interested
        binding.btnNotInterested.setOnClickListener {
            binding.cardStackView.swipe(SwipeCardStackView.SwipeDirection.LEFT)
        }

        // Right: Like / Interested
        binding.btnLike.setOnClickListener {
            binding.cardStackView.swipe(SwipeCardStackView.SwipeDirection.RIGHT)
        }

        // Up: Connect / Super Like
        binding.btnConnect.setOnClickListener {
            binding.cardStackView.swipe(SwipeCardStackView.SwipeDirection.UP)
        }
    }

    private fun setupScreenSwipeArea() {
        binding.rootMatches.setOnTouchListener { _, event ->
            if (binding.cardStackView.visibility == View.VISIBLE && binding.cardStackView.hasCards()) {
                binding.cardStackView.dispatchTouchEvent(event)
            } else {
                false
            }
        }
    }

    override fun onCardDragging(dx: Float, dy: Float, progress: Float) {
        // Handled dynamically inside SwipeCardStackView
    }

    override fun onCardSwiped(direction: SwipeCardStackView.SwipeDirection, profile: MatchProfile) {
        when (direction) {
            SwipeCardStackView.SwipeDirection.RIGHT -> {
                viewModel.submitAction(profile, "like")
            }
            SwipeCardStackView.SwipeDirection.LEFT -> {
                viewModel.submitAction(profile, "nope")
            }
            SwipeCardStackView.SwipeDirection.UP -> {
                viewModel.submitAction(profile, "super_like")
            }
            SwipeCardStackView.SwipeDirection.DOWN -> {
                viewModel.submitAction(profile, "nope")
            }
        }
    }

    private fun showMatchCelebrationDialog(profile: MatchProfile, matchId: String?) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogMatchCelebrationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialogBinding.tvMatchSubtitle.text = "You and ${profile.name} liked each other"
        ShimmerHelper.loadWithShimmer(dialogBinding.ivMatchedAvatar, profile.imageUrl)

        dialogBinding.btnSendMessage.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(requireContext(), "Opening chat with ${profile.name}...", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.btnKeepSwiping.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onNearStackEnd() {
        viewModel.loadNextPeoplePage()
    }

    override fun onStackEmpty() {
        if (!binding.cardStackView.hasCards()) {
            binding.layoutEmptyPeople.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
