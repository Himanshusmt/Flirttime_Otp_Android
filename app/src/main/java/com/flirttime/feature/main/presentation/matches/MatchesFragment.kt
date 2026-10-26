package com.flirttime.feature.main.presentation.matches

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.flirttime.R
import com.flirttime.databinding.FragmentMatchesBinding

import com.flirttime.feature.main.presentation.matches.filter.FiltersBottomSheetDialogFragment
import com.flirttime.feature.main.presentation.matches.interest.InterestAdapter
import com.flirttime.feature.main.presentation.matches.interest.InterestPeopleAdapter
import com.flirttime.feature.main.presentation.matches.interest.MockInterestPeopleProvider
import com.flirttime.feature.main.presentation.matches.interest.MockInterestsProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager

class MatchesFragment : Fragment(), SwipeCardStackView.OnCardSwipeListener {

    private var _binding: FragmentMatchesBinding? = null
    private val binding get() = _binding!!
    private lateinit var interestAdapter: InterestAdapter
    private lateinit var interestPeopleAdapter: InterestPeopleAdapter

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
        setupInterestsSection()
        setupBottomActions()
        setupScreenSwipeArea()
        loadMockCards()

        binding.btnNotification.setOnClickListener {
            com.flirttime.core.navigation.AppNavigator.navigateTo(
                requireContext(),
                com.flirttime.core.navigation.Destination.Notification
            )
        }

        binding.btnFilter.setOnClickListener {
            showFiltersBottomSheet()
        }
    }

    private fun setupInterestsSection() {
        // Section 1: Discover by Interests
        val interests = MockInterestsProvider.getInterests()
        val defaultInterest = interests.firstOrNull { it.isSelected } ?: interests.first()

        interestAdapter = InterestAdapter(interests) { selectedInterest ->
            binding.tvPeopleLoveTitle.text = "People who love ${selectedInterest.name}"
            interestPeopleAdapter.updateList(
                MockInterestPeopleProvider.getPeopleForInterest(selectedInterest.id)
            )
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
        interestPeopleAdapter = InterestPeopleAdapter(
            MockInterestPeopleProvider.getPeopleForInterest(defaultInterest.id)
        ) { person ->
            Toast.makeText(requireContext(), "Viewing ${person.name}", Toast.LENGTH_SHORT).show()
        }

        binding.rvPeopleWhoLove.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = interestPeopleAdapter
        }

        binding.btnSeeAllPeople.setOnClickListener {
            val currentInterestName = interestAdapter.getSelectedItem()?.name ?: "Art"
            Toast.makeText(requireContext(), "See All People who love $currentInterestName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFiltersBottomSheet() {
        val filterSheet = FiltersBottomSheetDialogFragment()
        filterSheet.onApplyFilters = { gender, minAge, maxAge, distanceKm ->
            Toast.makeText(
                requireContext(),
                "Filters applied: $gender, $minAge-$maxAge, $distanceKm km",
                Toast.LENGTH_SHORT
            ).show()
        }
        filterSheet.show(childFragmentManager, FiltersBottomSheetDialogFragment.TAG)
    }

    private fun setupCardStackView() {
        binding.cardStackView.listener = this
        binding.cardStackView.onCardClickListener = { profile ->
            // Card clicked
        }
    }

    private fun loadMockCards() {
        val mockProfiles = MockMatchProfileProvider.getMockProfiles()
        binding.cardStackView.submitList(mockProfiles)
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
        val dark = android.graphics.Color.parseColor("#262626")

        binding.pillPeople.setBackgroundResource(if (isPeople) R.drawable.bg_tab_pill_selected else R.drawable.bg_tab_pill_unselected)
        binding.pillPeople.setTextColor(if (isPeople) white else dark)

        binding.pillQuickMoments.setBackgroundResource(if (isQuickMoments) R.drawable.bg_tab_pill_selected else R.drawable.bg_tab_pill_unselected)
        binding.pillQuickMoments.setTextColor(if (isQuickMoments) white else dark)

        binding.pillInterests.setBackgroundResource(if (isInterests) R.drawable.bg_tab_pill_selected else R.drawable.bg_tab_pill_unselected)
        binding.pillInterests.setTextColor(if (isInterests) white else dark)

        if (isInterests) {
            binding.layoutPeopleContent.visibility = View.GONE
            binding.layoutInterestsContent.visibility = View.VISIBLE
        } else {
            binding.layoutPeopleContent.visibility = View.VISIBLE
            binding.layoutInterestsContent.visibility = View.GONE
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
        // Can be used for drag indicators or swipe hints if needed
    }

    override fun onCardSwiped(direction: SwipeCardStackView.SwipeDirection, profile: MatchProfile) {
        val name = profile.name

        when (direction) {
            SwipeCardStackView.SwipeDirection.RIGHT -> {
                // Right swipe (+X): Interested
                Toast.makeText(requireContext(), "Interested in $name ❤️", Toast.LENGTH_SHORT).show()
            }
            SwipeCardStackView.SwipeDirection.LEFT -> {
                // Left swipe (−X): Not Interested
                Toast.makeText(requireContext(), "Not interested in $name ✕", Toast.LENGTH_SHORT).show()
            }
            SwipeCardStackView.SwipeDirection.UP -> {
                // Up swipe (+Y visually / −Y coordinate): Super Like
                Toast.makeText(requireContext(), "Super Like on $name ⭐", Toast.LENGTH_SHORT).show()
            }
            SwipeCardStackView.SwipeDirection.DOWN -> {
                // Down swipe (−Y visually / +Y coordinate): Nope
                Toast.makeText(requireContext(), "Nope on $name 👎", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStackEmpty() {
        // Auto reload mock cards when all cards are swiped so testing is continuous
        loadMockCards()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
