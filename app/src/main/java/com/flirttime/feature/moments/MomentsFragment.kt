package com.flirttime.feature.moments

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
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentMomentsBinding
import com.flirttime.feature.moments.comments.CommentsBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.ReceivedGiftsBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.SendGiftBottomSheetDialogFragment
import com.flirttime.feature.moments.options.MomentMoreOptionsBottomSheetDialogFragment
import com.flirttime.feature.moments.options.ReportMomentBottomSheetDialogFragment
import com.flirttime.feature.notification.NotificationBadgeBinder
import com.flirttime.feature.notification.NotificationBadgeStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MomentsFragment : Fragment() {

    private var _binding: FragmentMomentsBinding? = null
    private val binding get() = _binding!!

    private val momentsViewModel: MomentsViewModel by viewModel()
    private val sessionManager: SessionManager by inject()
    private val uploadStore: MomentsUploadStore by inject()
    private val badgeStore: NotificationBadgeStore by inject()
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
        setupHeaderActions()
        setupSwipeRefresh()
        setupFeed()
        setupScrollListener()
        observeMoments()
        observeUpload()
        observeSocket()
        NotificationBadgeBinder.bind(binding.btnNotification, viewLifecycleOwner, badgeStore)
    }

    override fun onResume() {
        super.onResume()
        if (momentsAdapter.itemCount == 0) {
            momentsViewModel.loadMoments(isRefresh = false)
        }
    }

    private fun setupHeaderActions() {
        binding.btnAddMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }
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
                    momentsViewModel.markLocalEdit(post.id)
                    post.commentCount++
                    momentsAdapter.notifyCountsChanged(post.id)
                }
                commentSheet.onCommentsCountChanged = { count ->
                    momentsViewModel.markLocalEdit(post.id)
                    post.commentCount = count
                    momentsAdapter.notifyCountsChanged(post.id)
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
                            momentsViewModel.markLocalEdit(post.id)
                            post.giftCount++
                            momentsAdapter.notifyCountsChanged(post.id)
                        }
                        giftSheet.show(childFragmentManager, SendGiftBottomSheetDialogFragment.TAG)
                    }
                }
            },
            onMoreOptionsClick = { post ->
                val currentUserId = sessionManager.getCachedUserId()
                val isAuthor = post.isOwnMoment || (currentUserId.isNotBlank() && currentUserId == post.userId)
                val optionsSheet = MomentMoreOptionsBottomSheetDialogFragment.newInstance(
                    momentId = post.id,
                    authorId = post.userId,
                    authorName = post.username,
                    isOwnMoment = isAuthor
                )
                optionsSheet.onShareClick = {
                    MomentShareHelper.share(requireContext(), post)
                }
                optionsSheet.onDeleteClick = {
                    confirmDeleteMoment(post)
                }
                optionsSheet.onReportClick = {
                    showReportReasons(post)
                }
                optionsSheet.onBlockClick = {
                    confirmBlockAuthor(post)
                }
                optionsSheet.show(childFragmentManager, MomentMoreOptionsBottomSheetDialogFragment.TAG)
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

    private fun observeSocket() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                momentsViewModel.socketChange.collect { change ->
                    when (change) {
                        is MomentsViewModel.SocketListChange.Counts -> {
                            momentsAdapter.notifyCountsChanged(change.momentId)
                        }
                        is MomentsViewModel.SocketListChange.Removed -> {
                            dismissCommentsIfOpen(change.momentId)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun dismissCommentsIfOpen(momentId: String) {
        val sheet = childFragmentManager.findFragmentByTag(CommentsBottomSheetDialogFragment.TAG)
            as? CommentsBottomSheetDialogFragment
        if (sheet?.isForMoment(momentId) == true) sheet.dismiss()
    }

    private fun observeUpload() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    uploadStore.progress.collect { progress ->
                        if (progress == null) {
                            hideUploadingBar()
                        } else {
                            showUploadingBar(progress)
                        }
                    }
                }
                launch {
                    uploadStore.finished.collect { result ->
                        when (result) {
                            is Resource.Success -> {
                                momentsViewModel.loadMoments(isRefresh = true)
                                Toast.makeText(requireContext(), "Your moment is live", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                Toast.makeText(requireContext(), result.message ?: "Unable to post your moment", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun showUploadingBar(progress: Float) {
        binding.layoutUploadProgress.visibility = View.VISIBLE
        binding.layoutUploadProgress.alpha = 1f
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        binding.tvUploadPercent.text = "$percent%"
        binding.progressUploadMoment.progress = percent
    }

    private fun hideUploadingBar() {
        if (binding.layoutUploadProgress.visibility != View.VISIBLE) return
        binding.layoutUploadProgress.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.layoutUploadProgress.visibility = View.GONE
                binding.layoutUploadProgress.alpha = 1f
            }
            .start()
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

    private fun confirmDeleteMoment(post: MomentPost) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete this moment?")
            .setMessage("It will be removed for everyone. This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                momentsViewModel.deleteMoment(post.id) { success, errorMsg ->
                    if (success) {
                        momentsAdapter.removePost(post.id)
                        Toast.makeText(requireContext(), "Moment deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), errorMsg ?: "Unable to delete moment", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showReportReasons(post: MomentPost) {
        val reportSheet = ReportMomentBottomSheetDialogFragment.newInstance(post.id)
        reportSheet.onReasonSelected = { reasonTitle, reasonCode ->
            momentsViewModel.reportMoment(
                momentId = post.id,
                reason = reasonCode,
                description = reasonTitle
            ) { success, errorMsg ->
                if (success) {
                    momentsAdapter.removePost(post.id)
                    Toast.makeText(requireContext(), "Thanks for reporting. We'll review this moment.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), errorMsg ?: "Unable to report moment", Toast.LENGTH_SHORT).show()
                }
            }
        }
        reportSheet.show(childFragmentManager, ReportMomentBottomSheetDialogFragment.TAG)
    }

    private fun confirmBlockAuthor(post: MomentPost) {
        val name = post.username.ifBlank { "this user" }
        val authorId = post.userId ?: post.id
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Block $name?")
            .setMessage("They won't be able to see your profile or contact you, and you won't see their moments.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Block") { _, _ ->
                momentsViewModel.blockUser(authorId) { success, errorMsg ->
                    if (success) {
                        momentsAdapter.removePostsByAuthor(authorId)
                        Toast.makeText(requireContext(), "$name has been blocked", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), errorMsg ?: "Unable to block this user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
