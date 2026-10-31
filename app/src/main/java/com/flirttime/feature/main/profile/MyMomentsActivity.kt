package com.flirttime.feature.main.profile

import android.graphics.Color
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
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.databinding.ActivityMyMomentsBinding
import com.flirttime.feature.moments.MomentMediaViewerActivity
import com.flirttime.feature.moments.MomentPost
import com.flirttime.feature.moments.MomentsAdapter
import com.flirttime.feature.moments.MomentsRepository
import com.flirttime.feature.moments.UserMomentsResponseData
import com.flirttime.feature.moments.comments.CommentsBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.ReceivedGiftsBottomSheetDialogFragment
import com.flirttime.feature.moments.gift.SendGiftBottomSheetDialogFragment
import com.flirttime.feature.moments.likes.MomentLikesBottomSheetDialogFragment
import com.flirttime.feature.moments.options.MomentMoreOptionsBottomSheetDialogFragment
import com.flirttime.feature.moments.options.ReportMomentBottomSheetDialogFragment
import com.flirttime.feature.moments.toUiItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * "My Moments" (pushed from Profile) and another user's moments. Port of iOS `MomentsListViewController`
 * with scope `.currentUser` / `.user`:
 *  - own moments:   `GET api/v1/moments/me?page&limit=20`
 *  - other user's:  `GET api/v1/users/{id}/moments?page&limit=20`
 *  - like:          `POST api/v1/moments/{id}/like` (long press -> `GET moments/{id}/likes`)
 *  - delete/report/block, comments, gifts as in iOS.
 */
class MyMomentsActivity : AppCompatActivity() {

    private companion object {
        const val PAGE_SIZE = 20
        /** iOS reveals the scroll-to-top button once the fourth moment is on screen. */
        const val SCROLL_TO_TOP_REVEAL_ROW = 3
    }

    private lateinit var binding: ActivityMyMomentsBinding
    private val momentsRepository: MomentsRepository by inject()
    private val sessionManager: SessionManager by inject()
    private lateinit var momentsAdapter: MomentsAdapter

    private var targetUserId: String = "me"
    private var targetUserName: String = ""
    private var focusMomentId: String? = null
    private var targetUserAvatar: String? = null
    private var isTargetUserVerified: Boolean = false
    private var isCurrentUserMoments: Boolean = true

    // Signed-in user, used when a moment arrives without a creator (iOS `MomentsAuthor.currentUser`).
    private var meId = ""
    private var meName = ""
    private var meAvatar: String? = null
    private var meVerified = false
    private var meLocation = ""

