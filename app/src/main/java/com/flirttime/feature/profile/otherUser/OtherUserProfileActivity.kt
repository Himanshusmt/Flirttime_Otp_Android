package com.flirttime.feature.profile.otherUser

import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class OtherUserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtherUserProfileBinding
    private val viewModel: OtherUserProfileViewModel by viewModel()

    private val photosAdapter by lazy {
        OtherUserPhotosAdapter { url, pos ->
            // View full screen photo if needed
        }
    }

    private val momentsAdapter by lazy {
        OtherUserMomentsAdapter(
            onMomentClick = { moment ->
                Toast.makeText(this, moment.caption ?: "Moment clicked", Toast.LENGTH_SHORT).show()
            },
            onSeeAllClick = {
                Toast.makeText(this, "See all moments", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private var targetUserId: String = ""
    private var currentUserName: String = "User"

    // Media Player for Voice Intro
    private var mediaPlayer: MediaPlayer? = null
    private var isVoicePlaying: Boolean = false
    private var voiceProgressJob: Job? = null
    private var currentVoiceUrl: String? = null
    private var currentVoiceDurationSec: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentEdgeToEdge()
        binding = ActivityOtherUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupClickListeners()
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

        val initialData = if (targetUserId.isNotEmpty() && (!username.isNullOrEmpty() || !name.isNullOrEmpty())) {
            UserSearchItemDto(
                id = targetUserId,
                userId = targetUserId,
                username = username,
                name = name,
                age = age,
                profilePhotoUrl = photoUrl,
                coverPhotoUrl = coverUrl,
                city = city,
                about = bio
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

        binding.btnChat.setOnClickListener {
            animateButtonClick(it)
            AppNavigator.navigateTo(
                context = this@OtherUserProfileActivity,
                destination = Destination.Main(targetTab = 3)
            )
        }

        binding.btnSeeAllMoments.setOnClickListener {
            Toast.makeText(this, "See all moments", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActionSheet() {
        val sheet = OtherUserActionsBottomSheetDialogFragment.newInstance(currentUserName)
        sheet.onReportClick = {
            viewModel.reportUser(targetUserId, "Inappropriate profile")
        }
        sheet.onBlockClick = {
            viewModel.blockUser(targetUserId, "Blocked from Profile")
        }
        sheet.show(supportFragmentManager, OtherUserActionsBottomSheetDialogFragment.TAG)
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

                // 3. Action Events (Nope)
                launch {
                    viewModel.actionEvent.collect { (action, _) ->
                        if (action == "nope") {
                            Toast.makeText(this@OtherUserProfileActivity, "Passed", Toast.LENGTH_SHORT).show()
                            finish()
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
                                Toast.makeText(this@OtherUserProfileActivity, "Report submitted successfully. Thank you.", Toast.LENGTH_LONG).show()
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
            }
        }
    }

    private fun bindUserToUi(user: UserSearchItemDto) {
        currentUserName = user.effectiveName.ifBlank { "User" }

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
        val coverUrl = user.effectiveCover // Only real cover photo, does NOT fallback to avatar!

        FastImageHelper.loadDirect(
            draweeView = binding.ivAvatar,
            url = photoUrl,
            fallbackRes = R.drawable.explore_placeholder
        )

        FastImageHelper.loadDirect(
            draweeView = binding.ivCoverPhoto,
            url = coverUrl.takeIf { it.isNotBlank() },
            fallbackRes = R.drawable.bg_cover_placeholder
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
        photosAdapter.submitList(photoUrls)
        binding.tvPhotosCount.text = "${photoUrls.size}"
        binding.layoutPhotosHeader.visibility = if (photoUrls.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvPhotos.visibility = if (photoUrls.isNotEmpty()) View.VISIBLE else View.GONE

        // Interests List (Wrapped ChipGroup matching iOS Image 2)
        val interestsList = user.interests.orEmpty()
        bindInterests(interestsList)

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
            return
        }

        currentVoiceUrl = url
        currentVoiceDurationSec = voice.effectiveDuration.coerceAtLeast(3)

        binding.layoutVoiceIntroHeader.visibility = View.VISIBLE
        binding.cardVoiceIntro.visibility = View.VISIBLE
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
