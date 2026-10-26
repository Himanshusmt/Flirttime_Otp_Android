package com.flirttime.feature.main.presentation.moments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentMomentsBinding
import com.flirttime.feature.main.presentation.moments.comments.CommentsBottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MomentsFragment : Fragment() {

    private var _binding: FragmentMomentsBinding? = null
    private val binding get() = _binding!!

    private val momentsViewModel: MomentsViewModel by viewModel()
    private lateinit var momentsAdapter: MomentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMomentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFilters()
        setupFeed()
        setupScrollListener()
        observeMoments()
    }

    override fun onResume() {
        super.onResume()
        if (momentsAdapter.itemCount == 0) {
            momentsViewModel.loadMoments(isRefresh = false)
        }
    }

    private fun setupFilters() {
        binding.btnAddNewMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        binding.btnNearby.setOnClickListener {
            Toast.makeText(requireContext(), "Refreshing nearby moments...", Toast.LENGTH_SHORT).show()
            momentsViewModel.loadMoments(isRefresh = true)
        }

        binding.btnDistance.setOnClickListener {
            Toast.makeText(requireContext(), "Refreshing moments feed...", Toast.LENGTH_SHORT).show()
            momentsViewModel.loadMoments(isRefresh = true)
        }

        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }
    }

    private fun setupFeed() {
        momentsAdapter = MomentsAdapter(
            onLikeClick = { post, _ ->
                momentsViewModel.toggleLike(momentId = post.id, isLiked = post.isLiked)
            },
            onCommentClick = { post ->
                CommentsBottomSheetDialogFragment.newInstance(postId = post.id)
                    .show(childFragmentManager, CommentsBottomSheetDialogFragment.TAG)
            },
            onGiftClick = { post ->
                val giftSheet = com.flirttime.feature.main.presentation.moments.gift.SendGiftBottomSheetDialogFragment.newInstance(
                    recipientName = post.username,
                    recipientId = post.id
                )
                giftSheet.onGiftSent = { _, _ ->
                    post.giftCount++
                    momentsAdapter.notifyDataSetChanged()
                }
                giftSheet.show(
                    childFragmentManager,
                    com.flirttime.feature.main.presentation.moments.gift.SendGiftBottomSheetDialogFragment.TAG
                )
            },
            onMoreOptionsClick = { post ->
                Toast.makeText(requireContext(), "Options for ${post.username}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvMoments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = momentsAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupScrollListener() {
        binding.rvMoments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return // Only handle downward scroll

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Trigger next page when user is within 2 items from the end of the list
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 2
                    && firstVisibleItemPosition >= 0
                    && momentsViewModel.canLoadMore
                ) {
                    momentsViewModel.loadNextPage()
                }
            }
        })
    }

    private fun observeMoments() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Observe feed data & initial loading/error states
                launch {
                    momentsViewModel.momentsState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                if (momentsAdapter.itemCount == 0) {
                                    binding.progressBarMoments.visibility = View.VISIBLE
                                    binding.layoutEmptyMoments.visibility = View.GONE
                                }
                            }
                            is Resource.Success -> {
                                binding.progressBarMoments.visibility = View.GONE
                                updateMomentsUi(resource.data)
                            }
                            is Resource.Error -> {
                                binding.progressBarMoments.visibility = View.GONE
                                if (momentsAdapter.itemCount == 0) {
                                    binding.layoutEmptyMoments.visibility = View.VISIBLE
                                    binding.tvEmptyTitle.text = "Couldn't load moments"
                                    binding.tvEmptySubtitle.text = resource.message ?: "Please check your network and try again"
                                } else {
                                    Toast.makeText(requireContext(), resource.message ?: "Failed to refresh moments", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                // 2. Observe pagination loading more (footer loader)
                launch {
                    momentsViewModel.isLoadingMore.collect { isLoadingMore ->
                        momentsAdapter.setLoadingFooter(isLoadingMore)
                    }
                }

                // 3. Observe pagination background errors
                launch {
                    momentsViewModel.errorMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateMomentsUi(moments: List<MomentPost>) {
        momentsAdapter.submitList(moments)
        val isEmpty = moments.isEmpty()
        binding.rvMoments.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.layoutEmptyMoments.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            binding.tvEmptyTitle.text = "No moments yet"
            binding.tvEmptySubtitle.text = "Be the first to share what's on your mind!"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
