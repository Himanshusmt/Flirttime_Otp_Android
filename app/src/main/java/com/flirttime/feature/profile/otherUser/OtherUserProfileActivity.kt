package com.flirttime.feature.profile.otherUser

import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.FastImageHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityOtherUserProfileBinding
import com.flirttime.databinding.ItemOtherUserInterestBinding
import com.flirttime.databinding.ItemOtherUserMoreAboutMeRowBinding
import com.flirttime.feature.profile.AttributesCatalog
import com.flirttime.feature.profile.MoreAboutMeItemDto
import com.flirttime.feature.profile.UserInterestDto
import com.flirttime.feature.profile.UserSearchItemDto
import com.flirttime.feature.main.profile.UserProfileViewModel
import com.flirttime.feature.subscription.PremiumStatus
import com.flirttime.feature.subscription.SubscriptionPopUp
import com.flirttime.feature.subscription.SubscriptionPopUpType
import org.koin.android.ext.android.inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class OtherUserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtherUserProfileBinding
    private val viewModel: OtherUserProfileViewModel by viewModel()
    private val userProfileViewModel: UserProfileViewModel by inject()

    private val photosAdapter by lazy {
        OtherUserPhotosAdapter { _, pos ->
            openPhotoViewer(pos)
        }
    }
    private var currentPhotoUrls: List<String> = emptyList()

    private val momentsAdapter by lazy {
        OtherUserMomentsAdapter(
            onMomentClick = { moment ->
                openMoments(focusMomentId = moment.id)
            },
            onSeeAllClick = {
                openMoments(focusMomentId = null)
            }
        )
    }

    private var targetUserId: String = ""
    private var currentUserName: String = "User"
    private var currentUserAvatar: String? = null
    private var isCurrentUserVerified: Boolean = false
    private var isMatchedWithUser: Boolean = false
    private var currentMyAction: String? = null
    private var actionBeforeOptimistic: String? = null

    // Media Player for Voice Intro
    private var mediaPlayer: MediaPlayer? = null
    private var isVoicePlaying: Boolean = false
    private var voiceProgressJob: Job? = null
    private var currentVoiceUrl: String? = null
    private var currentVoiceDurationSec: Int = 0
    private var pendingReaction: MomentsReaction? = null
    private var lastConnectionReaction: MomentsReaction? = null
    private val partialReactionButtons = mutableMapOf<MomentsReaction.Target, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentEdgeToEdge()
        binding = ActivityOtherUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupClickListeners()
        setupPartialReactions()
        setupInsets()
        observeViewModel()
        parseIntentData()
    }

    private fun setupTransparentEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    private fun parseIntentData() {
        targetUserId = intent.getStringExtra(AppNavigator.EXTRA_USER_ID).orEmpty()
        val username = intent.getStringExtra(AppNavigator.EXTRA_USERNAME)
        val name = intent.getStringExtra(AppNavigator.EXTRA_NAME)
        val age = intent.getIntExtra(AppNavigator.EXTRA_AGE, 0).takeIf { it > 0 }
        val photoUrl = intent.getStringExtra(AppNavigator.EXTRA_PROFILE_PHOTO_URL)
        val coverUrl = intent.getStringExtra(AppNavigator.EXTRA_COVER_PHOTO_URL)
        val city = intent.getStringExtra(AppNavigator.EXTRA_CITY)
        val bio = intent.getStringExtra(AppNavigator.EXTRA_BIO)
        currentUserAvatar = photoUrl
        isMatchedWithUser = intent.getBooleanExtra(AppNavigator.EXTRA_IS_MATCHED, false)
        currentMyAction = intent.getStringExtra(AppNavigator.EXTRA_MY_ACTION)
        updateActionBar()

        val initialData = if (targetUserId.isNotEmpty() && (!username.isNullOrEmpty() || !name.isNullOrEmpty())) {
            UserSearchItemDto(
                id = targetUserId,
                userId = targetUserId,
                username = username,
                name = name,
                age = age,
                profilePhotoUrl = photoUrl,
                coverPhotoUrl = coverUrl?.takeIf { it.isNotBlank() } ?: photoUrl,
                city = city,
                about = bio,
                isMatched = isMatchedWithUser,
                myAction = currentMyAction
            )
        } else null

        if (initialData != null) {
            bindUserToUi(initialData)
        }

        if (targetUserId.isNotEmpty()) {
            viewModel.loadProfile(userId = targetUserId, initialData = initialData)
        } else {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupAdapters() {
        binding.rvPhotos.apply {
            layoutManager = LinearLayoutManager(this@OtherUserProfileActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = photosAdapter
        }

        binding.rvMoments.apply {
            layoutManager = GridLayoutManager(this@OtherUserProfileActivity, 3)
            adapter = momentsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMore.setOnClickListener {
            showActionSheet()
        }

        binding.btnNope.setOnClickListener {
            animateButtonClick(it)
            viewModel.submitAction(targetUserId, "nope")
        }

        binding.btnSuperLike.setOnClickListener {
            animateButtonClick(it)
            actionBeforeOptimistic = currentMyAction
            currentMyAction = "super_like"
            updateActionBar()
            viewModel.submitAction(targetUserId, "super_like")
        }

        binding.btnLike.setOnClickListener {
            animateButtonClick(it)
            showConnectBecauseSheet()
        }

        binding.btnChat.setOnClickListener {
            animateButtonClick(it)
            AppNavigator.navigateTo(
                context = this@OtherUserProfileActivity,
                destination = Destination.Main(targetTab = 3)
            )
        }

        binding.btnSeeAllMoments.setOnClickListener {
            openMoments(focusMomentId = null)
        }

        binding.ivAvatar.setOnClickListener {
            openPhotoViewer(0)
        }
        binding.ivCoverPhoto.setOnClickListener {
            openPhotoViewer(0)
        }
    }

    private fun openPhotoViewer(startIndex: Int) {
        if (currentPhotoUrls.isEmpty()) return
        com.flirttime.feature.moments.MomentMediaViewerActivity.start(
            this,
            currentPhotoUrls,
            startIndex.coerceIn(0, currentPhotoUrls.lastIndex)
        )
    }

    private fun setupPartialReactions() {
        addPartialReactionAffordance(
            host = binding.coverContainer,
            target = MomentsReaction.Target.Photo(0),
            overlay = true
        )
        addPartialReactionAffordance(
            host = binding.cardVoiceIntro,
            target = MomentsReaction.Target.Voice,
            overlay = false
        )
        addPartialReactionAffordance(
            host = binding.layoutMomentsHeader,
            target = MomentsReaction.Target.Moments,
            overlay = false
        )
        partialReactionButtons[MomentsReaction.Target.Voice]?.visibility = View.GONE
    }

    private fun addPartialReactionAffordance(
        host: ViewGroup,
        target: MomentsReaction.Target,
        overlay: Boolean
    ) {
        val density = resources.displayMetrics.density
        val size = (34 * density).toInt()
        val margin = (10 * density).toInt()
        val button = ImageView(this).apply {
            setImageResource(R.drawable.ic_heart_action_white)
            background = ContextCompat.getDrawable(context, R.drawable.bg_translucent_action_circle)
            val pad = (7 * density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = "Like this part of the profile"
            setOnClickListener { togglePartialReaction(MomentsReaction(target)) }
        }
        if (overlay && host is FrameLayout) {
            button.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(margin, margin, margin, margin)
            }
            host.addView(button)
        } else if (host is LinearLayout && host.orientation == LinearLayout.HORIZONTAL) {
            host.addView(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                }
            )
            host.addView(button, LinearLayout.LayoutParams(size, size))
        } else {
            host.addView(button, ViewGroup.LayoutParams(size, size))
        }
        partialReactionButtons[target] = button
    }

    private fun togglePartialReaction(reaction: MomentsReaction) {
        pendingReaction = if (pendingReaction?.target == reaction.target) null else reaction
        updatePartialReactionVisuals()
    }

    private fun updatePartialReactionVisuals() {
        partialReactionButtons.forEach { (target, view) ->
            val selected = pendingReaction?.target == target
            if (selected) {
                view.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary))
            } else {
                view.clearColorFilter()
            }
            view.alpha = if (selected) 1f else 0.88f
        }
    }

    private fun clearPartialReactionSelection() {
        pendingReaction = null
        updatePartialReactionVisuals()
    }

    private fun showConnectBecauseSheet() {
        val profile = (viewModel.profileState.value as? Resource.Success)?.data
        val sharedCount = profile?.sharedInterests?.size ?: 0
        val hasMoments = viewModel.momentsState.value.isNotEmpty() || !profile?.moments.isNullOrEmpty()
        val sheet = ConnectBecauseBottomSheetDialogFragment.newInstance(
            displayName = currentUserName,
            hasSharedInterests = sharedCount > 0,
            hasMoments = hasMoments
        )
        sheet.onSend = { reason ->
            actionBeforeOptimistic = currentMyAction
            currentMyAction = "like"
            updateActionBar()
            lastConnectionReaction = pendingReaction
            viewModel.sendConnectionRequest(targetUserId, reason)
            clearPartialReactionSelection()
        }
        sheet.show(supportFragmentManager, ConnectBecauseBottomSheetDialogFragment.TAG)
    }

    private fun openMoments(focusMomentId: String? = null) {
        AppNavigator.navigateTo(
            context = this@OtherUserProfileActivity,
            destination = Destination.UserMoments(
                userId = targetUserId,
                userName = currentUserName,
                focusMomentId = focusMomentId,
                userAvatar = currentUserAvatar,
                isVerified = isCurrentUserVerified
            )
        )
    }

    private fun updateActionBar() {
        val action = currentMyAction?.lowercase()
        val hasSuperLiked = action == "super_like"
        val hasLiked = action == "like" || hasSuperLiked
        val isMatched = isMatchedWithUser

        binding.btnNope.visibility = View.VISIBLE
        binding.btnLike.visibility = if (hasLiked) View.GONE else View.VISIBLE
        binding.btnSuperLike.visibility = if (hasSuperLiked) View.GONE else View.VISIBLE
        binding.btnChat.visibility = if (isMatched) View.VISIBLE else View.GONE
    }

    private fun showActionSheet() {
        val sheet = OtherUserActionsBottomSheetDialogFragment.newInstance(currentUserName)
        sheet.onReportClick = {
            showReportReasons()
        }
        sheet.onBlockClick = {
            viewModel.blockUser(targetUserId, "Blocked from Profile")
        }
        sheet.show(supportFragmentManager, OtherUserActionsBottomSheetDialogFragment.TAG)
    }

    private fun showReportReasons() {
        val reportSheet = ReportUserBottomSheetDialogFragment.newInstance(currentUserName)
        reportSheet.onReasonSelected = { title, code ->
            viewModel.reportUser(targetUserId, reason = code, description = title)
        }
        reportSheet.show(supportFragmentManager, ReportUserBottomSheetDialogFragment.TAG)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOtherUserProfile) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.layoutTopActions.updatePadding(top = statusBarInsets.top + 8)

            binding.layoutBottomActions.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = navBarInsets.bottom + 20
            }

            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Profile State
                launch {
                    viewModel.profileState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                if (binding.tvNameAge.text.isNullOrEmpty()) {
                                    binding.progressBar.visibility = View.VISIBLE
                                }
                            }
                            is Resource.Success -> {
                                binding.progressBar.visibility = View.GONE
                                bindUserToUi(resource.data)
                            }
                            is Resource.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@OtherUserProfileActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 2. Moments State
                launch {
                    viewModel.momentsState.collect { moments ->
                        momentsAdapter.submitList(moments)
                        val count = moments.size
                        binding.tvMomentsCount.text = "$count"
                        binding.layoutMomentsHeader.visibility = if (count > 0) View.VISIBLE else View.GONE
                        binding.rvMoments.visibility = if (count > 0) View.VISIBLE else View.GONE
                    }
                }

                // 3. Action Events (Nope, Like, SuperLike)
                launch {
                    viewModel.actionEvent.collect { (action, data) ->
                        if (action == "nope") {
                            Toast.makeText(this@OtherUserProfileActivity, "Not interested", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            val didMatch = data?.isMatched == true
                            if (didMatch) {
                                isMatchedWithUser = true
                            }
                            currentMyAction = action
                            updateActionBar()
                            handleLikeResult(action, didMatch)
                        }
                    }
                }

                // 4. Block Result
                launch {
                    viewModel.blockResult.collect { res ->
                        when (res) {
                            is Resource.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                            is Resource.Success -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@OtherUserProfileActivity, "User has been blocked", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            is Resource.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@OtherUserProfileActivity, res.message ?: "Failed to block user", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 5. Report Result
                launch {
                    viewModel.reportResult.collect { res ->
                        when (res) {
                            is Resource.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                            is Resource.Success -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(
                                    this@OtherUserProfileActivity,
                                    "Thanks for letting us know. We'll review this profile.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            is Resource.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@OtherUserProfileActivity, res.message ?: "Failed to submit report", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 6. Error Messages
                launch {
                    viewModel.errorMessage.collect { msg ->
                        Toast.makeText(this@OtherUserProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.actionGate.collect { gate ->
                        currentMyAction = actionBeforeOptimistic
                        updateActionBar()
                        if (PremiumStatus.isPremium(userProfileViewModel)) return@collect
                        when (gate) {
                            OtherUserActionGate.LikeLimit ->
                                SubscriptionPopUp.show(this@OtherUserProfileActivity, SubscriptionPopUpType.UNLIMITED_SWIPES)
                            OtherUserActionGate.InsufficientCoins ->
                                SubscriptionPopUp.show(this@OtherUserProfileActivity, SubscriptionPopUpType.SUPER_LIKE)
                        }
                    }
                }
            }
        }
    }

    private fun bindUserToUi(user: UserSearchItemDto) {
        currentUserName = user.effectiveName.ifBlank { "User" }
        currentUserAvatar = user.effectivePhoto
        isCurrentUserVerified = user.isVerified == true
        isMatchedWithUser = user.resolveIsMatched(fallback = isMatchedWithUser)
        currentMyAction = user.resolveMyAction(fallback = currentMyAction)
        updateActionBar()

        // Name & Age
        val displayName = currentUserName
        val ageText = user.age?.let { ", $it" } ?: ""
        binding.tvNameAge.text = "$displayName$ageText"

        // Verified Badge (Red iOS Badge)
        binding.ivVerifiedBadge.visibility = if (user.isVerified == true) View.VISIBLE else View.GONE

        // Location & Distance
        val locText = user.effectiveLocation
        if (locText.isNotEmpty()) {
            binding.layoutLocation.visibility = View.VISIBLE
            binding.tvLocation.text = locText
        } else {
            binding.layoutLocation.visibility = View.GONE
        }

        // Bio Quote
        val bioText = user.effectiveBio
        if (bioText.isNotEmpty()) {
            binding.tvBio.visibility = View.VISIBLE
            binding.tvBio.text = if (bioText.startsWith("“") || bioText.startsWith("\"")) bioText else "“$bioText”"
        } else {
            binding.tvBio.visibility = View.GONE
        }

        // Profile Photo & Cover (FAST LOADING - ZERO SHIMMER!)
        val photoUrl = user.effectivePhoto
        val coverUrl = user.effectiveCover.ifBlank {
            user.coverPhotoUrl?.takeIf { it.isNotBlank() }
                ?: user.photos?.firstOrNull()?.effectiveUrl?.takeIf { it.isNotBlank() }
                ?: photoUrl
        }

        FastImageHelper.loadDirect(
            draweeView = binding.ivAvatar,
            url = photoUrl,
            fallbackRes = R.drawable.explore_placeholder
        )

        FastImageHelper.loadDirect(
            draweeView = binding.ivCoverPhoto,
            url = coverUrl.takeIf { it.isNotBlank() },
            fallbackRes = R.drawable.img_login,
            fadeDurationMs = 280
        )

        // Voice Intro (Matching iOS Image 1)
        bindVoiceIntro(user)

        // Photos List (FAST LOADING - ZERO SHIMMER!)
        val photoUrls = mutableListOf<String>()
        user.photos?.forEach { p ->
            val u = p.effectiveUrl
            if (u.isNotBlank()) photoUrls.add(u)
        }
        if (photoUrls.isEmpty() && photoUrl.isNotBlank()) {
            photoUrls.add(photoUrl)
        }
        currentPhotoUrls = photoUrls
        photosAdapter.submitList(photoUrls)
        binding.tvPhotosCount.text = "${photoUrls.size}"
        binding.layoutPhotosHeader.visibility = if (photoUrls.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvPhotos.visibility = if (photoUrls.isNotEmpty()) View.VISIBLE else View.GONE

        // Interests List (Wrapped ChipGroup matching iOS Image 2)
        val interestsList = user.interests.orEmpty()
        bindInterests(interestsList)

        bindWhyYouMatch(user)

        // Common Interests Badge
        val commonCount = user.sharedInterests?.size ?: interestsList.count { it.isCommon == true }
        if (commonCount > 0) {
            binding.tvCommonInterestsBadge.visibility = View.VISIBLE
            binding.tvCommonInterestsBadge.text = "💖 $commonCount in common"
        } else {
            binding.tvCommonInterestsBadge.visibility = View.GONE
        }

        // More About Me (Card with S3 iOS icons matching iOS Image 2 & 3)
        val moreAboutMeItems = user.getEffectiveMoreAboutMeItems()
        bindMoreAboutMe(moreAboutMeItems)

        // Moments from Profile response if provided
        if (!user.moments.isNullOrEmpty()) {
            momentsAdapter.submitList(user.moments)
            binding.tvMomentsCount.text = "${user.moments.size}"
            binding.layoutMomentsHeader.visibility = View.VISIBLE
            binding.rvMoments.visibility = View.VISIBLE
        }
    }

    private fun bindVoiceIntro(user: UserSearchItemDto) {
        val voice = user.voiceIntro
        val url = voice?.effectiveUrl
        if (url.isNullOrBlank()) {
            binding.layoutVoiceIntroHeader.visibility = View.GONE
            binding.cardVoiceIntro.visibility = View.GONE
            partialReactionButtons[MomentsReaction.Target.Voice]?.visibility = View.GONE
            return
        }

        currentVoiceUrl = url
        currentVoiceDurationSec = voice.effectiveDuration.coerceAtLeast(3)

        binding.layoutVoiceIntroHeader.visibility = View.VISIBLE
        binding.cardVoiceIntro.visibility = View.VISIBLE
        partialReactionButtons[MomentsReaction.Target.Voice]?.visibility = View.VISIBLE
        binding.tvVoiceDuration.text = formatDuration(currentVoiceDurationSec)
        binding.waveformVoice.setProgress(0f)
        binding.ivVoicePlayIcon.setImageResource(R.drawable.ic_voice_play_triangle)

        binding.btnVoicePlay.setOnClickListener {
            toggleVoicePlayback(url, currentVoiceDurationSec)
        }
    }

    private fun toggleVoicePlayback(url: String, durationSec: Int) {
        if (isVoicePlaying) {
            pauseVoicePlayback()
            return
        }

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(url)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        isVoicePlaying = true
                        binding.ivVoicePlayIcon.setImageResource(R.drawable.ic_voice_pause_bars)
                        startWaveformTracking(mp, durationSec)
                    }
                    setOnCompletionListener {
                        resetVoicePlayer(durationSec)
                    }
                    setOnErrorListener { _, _, _ ->
                        resetVoicePlayer(durationSec)
                        Toast.makeText(this@OtherUserProfileActivity, "Unable to play audio", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
            } else {
                mediaPlayer?.start()
                isVoicePlaying = true
                binding.ivVoicePlayIcon.setImageResource(R.drawable.ic_voice_pause_bars)
                mediaPlayer?.let { startWaveformTracking(it, durationSec) }
            }
        } catch (e: Exception) {
            resetVoicePlayer(durationSec)
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pauseVoicePlayback() {
        mediaPlayer?.pause()
        isVoicePlaying = false
        voiceProgressJob?.cancel()
        binding.ivVoicePlayIcon.setImageResource(R.drawable.ic_voice_play_triangle)
    }

    private fun resetVoicePlayer(totalDurationSec: Int) {
        isVoicePlaying = false
        voiceProgressJob?.cancel()
        binding.ivVoicePlayIcon.setImageResource(R.drawable.ic_voice_play_triangle)
        binding.waveformVoice.setProgress(0f)
        binding.tvVoiceDuration.text = formatDuration(totalDurationSec)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startWaveformTracking(mp: MediaPlayer, durationSec: Int) {
        voiceProgressJob?.cancel()
        voiceProgressJob = lifecycleScope.launch {
            while (isActive && isVoicePlaying) {
                try {
                    val currentPos = mp.currentPosition
                    val totalDuration = mp.duration.takeIf { it > 0 } ?: (durationSec * 1000)
                    val progress = (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    binding.waveformVoice.setProgress(progress)

                    val remainingSec = ((totalDuration - currentPos) / 1000).coerceAtLeast(0)
                    binding.tvVoiceDuration.text = formatDuration(remainingSec)
                } catch (e: Exception) {
                    break
                }
                delay(60)
            }
        }
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    private fun handleLikeResult(action: String, didMatch: Boolean) {
        val sharedCount = (viewModel.profileState.value as? Resource.Success)?.data?.sharedInterests?.size ?: 0
        val reactionSubtitle = lastConnectionReaction?.title
        lastConnectionReaction = null
        if (didMatch) {
            ConnectionResultDialog.showMatch(
                context = this,
                name = currentUserName,
                avatarUrl = currentUserAvatar,
                hasSharedInterests = sharedCount > 0,
                onStartConversation = {
                    AppNavigator.navigateTo(
                        context = this,
                        destination = Destination.Main(targetTab = 3)
                    )
                },
                subtitleOverride = reactionSubtitle
            )
        } else if (action == "like") {
            ConnectionResultDialog.showLikeSent(
                context = this,
                name = currentUserName,
                avatarUrl = currentUserAvatar,
                subtitleOverride = reactionSubtitle
            )
        } else {
            Toast.makeText(this, "Super like sent", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindWhyYouMatch(user: UserSearchItemDto) {
        val reasons = WhyYouMatchHelper.reasonsFor(user)
        val score = user.profileMatchPercentage ?: user.vibeScore ?: user.compatibility?.interestMatchPercentage
            ?: user.compatibility?.vibeScore
        if (reasons.isEmpty() && (score == null || score <= 0)) {
            binding.cardWhyYouMatch.visibility = View.GONE
            return
        }

        binding.cardWhyYouMatch.visibility = View.VISIBLE
        if (score != null && score > 0) {
            binding.tvMatchPercentBadge.visibility = View.VISIBLE
            binding.tvMatchPercentBadge.text = "$score% Match"
        } else {
            binding.tvMatchPercentBadge.visibility = View.GONE
        }

        val container = binding.layoutWhyReasons
        container.removeAllViews()
        reasons.forEach { reason ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
            }
            val emoji = android.widget.TextView(this).apply {
                text = reason.emoji
                textSize = 16f
            }
            val text = android.widget.TextView(this).apply {
                this.text = reason.text
                textSize = 14f
                setTextColor(0xFF333333.toInt())
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@OtherUserProfileActivity, R.font.fredokaregular)
                setPadding((10 * resources.displayMetrics.density).toInt(), 0, 0, 0)
            }
            row.addView(emoji)
            row.addView(text)
            container.addView(row)
        }
    }

    private fun bindInterests(interests: List<UserInterestDto>) {
        binding.cgInterests.removeAllViews()
        if (interests.isEmpty()) {
            binding.layoutInterestsHeader.visibility = View.GONE
            binding.cgInterests.visibility = View.GONE
            return
        }

        binding.layoutInterestsHeader.visibility = View.VISIBLE
        binding.cgInterests.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        interests.forEach { interest ->
            val chipBinding = ItemOtherUserInterestBinding.inflate(inflater, binding.cgInterests, false)
            val name = interest.name.orEmpty()
            chipBinding.tvInterestName.text = name

            val icon = resolveInterestEmoji(name, interest.emoji ?: interest.icon)
            chipBinding.tvInterestIcon.text = icon

            val isCommon = interest.isCommon == true
            if (isCommon) {
                chipBinding.rootInterestChip.setBackgroundResource(R.drawable.bg_interest_chip_pill_common)
                chipBinding.tvInterestName.setTextColor(ContextCompat.getColor(this, R.color.brand_red))
            } else {
                chipBinding.rootInterestChip.setBackgroundResource(R.drawable.bg_interest_chip_pill)
                chipBinding.tvInterestName.setTextColor(0xFF333333.toInt())
            }

            binding.cgInterests.addView(chipBinding.root)
        }
    }

    private fun bindMoreAboutMe(items: List<MoreAboutMeItemDto>) {
        val container = binding.layoutMoreAboutMeRows
        container.removeAllViews()

        if (items.isEmpty()) {
            binding.layoutMoreAboutMeHeader.visibility = View.GONE
            binding.cardMoreAboutMe.visibility = View.GONE
            return
        }

        binding.layoutMoreAboutMeHeader.visibility = View.VISIBLE
        binding.cardMoreAboutMe.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        items.forEachIndexed { index, item ->
            val rowBinding = ItemOtherUserMoreAboutMeRowBinding.inflate(inflater, container, false)

            // Resolve exact iOS icon URL from S3 (attributes/*.png)
            val iconUrl = AttributesCatalog.getIconUrl(this, item.key)
            val label = AttributesCatalog.getTitle(this, item.key)

            if (!iconUrl.isNullOrBlank()) {
                rowBinding.ivRowIcon.visibility = View.VISIBLE
                rowBinding.tvRowEmoji.visibility = View.GONE
                FastImageHelper.loadDirect(rowBinding.ivRowIcon, iconUrl)
            } else {
                rowBinding.ivRowIcon.visibility = View.GONE
                rowBinding.tvRowEmoji.visibility = View.VISIBLE
                rowBinding.tvRowEmoji.text = item.icon ?: "✨"
            }

            rowBinding.tvRowLabel.text = label
            rowBinding.tvRowValue.text = item.value ?: "Not specified"
            rowBinding.viewDivider.visibility = if (index == items.lastIndex) View.GONE else View.VISIBLE

            container.addView(rowBinding.root)
        }
    }

    private fun resolveInterestEmoji(name: String, provided: String?): String {
        if (!provided.isNullOrBlank()) return provided
        val lower = name.lowercase().trim()
        return when {
            lower.contains("party") -> "🎉"
            lower.contains("exercise") -> "🏋️"
            lower.contains("shop") -> "🛍️"
            lower.contains("game") || lower.contains("gaming") -> "🎮"
            lower.contains("fitness") || lower.contains("gym") -> "💪"
            lower.contains("sport") -> "⚽"
            lower.contains("music") -> "🎵"
            lower.contains("travel") -> "✈️"
            lower.contains("read") || lower.contains("book") -> "📚"
            lower.contains("cook") || lower.contains("food") -> "🍳"
            lower.contains("photo") -> "📸"
            lower.contains("coffee") -> "☕"
            lower.contains("movie") || lower.contains("cinema") -> "🎬"
            lower.contains("art") || lower.contains("draw") -> "🎨"
            lower.contains("dance") -> "💃"
            lower.contains("nature") || lower.contains("hike") -> "🌿"
            lower.contains("pet") || lower.contains("dog") || lower.contains("cat") -> "🐾"
            else -> "✨"
        }
    }

    private fun animateButtonClick(view: View) {
        view.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    override fun onPause() {
        super.onPause()
        if (isVoicePlaying) {
            pauseVoicePlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceProgressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