    // Paging (iOS MomentsListViewModel)
    private var currentPage = 0
    private var hasMore = false
    private var isLoading = false
    private var fetchJob: Job? = null
    private val likeJobs = mutableMapOf<String, Job>()
    private var isScrollToTopVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMyMomentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parseIntentData()
        setupSystemBars()
        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            loadSessionAuthor()
            loadFirstPage(showLoader = true)
        }
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
            binding.tvEmptyDescription.text = "You haven't shared any moments yet."
        } else {
            val displayName = targetUserName.ifBlank { "User" }
            binding.tvTitle.text = "$displayName's Moments"
            binding.tvEmptyDescription.text = "$displayName hasn't shared any moments yet."
        }
    }

    private suspend fun loadSessionAuthor() {
        meId = sessionManager.getUserId()
        meName = sessionManager.getUsername().ifBlank {
            sessionManager.getNickName().ifBlank { sessionManager.getFirstName() }
        }
        meAvatar = sessionManager.getProfileImageUri().ifBlank { null }
        meVerified = sessionManager.isVerified()
        meLocation = sessionManager.getLocation()
        // A real id for "me" also lets us recognise the user's own moments in other scopes.
        if (!isCurrentUserMoments && meId.isNotBlank() && targetUserId == meId) isCurrentUserMoments = true
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

    private fun isOwn(post: MomentPost) =
        isCurrentUserMoments || post.isOwnMoment || (meId.isNotBlank() && post.userId == meId)

    private fun setupRecyclerView() {
        momentsAdapter = MomentsAdapter(
            onLikeClick = { post, position -> syncLike(post, position) },
            onLikeLongClick = { post ->
                if (post.likeCount <= 0) {
                    Toast.makeText(this, "No likes yet", Toast.LENGTH_SHORT).show()
                } else {
                    MomentLikesBottomSheetDialogFragment.newInstance(post.id)
                        .show(supportFragmentManager, MomentLikesBottomSheetDialogFragment.TAG)
                }
            },
            onMediaClick = { post, index ->
                MomentMediaViewerActivity.start(this, post.getAllMediaUrls(), index)
            },
            onCommentClick = { post ->
                val commentSheet = CommentsBottomSheetDialogFragment.newInstance(postId = post.id)
                commentSheet.onCommentPosted = {
                    post.commentCount++
                    momentsAdapter.notifyPostChanged(post.id)
                }
                commentSheet.show(supportFragmentManager, CommentsBottomSheetDialogFragment.TAG)
            },
            onGiftClick = { post ->
                if (isOwn(post)) {
                    // iOS: the owner sees who sent gifts instead of the send-gift sheet.
                    ReceivedGiftsBottomSheetDialogFragment.newInstance(momentId = post.id)
                        .show(supportFragmentManager, ReceivedGiftsBottomSheetDialogFragment.TAG)
                } else {
                    val giftSheet = SendGiftBottomSheetDialogFragment.newInstance(
                        recipientName = post.username,
                        recipientId = post.userId ?: targetUserId,
                        momentId = post.id
                    )
                    giftSheet.onGiftSent = { _, _ ->
                        post.giftCount++
                        momentsAdapter.notifyPostChanged(post.id)
                        Toast.makeText(this, "Gift sent \uD83C\uDF89", Toast.LENGTH_SHORT).show()
                    }
                    giftSheet.show(supportFragmentManager, SendGiftBottomSheetDialogFragment.TAG)
                }
            },
            onMoreOptionsClick = { post ->
                val optionsSheet = MomentMoreOptionsBottomSheetDialogFragment.newInstance(
                    momentId = post.id,
                    authorId = post.userId ?: targetUserId,
                    authorName = post.username,
                    isOwnMoment = isOwn(post)
                )
                optionsSheet.onDeleteClick = { confirmDeleteMoment(post) }
                optionsSheet.onReportClick = { showReportReasons(post) }
                optionsSheet.onBlockClick = { confirmBlockAuthor(post) }
                optionsSheet.show(supportFragmentManager, MomentMoreOptionsBottomSheetDialogFragment.TAG)
            },
            // iOS vibeCellDidTapProfile: this screen only shows one author, so their profile is "back".
            onUserClick = { finish() }
        )

        val layoutManager = LinearLayoutManager(this)
        binding.rvMoments.apply {
            this.layoutManager = layoutManager
            adapter = momentsAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val last = layoutManager.findLastVisibleItemPosition()
                    updateScrollToTop(last >= SCROLL_TO_TOP_REVEAL_ROW)
                    if (last >= momentsAdapter.currentList.size - 3) loadNextPage()
                }
            })
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.swipeRefreshMoments.setColorSchemeColors(Color.parseColor("#D92B2B"))
        binding.swipeRefreshMoments.setOnRefreshListener { loadFirstPage(showLoader = false) }

        binding.btnScrollToTop.setOnClickListener {
            val lm = binding.rvMoments.layoutManager as LinearLayoutManager
            // Jump close to the top first so very long feeds don't scroll for seconds.
            if (lm.findFirstVisibleItemPosition() > 6) binding.rvMoments.scrollToPosition(6)
            binding.rvMoments.smoothScrollToPosition(0)
        }
    }

    private fun updateScrollToTop(visible: Boolean) {
        if (visible == isScrollToTopVisible) return
        isScrollToTopVisible = visible
        val button = binding.btnScrollToTop
        if (visible) {
            button.alpha = 0f
            button.scaleX = 0.6f
            button.scaleY = 0.6f
            button.visibility = View.VISIBLE
        }
        button.animate()
            .alpha(if (visible) 1f else 0f)
            .scaleX(if (visible) 1f else 0.6f)
            .scaleY(if (visible) 1f else 0.6f)
            .setDuration(250)
            .withEndAction { if (!isScrollToTopVisible) button.visibility = View.GONE }
            .start()
    }

    // ---------------------------------------------------------------- data

    private fun loadFirstPage(showLoader: Boolean) {
        fetchJob?.cancel()
        currentPage = 0
        fetchPage(1, showLoader)
    }

    private fun loadNextPage() {
        if (!hasMore || isLoading) return
        fetchPage(currentPage + 1, showLoader = false)
    }

    private fun fetchPage(page: Int, showLoader: Boolean) {
        isLoading = true
        if (page > 1) momentsAdapter.setLoadingFooter(true)
        val source = if (isCurrentUserMoments) {
            momentsRepository.getMyMoments(page = page, limit = PAGE_SIZE)
        } else {
            momentsRepository.getUserMoments(userId = targetUserId, page = page, limit = PAGE_SIZE)
        }
        fetchJob = lifecycleScope.launch {
            source.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        if (showLoader && momentsAdapter.itemCount == 0) {
                            binding.progressBarMoments.visibility = View.VISIBLE
                        }
                    }
                    is Resource.Success -> {
                        finishLoading()
                        applyPage(resource.data, page)
                    }
                    is Resource.Error -> {
                        finishLoading()
                        Toast.makeText(this@MyMomentsActivity, resource.message ?: "Unable to load moments", Toast.LENGTH_SHORT).show()
                        updateEmptyState()
                    }
                }
            }
        }
    }

    private fun finishLoading() {
        isLoading = false
        binding.progressBarMoments.visibility = View.GONE
        binding.swipeRefreshMoments.isRefreshing = false
        momentsAdapter.setLoadingFooter(false)
    }

    private fun applyPage(data: UserMomentsResponseData, page: Int) {
        val rows = data.moments.orEmpty()
        val posts = rows.map { dto ->
            val item = if (isCurrentUserMoments) {
                dto.toUiItem(
                    fallbackName = meName,
                    fallbackAvatarUrl = meAvatar,
                    fallbackVerified = meVerified,
                    fallbackLocation = meLocation,
                    fallbackUserId = meId
                )
            } else {
                dto.toUiItem(
                    fallbackName = targetUserName,
                    fallbackAvatarUrl = targetUserAvatar,
                    fallbackVerified = isTargetUserVerified,
                    fallbackUserId = targetUserId
                )
            }
            item.copy(isOwnMoment = isCurrentUserMoments || (meId.isNotBlank() && item.userId == meId))
        }

        currentPage = page
        hasMore = if (data.page != null && data.totalPages != null) {
            data.page < data.totalPages
        } else {
            rows.size >= (data.limit ?: Int.MAX_VALUE)
        }

        if (page == 1) {
            momentsAdapter.submitList(posts)
            focusMomentId?.let { focusedId ->
                val index = posts.indexOfFirst { it.id == focusedId }
                if (index >= 0) {
                    binding.rvMoments.post {
                        (binding.rvMoments.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(index, 0)
                    }
                }
                focusMomentId = null
            }
        } else {
            momentsAdapter.appendPosts(posts)
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = momentsAdapter.currentList.isEmpty()
        binding.layoutEmptyMoments.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvMoments.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ---------------------------------------------------------------- like

    /** The adapter already applied the optimistic toggle; reconcile with the server (iOS `toggleLike`). */
    private fun syncLike(post: MomentPost, position: Int) {
        val targetLiked = post.isLiked
        likeJobs.remove(post.id)?.cancel()
        likeJobs[post.id] = lifecycleScope.launch {
            momentsRepository.toggleLike(post.id, targetLiked).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        var changed = false
                        resource.data?.effectiveIsLiked?.let { serverLiked ->
                            if (post.isLiked != serverLiked) {
                                post.isLiked = serverLiked
                                changed = true
                            }
                        }
                        resource.data?.effectiveLikeCount?.let { serverCount ->
                            if (post.likeCount != serverCount) {
                                post.likeCount = serverCount
                                changed = true
                            }
                        }
                        if (changed) refreshLike(post, position)
                    }
                    is Resource.Error -> {
                        post.isLiked = !targetLiked
                        post.likeCount = if (post.isLiked) post.likeCount + 1 else maxOf(0, post.likeCount - 1)
                        refreshLike(post, position)
                        Toast.makeText(this@MyMomentsActivity, resource.message ?: "Failed to update like", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun refreshLike(post: MomentPost, position: Int) {
        val pos = if (position != RecyclerView.NO_POSITION && position < momentsAdapter.currentList.size &&
            momentsAdapter.currentList[position].id == post.id
        ) position else momentsAdapter.findPostPosition(post.id)
        momentsAdapter.notifyLikeChanged(pos)
    }

    // ------------------------------------------------------- delete / report / block

    private fun afterRemoval() {
        updateEmptyState()
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
                                afterRemoval()
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
                            afterRemoval()
                            // iOS: leave another user's moments once none are left.
                            if (!isCurrentUserMoments && momentsAdapter.currentList.isEmpty()) finish()
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
        val authorId = post.userId
        if (authorId.isNullOrBlank()) {
            Toast.makeText(this, "This user can't be blocked right now", Toast.LENGTH_SHORT).show()
            return
        }
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
                                momentsAdapter.removePostsByAuthor(authorId)
                                afterRemoval()
                                if (!isCurrentUserMoments) finish()
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
