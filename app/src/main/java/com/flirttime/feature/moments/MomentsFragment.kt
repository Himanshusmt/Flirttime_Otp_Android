package com.flirttime.feature.moments

import android.graphics.Color
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
import com.flirttime.R
import androidx.lifecycle.lifecycleScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentMomentsBinding
import com.flirttime.feature.moments.comments.CommentsBottomSheetDialogFragment
import com.flirttime.feature.moments.delete.DeleteMomentBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.ReceivedGiftsBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.SendGiftBottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MomentsFragment : Fragment() {

    private var _binding: FragmentMomentsBinding? = null
    private val binding get() = _binding!!

    private val momentsViewModel: MomentsViewModel by viewModel()
    private val sessionManager: SessionManager by inject()
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
        setupSwipeRefresh()
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

    private var activeFilter: String? = null

    private fun setupFilters() {
        binding.btnAddNewMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        binding.btnNearby.setOnClickListener {
            toggleFilter("nearby")
        }

        binding.btnDistance.setOnClickListener {
            toggleFilter("distance")
        }

        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }
    }

    private fun toggleFilter(filter: String) {
        activeFilter = if (activeFilter == filter) null else filter
        updateFilterPillsUi()
    }

    private fun updateFilterPillsUi() {
        val isNearbyActive = activeFilter == "nearby"
        val isDistanceActive = activeFilter == "distance"

        binding.btnNearby.setBackgroundResource(
            if (isNearbyActive) R.drawable.bg_action_pill_red else R.drawable.bg_action_pill_white
        )
        binding.tvNearby.setTextColor(
            if (isNearbyActive) Color.WHITE else Color.parseColor("#555555")
        )

        binding.btnDistance.setBackgroundResource(
            if (isDistanceActive) R.drawable.bg_action_pill_red else R.drawable.bg_action_pill_white
        )
        binding.tvDistance.setTextColor(
            if (isDistanceActive) Color.WHITE else Color.parseColor("#555555")
        )
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshMoments.setColorSchemeResources(R.color.brand_red)
        binding.swipeRefreshMoments.setOnRefreshListener {
            momentsViewModel.loadMoments(isRefresh = true)
        }
    }

    private fun setupFeed() {
        momentsAdapter = MomentsAdapter(
            onLikeClick = { post, _ ->
                momentsViewModel.toggleLike(momentId = post.id, isLiked = post.isLiked)
            },
            onCommentClick = { post ->
                val commentSheet = CommentsBottomSheetDialogFragment.newInstance(postId = post.id)
                commentSheet.onCommentPosted = {
                    post.commentCount++
                    momentsAdapter.notifyDataSetChanged()
                }
                commentSheet.show(childFragmentManager, CommentsBottomSheetDialogFragment.TAG)
            },
            onGiftClick = { post ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val currentUserId = sessionManager.getUserId()
                    val isOwn = post.isOwnMoment || (currentUserId.isNotBlank() && currentUserId == post.userId)
                    if (isOwn) {
                        val receivedGiftsSheet = ReceivedGiftsBottomSheetDialogFragment.newInstance(momentId = post.id)
                        receivedGiftsSheet.show(childFragmentManager, ReceivedGiftsBottomSheetDialogFragment.TAG)
                    } else {
                        val giftSheet = SendGiftBottomSheetDialogFragment.newInstance(
                            recipientName = post.username,
                            recipientId = post.userId ?: post.id,
                            momentId = post.id
                        )
                        giftSheet.onGiftSent = { _, _ ->
                            post.giftCount++
                            momentsAdapter.notifyDataSetChanged()
                        }
                        giftSheet.show(childFragmentManager, SendGiftBottomSheetDialogFragment.TAG)
                    }
                }
            },
            onMoreOptionsClick = { post ->
                val currentUserId = sessionManager.getCachedUserId()
                val isAuthor = post.isOwnMoment || (currentUserId.isNotBlank() && currentUserId == post.userId)
                if (isAuthor) {
                    val deleteSheet = DeleteMomentBottomSheetDialogFragment.newInstance(post.id)
                    deleteSheet.onMomentDeleted = { deletedId ->
                        momentsViewModel.removeMomentFromList(deletedId)
                        momentsAdapter.removePost(deletedId)
                    }
                    deleteSheet.show(childFragmentManager, DeleteMomentBottomSheetDialogFragment.TAG)
                } else {
                    Toast.makeText(requireContext(), "Options for ${post.username}", Toast.LENGTH_SHORT).show()
                }
            },
            onUserClick = { post ->
                val targetId = post.userId ?: post.id
                if (targetId.isNotBlank()) {
                    AppNavigator.navigateTo(
                        context = requireContext(),
                        destination = Destination.UserProfile(
                            userId = targetId,
                            username = post.username,
                            profilePhotoUrl = post.avatarUrl
                        )
                    )
                }
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
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                // Trigger next page when user is within 2 items from the end or can't scroll down further
                if ((lastVisibleItemPosition >= totalItemCount - 2 || !recyclerView.canScrollVertically(1))
                    && totalItemCount > 0
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
                // 1. Observe swipe-to-refresh spinner state
                launch {
                    momentsViewModel.isRefreshing.collect { isRefreshing ->
                        binding.swipeRefreshMoments.isRefreshing = isRefreshing
                    }
                }

                // 2. Observe feed data & initial loading/error states
                launch {
                    momentsViewModel.momentsState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                if (momentsAdapter.itemCount == 0 && !binding.swipeRefreshMoments.isRefreshing) {
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

                // 3. Observe pagination loading more (footer loader)
                launch {
                    momentsViewModel.isLoadingMore.collect { isLoadingMore ->
                        momentsAdapter.setLoadingFooter(isLoadingMore)
                    }
                }

                // 4. Observe pagination background errors
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
