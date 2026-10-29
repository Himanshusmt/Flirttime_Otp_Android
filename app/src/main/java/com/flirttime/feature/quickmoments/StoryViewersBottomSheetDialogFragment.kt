package com.flirttime.feature.quickmoments

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.BottomSheetStoryViewersBinding
import com.flirttime.feature.main.matches.MatchesRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class StoryViewersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetStoryViewersBinding? = null
    private val binding get() = _binding!!

    private val matchesRepository: MatchesRepository by inject()
    private lateinit var viewersAdapter: StoryViewersAdapter

    private var momentId: String = ""

    override fun getTheme(): Int = R.style.TransparentBottomSheetDialogTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentId = arguments?.getString(ARG_MOMENT_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStoryViewersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewersAdapter = StoryViewersAdapter(
            onViewerClick = { targetUserId ->
                dismiss()
                AppNavigator.navigateTo(
                    requireContext(),
                    Destination.UserProfile(userId = targetUserId)
                )
            }
        )

        binding.rvStoryViewers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStoryViewers.adapter = viewersAdapter

        loadStoryViewersData()
    }

    private fun loadStoryViewersData() {
        if (momentId.isBlank()) {
            binding.pbViewersLoading.visibility = View.GONE
            binding.tvEmptyViewers.visibility = View.VISIBLE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var reactionsMap = emptyMap<String, String>()

            // 1. Fetch Reactions Breakdown & Map
            launch {
                matchesRepository.getQuickMomentReactions(momentId).collectLatest { res ->
                    if (res is Resource.Success) {
                        val breakdown = res.data.breakdown.orEmpty()
                        renderReactionsBreakdown(breakdown)

                        val rMap = mutableMapOf<String, String>()
                        res.data.reactions.orEmpty().forEach { reactionItem ->
                            val uid = reactionItem.reactor?.userId
                            val emoji = reactionItem.emoji
                            if (!uid.isNullOrBlank() && !emoji.isNullOrBlank()) {
                                rMap[uid] = emoji
                            }
                        }
                        reactionsMap = rMap
                    }
                }
            }

            // 2. Fetch Viewers List
            launch {
                matchesRepository.getQuickMomentViewers(momentId).collectLatest { res ->
                    binding.pbViewersLoading.visibility = View.GONE
                    when (res) {
                        is Resource.Success -> {
                            val viewersList = res.data.viewers.orEmpty()
                            val totalCount = res.data.totalViews ?: viewersList.size
                            binding.tvTotalViewsBadge.text = "$totalCount views"

                            if (viewersList.isEmpty()) {
                                binding.tvEmptyViewers.visibility = View.VISIBLE
                                binding.rvStoryViewers.visibility = View.GONE
                            } else {
                                binding.tvEmptyViewers.visibility = View.GONE
                                binding.rvStoryViewers.visibility = View.VISIBLE
                                viewersAdapter.submitData(viewersList, reactionsMap)
                            }
                        }
                        is Resource.Error -> {
                            binding.tvEmptyViewers.visibility = View.VISIBLE
                            binding.rvStoryViewers.visibility = View.GONE
                        }
                        is Resource.Loading -> {
                            binding.pbViewersLoading.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun renderReactionsBreakdown(breakdown: List<ReactionCountDto>) {
        if (breakdown.isEmpty()) {
            binding.scrollReactionsBreakdown.visibility = View.GONE
            return
        }

        binding.layoutReactionsBreakdown.removeAllViews()
        binding.scrollReactionsBreakdown.visibility = View.VISIBLE

        breakdown.forEach { item ->
            val pill = TextView(requireContext()).apply {
                val padH = (12 * resources.displayMetrics.density).toInt()
                val padV = (5 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
                text = "${item.emoji} ${item.count}"
                setTextColor(Color.parseColor("#1A1A1A"))
                textSize = 13f

                val bg = GradientDrawable().apply {
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(Color.parseColor("#F2F2F2"))
                }
                background = bg

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                layoutParams = lp
            }
            binding.layoutReactionsBreakdown.addView(pill)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "StoryViewersBottomSheet"
        private const val ARG_MOMENT_ID = "arg_moment_id"

        fun newInstance(momentId: String): StoryViewersBottomSheetDialogFragment {
            return StoryViewersBottomSheetDialogFragment().apply {
                arguments = bundleOf(ARG_MOMENT_ID to momentId)
            }
        }
    }
}
