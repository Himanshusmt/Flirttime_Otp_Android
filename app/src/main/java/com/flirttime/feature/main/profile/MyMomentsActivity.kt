package com.flirttime.feature.main.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.databinding.ActivityMyMomentsBinding
import com.flirttime.feature.moments.MomentPost
import com.flirttime.feature.moments.MomentsAdapter
import com.flirttime.feature.moments.comments.CommentsBottomSheetDialogFragment
import com.flirttime.feature.moments.delete.DeleteMomentBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.ReceivedGiftsBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.SendGiftBottomSheetDialogFragment
import com.flirttime.feature.moments.options.MomentMoreOptionsBottomSheetDialogFragment
import com.flirttime.feature.moments.options.ReportMomentBottomSheetDialogFragment
import com.flirttime.feature.moments.toUiItem
import com.flirttime.feature.moments.MomentsRepository
import com.flirttime.feature.profile.otherUser.OtherUserActionsBottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MyMomentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyMomentsBinding
    private val momentsRepository: MomentsRepository by inject()
    private lateinit var momentsAdapter: MomentsAdapter

    private var targetUserId: String = "me"
    private var targetUserName: String = ""
    private var focusMomentId: String? = null
    private var targetUserAvatar: String? = null
    private var isTargetUserVerified: Boolean = false
    private var isCurrentUserMoments: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMyMomentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parseIntentData()
        setupSystemBars()
        setupRecyclerView()
        setupListeners()
        fetchMoments()
    }

    private fun parseIntentData() {
        targetUserId = intent.getStringExtra(AppNavigator.EXTRA_USER_ID)?.ifBlank { "me" } ?: "me"
        targetUserName = intent.getStringExtra(AppNavigator.EXTRA_USERNAME)
            ?: intent.getStringExtra(AppNavigator.EXTRA_NAME).orEmpty()
        focusMomentId = intent.getStringExtra(AppNavigator.EXTRA_FOCUS_MOMENT_ID)
        targetUserAvatar = intent.getStringExtra(AppNavigator.EXTRA_USER_AVATAR)
            ?: intent.getStringExtra(AppNavigator.EXTRA_PROFILE_PHOTO_URL)
        isTargetUserVerified = intent.getBooleanExtra(AppNavigator.EXTRA_IS_VERIFIED, false)
        isCurrentUserMoments = targetUserId.equals("me", ignoreCase = true)

        if (isCurrentUserMoments) {
            binding.tvTitle.text = "My Moments"
            binding.tvEmptyTitle.text = "No moments yet"
            binding.tvEmptyDescription.text = "Share your first moment to see it here!"
        } else {
            val displayName = targetUserName.ifBlank { "User" }
            binding.tvTitle.text = "$displayName's Moments"
            binding.tvEmptyTitle.text = "No moments yet"
            binding.tvEmptyDescription.text = "$displayName hasn't shared any moments yet."
        }
    }

    private fun setupSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMyMoments) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.layoutHeader.updatePadding(top = statusBarTop + 12)
            binding.rvMoments.updatePadding(bottom = navBarBottom + 24)
            insets
        }
        binding.rootMyMoments.requestApplyInsets()
    }

    private fun setupRecyclerView() {
        momentsAdapter = MomentsAdapter(
            onLikeClick = { post, position ->
                val targetLiked = post.isLiked
                lifecycleScope.launch {
                    momentsRepository.toggleLike(post.id, targetLiked).collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                resource.data?.let { responseData ->
                                    var changed = false
                                    responseData.effectiveIsLiked?.let { serverLiked ->
                                        if (post.isLiked != serverLiked) {
                                            post.isLiked = serverLiked
                                            changed = true
                                        }
                                    }
                                    responseData.effectiveLikeCount?.let { serverCount ->
                                        if (post.likeCount != serverCount) {
                                            post.likeCount = serverCount
                                            changed = true
                                        }
                                    }
                                    if (changed) {
                                        val pos = if (position != RecyclerView.NO_POSITION) position else momentsAdapter.findPostPosition(post.id)
                                        momentsAdapter.notifyLikeChanged(pos)
                                    }
                                }
                            }
                            is Resource.Error -> {
                                // Optimistic rollback on error
                                post.isLiked = !targetLiked
                                post.likeCount = if (post.isLiked) post.likeCount + 1 else maxOf(0, post.likeCount - 1)
                                val pos = if (position != RecyclerView.NO_POSITION) position else momentsAdapter.findPostPosition(post.id)
                                momentsAdapter.notifyLikeChanged(pos)
                                Toast.makeText(
                                    this@MyMomentsActivity,
                                    resource.message ?: "Failed to update like",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
            },
            onCommentClick = { post ->
                val commentSheet = CommentsBottomSheetDialogFragment.newInstance(postId = post.id)
                commentSheet.onCommentPosted = {
                    post.commentCount++
                    momentsAdapter.notifyDataSetChanged()
                }
                commentSheet.show(supportFragmentManager, CommentsBottomSheetDialogFragment.TAG)
            },
            onGiftClick = { post ->
                if (isCurrentUserMoments || post.isOwnMoment) {
                    val receivedGiftsSheet = ReceivedGiftsBottomSheetDialogFragment.newInstance(momentId = post.id)
                    receivedGiftsSheet.show(supportFragmentManager, ReceivedGiftsBottomSheetDialogFragment.TAG)
                } else {
                    val giftSheet = SendGiftBottomSheetDialogFragment.newInstance(
                        recipientName = post.username,
                        recipientId = post.userId ?: targetUserId,
                        momentId = post.id
                    )
                    giftSheet.onGiftSent = { _, _ ->
                        post.giftCount++
                        momentsAdapter.notifyDataSetChanged()
                    }
                    giftSheet.show(supportFragmentManager, SendGiftBottomSheetDialogFragment.TAG)
                }
            },
            onMoreOptionsClick = { post ->
                val isAuthor = isCurrentUserMoments || post.isOwnMoment
                val optionsSheet = MomentMoreOptionsBottomSheetDialogFragment.newInstance(
                    momentId = post.id,
                    authorId = post.userId ?: targetUserId,
                    authorName = post.username,
                    isOwnMoment = isAuthor
                )
                optionsSheet.onDeleteClick = {
                    confirmDeleteMoment(post)
                }
                optionsSheet.onReportClick = {
                    showReportReasons(post)
                }
                optionsSheet.onBlockClick = {
                    confirmBlockAuthor(post)
                }
                optionsSheet.show(supportFragmentManager, MomentMoreOptionsBottomSheetDialogFragment.TAG)
            }
        )

        binding.rvMoments.apply {
            layoutManager = LinearLayoutManager(this@MyMomentsActivity)
            adapter = momentsAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.swipeRefreshMoments.setOnRefreshListener {
            fetchMoments()
        }
    }

    private fun fetchMoments() {
        lifecycleScope.launch {
            momentsRepository.getUserMoments(userId = targetUserId, page = 1, limit = 20)
                .collectLatest { resource ->
                    binding.swipeRefreshMoments.isRefreshing = false
                    when (resource) {
                        is Resource.Loading -> {
                            if (momentsAdapter.itemCount == 0) {
                                binding.progressBarMoments.visibility = View.VISIBLE
                            }
                        }
                        is Resource.Success -> {
                            binding.progressBarMoments.visibility = View.GONE
                            val rawMoments = resource.data.moments.orEmpty()
                            if (rawMoments.isNotEmpty()) {
                                val uiPosts = rawMoments.map { dto ->
                                    val item = dto.toUiItem()
                                    item.copy(
                                        username = item.username.takeIf { it != "User" } ?: targetUserName.ifBlank { "User" },
                                        avatarUrl = item.avatarUrl ?: targetUserAvatar,
                                        avatarRes = if (item.avatarUrl.isNullOrBlank() && targetUserAvatar.isNullOrBlank()) {
                                            if (isCurrentUserMoments) R.drawable.img_login else R.drawable.explore_placeholder
                                        } else null,
                                        isVerified = item.isVerified || isTargetUserVerified,
                                        isOwnMoment = isCurrentUserMoments
                                    )
                                }
                                momentsAdapter.submitList(uiPosts)
                                binding.rvMoments.visibility = View.VISIBLE
                                binding.layoutEmptyMoments.visibility = View.GONE

                                focusMomentId?.let { focusedId ->
                                    val index = uiPosts.indexOfFirst { it.id == focusedId }
                                    if (index >= 0) {
                                        binding.rvMoments.post {
                                            (binding.rvMoments.layoutManager as? LinearLayoutManager)
                                                ?.scrollToPositionWithOffset(index, 0)
                                        }
                                    }
                                    focusMomentId = null
                                }
                            } else {
                                if (isCurrentUserMoments) {
                                    showPreviewMoments()
                                } else {
                                    binding.rvMoments.visibility = View.GONE
                                    binding.layoutEmptyMoments.visibility = View.VISIBLE
                                }
                            }
                        }
                        is Resource.Error -> {
                            binding.progressBarMoments.visibility = View.GONE
                            if (momentsAdapter.itemCount == 0) {
                                if (isCurrentUserMoments) {
                                    showPreviewMoments()
                                } else {
                                    binding.rvMoments.visibility = View.GONE
                                    binding.layoutEmptyMoments.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * Preview sample moments for current user if backend returns empty moments.
     */
    private fun showPreviewMoments() {
        val previewPosts = listOf(
            MomentPost(
                id = "my_moment_1",
                username = "tushar",
                avatarRes = R.drawable.img_login,
                isVerified = true,
                locationAndTime = "Mumbai · 1d",
                caption = "",
                mediaUrls = listOf(
                    "https://images.unsplash.com/photo-1534081333815-ae5019106622?w=800",
                    "https://images.unsplash.com/photo-1534081333815-ae5019106622?w=800",
                    "https://images.unsplash.com/photo-1534081333815-ae5019106622?w=800",
                    "https://images.unsplash.com/photo-1534081333815-ae5019106622?w=800",
                    "https://images.unsplash.com/photo-1534081333815-ae5019106622?w=800"
                ),
                mediaUrl = "https://images.unsplash.com/photo-1534081333815-ae5019106622?w=800",
                currentMediaIndex = 0,
                likeCount = 0,
                commentCount = 2,
                giftCount = 0,
                isOwnMoment = true
            ),
            MomentPost(
                id = "my_moment_2",
                username = "tushar",
                avatarRes = R.drawable.img_login,
                isVerified = true,
                locationAndTime = "Mumbai · 1d",
                caption = "Hii",
                mediaUrls = listOf(
                    "https://images.unsplash.com/photo-1532375810709-75b1da00537c?w=800",
                    "https://images.unsplash.com/photo-1532375810709-75b1da00537c?w=800",
                    "https://images.unsplash.com/photo-1532375810709-75b1da00537c?w=800",
                    "https://images.unsplash.com/photo-1532375810709-75b1da00537c?w=800",
                    "https://images.unsplash.com/photo-1532375810709-75b1da00537c?w=800"
                ),
                mediaUrl = "https://images.unsplash.com/photo-1532375810709-75b1da00537c?w=800",
                currentMediaIndex = 0,
                likeCount = 0,
                commentCount = 0,
                giftCount = 0,
                isOwnMoment = true
            )
        )
        momentsAdapter.submitList(previewPosts)
        binding.rvMoments.visibility = View.VISIBLE
        binding.layoutEmptyMoments.visibility = View.GONE
    }

    private fun confirmDeleteMoment(post: MomentPost) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this moment?")
            .setMessage("It will be removed for everyone. This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    momentsRepository.deleteMoment(post.id).collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                momentsAdapter.removePost(post.id)
                                Toast.makeText(this@MyMomentsActivity, "Moment deleted", Toast.LENGTH_SHORT).show()
                                if (momentsAdapter.itemCount == 0) {
                                    binding.layoutEmptyMoments.visibility = View.VISIBLE
                                    binding.rvMoments.visibility = View.GONE
                                }
                            }
                            is Resource.Error -> {
                                Toast.makeText(this@MyMomentsActivity, resource.message ?: "Unable to delete moment", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
            }
            .show()
    }

    private fun showReportReasons(post: MomentPost) {
        val reportSheet = ReportMomentBottomSheetDialogFragment.newInstance(post.id)
        reportSheet.onReasonSelected = { reasonTitle, reasonCode ->
            lifecycleScope.launch {
                momentsRepository.reportMoment(
                    momentId = post.id,
                    reason = reasonCode,
                    description = reasonTitle
                ).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            momentsAdapter.removePost(post.id)
                            Toast.makeText(this@MyMomentsActivity, "Thanks for reporting. We'll review this moment.", Toast.LENGTH_SHORT).show()
                            if (momentsAdapter.itemCount == 0) {
                                if (isCurrentUserMoments) {
                                    binding.layoutEmptyMoments.visibility = View.VISIBLE
                                    binding.rvMoments.visibility = View.GONE
                                } else {
                                    finish()
                                }
                            }
                        }
                        is Resource.Error -> {
                            Toast.makeText(this@MyMomentsActivity, resource.message ?: "Unable to report moment", Toast.LENGTH_SHORT).show()
                        }
                        is Resource.Loading -> Unit
                    }
                }
            }
        }
        reportSheet.show(supportFragmentManager, ReportMomentBottomSheetDialogFragment.TAG)
    }

    private fun confirmBlockAuthor(post: MomentPost) {
        val name = post.username.ifBlank { "this user" }
        val authorId = post.userId ?: targetUserId
        MaterialAlertDialogBuilder(this)
            .setTitle("Block $name?")
            .setMessage("They won't be able to see your profile or contact you, and you won't see their moments.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Block") { _, _ ->
                lifecycleScope.launch {
                    momentsRepository.blockUser(authorId).collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                Toast.makeText(this@MyMomentsActivity, "$name has been blocked", Toast.LENGTH_SHORT).show()
                                if (!isCurrentUserMoments) {
                                    finish()
                                } else {
                                    momentsAdapter.removePostsByAuthor(authorId)
                                }
                            }
                            is Resource.Error -> {
                                Toast.makeText(this@MyMomentsActivity, resource.message ?: "Unable to block this user", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
            }
            .show()
    }
}
