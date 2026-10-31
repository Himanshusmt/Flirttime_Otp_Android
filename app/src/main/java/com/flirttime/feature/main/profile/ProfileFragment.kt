package com.flirttime.feature.main.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.image.CropAspectRatio
import com.flirttime.core.image.CropConfig
import com.flirttime.core.image.CropResult
import com.flirttime.core.image.CropShape
import com.flirttime.core.image.ImageCropperHelper
import com.flirttime.core.image.PhotoSourceBottomSheetDialogFragment
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentProfileBinding
import com.flirttime.feature.profile.VoiceIntroBottomSheetDialogFragment
import com.flirttime.feature.quickmoments.QuickMomentsTrayAdapter
import com.flirttime.feature.quickmoments.AddNewMomentBottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val userProfileViewModel: UserProfileViewModel by inject()
    private lateinit var momentsAdapter: ProfileMomentsAdapter
    private lateinit var photosAdapter: ProfilePhotosAdapter
    private lateinit var quickMomentsAdapter: QuickMomentsTrayAdapter
    private lateinit var cropperHelper: ImageCropperHelper
    private var isVoicePlaying = false
    private var currentVoiceUrl: String? = null
    private var hasResolvedVoice = false
    private var currentVoiceMode: VoiceUiMode? = null
    private var renderedInterests: List<com.flirttime.feature.profile.UserInterestDto>? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var voicePlaybackJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cropperHelper = ImageCropperHelper.register(this) { result ->
            when (result) {
                is CropResult.Success -> {
                    val uri = result.croppedUri
                    binding.ivCoverPhoto.tag = uri.toString()
                    binding.ivCoverPhoto.setImageURI(uri)
                    userProfileViewModel.uploadCoverPhoto(requireContext(), uri)
                }
                is CropResult.Failure -> {
                    Toast.makeText(requireContext(), "Cropping failed: ${result.error?.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                CropResult.Cancelled -> Unit
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopActionsInsets()
        setupPullToRefresh()
        observeUserProfile()
        observeUploadCoverPhoto()
        // Set initial voice state: If resolved -> show active content; only show skeleton shimmer on cold launch
        val currentAudioResource = userProfileViewModel.userAudioState.value
        val initialAudio = currentAudioResource?.getOrNull()
        if (initialAudio != null && !initialAudio.effectiveUrl.isNullOrBlank()) {
            hasResolvedVoice = true
            currentVoiceUrl = initialAudio.effectiveUrl
            val duration = initialAudio.duration ?: 0
            val min = duration / 60
            val sec = duration % 60
            binding.tvVoiceDuration.text = String.format("%02d:%02d", min, sec)
            setVoiceUiMode(VoiceUiMode.HAS_VOICE)
        } else if (currentAudioResource is Resource.Success && initialAudio?.effectiveUrl.isNullOrBlank()) {
            hasResolvedVoice = true
            setVoiceUiMode(VoiceUiMode.NO_VOICE)
        } else if (currentAudioResource is Resource.Error) {
            hasResolvedVoice = true
            setVoiceUiMode(VoiceUiMode.NO_VOICE)
        } else {
            // First cold launch only
            if (!hasResolvedVoice) {
                setVoiceUiMode(VoiceUiMode.LOADING)
            }
        }
        setupVoiceSection()
        setupInterestsSection()
        setupMomentsSection()
        setupPhotosSection()
        setupQuickMomentsSection()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Single atomic profile refresh (fetches user profile, interests, moments, photos, and voice note in one call)
        userProfileViewModel.fetchUserProfile()

        // Ensure status bar icons are white over the cover photo
        activity?.window?.let { window ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }
    }

    private fun observeUserProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.userProfileState.collectLatest { profile ->
                if (profile != null) {
                    bindUserProfile(profile)
                }
            }
        }
    }

    private fun bindUserProfile(profile: UserProfileUiModel) {
        // 1. Profile Completion Ring & Badge
        if (binding.profileProgressRing.progress != profile.completionPercent) {
            binding.profileProgressRing.progress = profile.completionPercent
        }
        val percentText = "${profile.completionPercent}% complete"
        if (binding.tvCompletionPercent.text != percentText) {
            binding.tvCompletionPercent.text = percentText
        }
        // If profile is 100% complete, hide the % complete pill badge, keeping the circular ring around avatar
        val badgeVisibility = if (profile.completionPercent >= 100) View.GONE else View.VISIBLE
        if (binding.badgeComplete.visibility != badgeVisibility) {
            binding.badgeComplete.visibility = badgeVisibility
        }

        // 2. Name & Age
        val nameAge = if (profile.age != null) "${profile.name}, ${profile.age}" else profile.name
        if (binding.tvNameAge.text != nameAge) {
            binding.tvNameAge.text = nameAge
        }

        // 3. Location (hide entire container if blank to avoid orphan pin and empty space jumps)
        val hasLocation = profile.location.isNotBlank()
        if (hasLocation) {
            if (binding.tvLocation.text != profile.location) {
                binding.tvLocation.text = profile.location
            }
            binding.layoutLocation.visibility = View.VISIBLE
        } else {
            binding.layoutLocation.visibility = View.GONE
        }

        // 4. Bio (hide if blank to avoid empty space jumps)
        val hasBio = profile.bio.isNotBlank()
        if (hasBio) {
            if (binding.tvBio.text != profile.bio) {
                binding.tvBio.text = profile.bio
            }
            binding.tvBio.visibility = View.VISIBLE
        } else {
            binding.tvBio.visibility = View.GONE
        }

        // 5. Verified Badge
        val verifiedVisibility = if (profile.isVerified) View.VISIBLE else View.GONE
        if (binding.ivVerifiedBadge.visibility != verifiedVisibility) {
            binding.ivVerifiedBadge.visibility = verifiedVisibility
        }

        // 6. User Avatar (Direct Fresco loading with tag caching to avoid flicker)
        val avatarUrl = profile.profilePhotoUrl
        if (!avatarUrl.isNullOrBlank()) {
            if (binding.ivAvatar.tag != avatarUrl) {
                binding.ivAvatar.tag = avatarUrl
                binding.ivAvatar.setImageURI(avatarUrl)
            }
        } else if (binding.ivAvatar.tag != "default") {
            binding.ivAvatar.tag = "default"
            binding.ivAvatar.setActualImageResource(R.drawable.img_login)
        }

        // 7. Cover Photo (Direct Fresco loading with tag caching to avoid flicker)
        val coverUrl = profile.coverPhotoUrl
        if (!coverUrl.isNullOrBlank()) {
            if (binding.ivCoverPhoto.tag != coverUrl) {
                binding.ivCoverPhoto.tag = coverUrl
                binding.ivCoverPhoto.setImageURI(coverUrl)
            }
        } else if (binding.ivCoverPhoto.tag != "default") {
            binding.ivCoverPhoto.tag = "default"
            binding.ivCoverPhoto.setActualImageResource(R.drawable.img_login)
        }

        // 8. Quick Moments "Add new" Thumbnail (Direct Fresco loading with tag caching to avoid flicker)
        if (!avatarUrl.isNullOrBlank()) {
            if (binding.ivAddNewThumb.tag != avatarUrl) {
                binding.ivAddNewThumb.tag = avatarUrl
                binding.ivAddNewThumb.setImageURI(avatarUrl)
            }
        } else if (binding.ivAddNewThumb.tag != "default") {
            binding.ivAddNewThumb.tag = "default"
            binding.ivAddNewThumb.setActualImageResource(R.drawable.img_login)
        }
    }

    private fun setupClickListeners() {
        // Edit Cover Photo (triggered exclusively on camera icon click, not on cover photo click)
        binding.btnEditCover.setOnClickListener {
            showCoverPhotoPickerBottomSheet()
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Settings)
        }

        // Profile Completion Pill -> Edit Profile
        binding.badgeComplete.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.EditProfile)
        }

        // Edit Profile CTA
        binding.btnEditProfile.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.EditProfile)
        }

        // Add Moment CTA -> Navigate directly to AddMomentActivity
        binding.btnAddMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // Voice Section: Empty State (Record Prompt)
        binding.cardVoiceRecordPrompt.setOnClickListener {
            showVoiceIntroBottomSheet()
        }
        binding.btnRecordMic.setOnClickListener {
            showVoiceIntroBottomSheet()
        }

        // Voice Section: Active State (Player & Edit)
        binding.btnPlayVoice.setOnClickListener {
            toggleVoicePlayback()
        }
        binding.cardVoicePlayer.setOnClickListener {
            toggleVoicePlayback()
        }

        // Edit Voice Note
        binding.btnEditVoice.setOnClickListener {
            showVoiceIntroBottomSheet()
        }

        // Quick Moments + Add
        binding.btnAddQuickMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // Quick Moments: Add new item
        binding.itemAddNewMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // Premium Benefit Banner
        binding.cardPremiumBanner.setOnClickListener {
            Toast.makeText(requireContext(), "Upgrade to Premium to unlock all benefits", Toast.LENGTH_SHORT).show()
        }
        binding.btnUpgradePremium.setOnClickListener {
            Toast.makeText(requireContext(), "Upgrade to Premium to unlock all benefits", Toast.LENGTH_SHORT).show()
        }

        // My Interests: Edit -> Navigate to Edit Profile
        binding.btnEditInterests.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.EditProfile)
        }

        // My Moments: See all
        binding.btnSeeAllMoments.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.MyMoments)
        }

        // My Moments: Empty State Add CTA
        binding.btnEmptyAddMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // My Photos: Edit -> Navigate to Edit Profile
        binding.btnEditPhotos.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.EditProfile)
        }
    }

    private fun setupInterestsSection() {
        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.savedInterestsState.collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val interests = resource.data.orEmpty()
                        renderInterests(interests)
                    }
                    is Resource.Error -> {
                        if (binding.cgMyInterests.childCount == 0) {
                            binding.tvNoInterests.visibility = View.VISIBLE
                            binding.cgMyInterests.visibility = View.GONE
                        }
                    }
                    is Resource.Loading -> Unit
                    null -> Unit
                }
            }
        }
    }

    private fun renderInterests(interests: List<com.flirttime.feature.profile.UserInterestDto>) {
        if (renderedInterests == interests && binding.cgMyInterests.childCount > 0) {
            return
        }
        renderedInterests = interests
        binding.cgMyInterests.removeAllViews()
        if (interests.isNotEmpty()) {
            binding.tvNoInterests.visibility = View.GONE
            binding.cgMyInterests.visibility = View.VISIBLE
            val inflater = LayoutInflater.from(requireContext())
            interests.forEach { interest ->
                val chip = inflater.inflate(R.layout.item_profile_interest_chip, binding.cgMyInterests, false) as com.google.android.material.chip.Chip
                val label = buildString {
                    if (!interest.icon.isNullOrBlank()) {
                        append(interest.icon).append(" ")
                    }
                    append(interest.name.orEmpty())
                }
                chip.text = label
                chip.setOnClickListener {
                    Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show()
                }
                binding.cgMyInterests.addView(chip)
            }
        } else {
            binding.tvNoInterests.visibility = View.VISIBLE
            binding.cgMyInterests.visibility = View.GONE
        }
    }

    private fun setupPhotosSection() {
        photosAdapter = ProfilePhotosAdapter { photo ->
            AppNavigator.navigateTo(requireContext(), Destination.EditProfile)
        }

        binding.rvMyPhotos.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvMyPhotos.adapter = photosAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.userPhotosState.collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val photos = resource.data.orEmpty()
                        val items = photos.map { photo ->
                            ProfilePhotoItem(
                                id = photo.id.orEmpty(),
                                url = photo.url ?: photo.thumbnailUrl
                            )
                        }
                        photosAdapter.submitList(items)
                    }
                    is Resource.Error -> {
                        if (photosAdapter.itemCount == 0) {
                            photosAdapter.submitList(emptyList())
                        }
                    }
                    is Resource.Loading -> Unit
                    null -> Unit
                }
            }
        }
    }

    private fun setupQuickMomentsSection() {
        quickMomentsAdapter = QuickMomentsTrayAdapter { story ->
            Toast.makeText(requireContext(), "Quick Moment: ${story.relativeAge ?: "Story"}", Toast.LENGTH_SHORT).show()
        }

        binding.rvQuickMoments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvQuickMoments.adapter = quickMomentsAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.myQuickMomentsState.collectLatest { resource ->
                val stories = resource?.getOrNull()?.getEffectiveStories().orEmpty()
                quickMomentsAdapter.submitList(stories)
            }
        }
    }

    private fun setupMomentsSection() {
        momentsAdapter = ProfileMomentsAdapter { _ ->
            AppNavigator.navigateTo(requireContext(), Destination.MyMoments)
        }

        binding.rvMyMoments.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvMyMoments.adapter = momentsAdapter

        // Initial state before network returns - only hide if moments not already loaded
        val currentMoments = userProfileViewModel.userMomentsState.value?.getOrNull()?.moments
        if (currentMoments == null) {
            binding.btnSeeAllMoments.visibility = View.GONE
            binding.layoutMomentsEmptyState.visibility = View.GONE
        }

        // Wire empty state clicks to Add Moment
        binding.btnEmptyAddMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }
        binding.layoutMomentsEmptyState.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.userMomentsState.collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val moments = resource.data?.moments.orEmpty()
                        if (moments.isNotEmpty()) {
                            // Display up to 4 moments in the 2x2 grid on profile screen
                            momentsAdapter.submitList(moments.take(4).toList())
                            val totalCount = resource.data?.totalCount
                                ?: resource.data?.momentCount
                                ?: moments.size
                            binding.btnSeeAllMoments.text = "See all ($totalCount)"
                            binding.btnSeeAllMoments.visibility = View.VISIBLE
                            binding.rvMyMoments.visibility = View.VISIBLE
                            binding.layoutMomentsEmptyState.visibility = View.GONE
                        } else {
                            // If no moments, hide "See all" and grid, show empty state card
                            momentsAdapter.submitList(emptyList())
                            binding.btnSeeAllMoments.visibility = View.GONE
                            binding.rvMyMoments.visibility = View.GONE
                            binding.layoutMomentsEmptyState.visibility = View.VISIBLE
                        }
                    }
                    is Resource.Error -> {
                        if (momentsAdapter.itemCount == 0) {
                            binding.btnSeeAllMoments.visibility = View.GONE
                            binding.rvMyMoments.visibility = View.GONE
                            binding.layoutMomentsEmptyState.visibility = View.VISIBLE
                        }
                    }
                    is Resource.Loading -> Unit
                    null -> Unit
                }
            }
        }
    }

    private fun setupVoiceSection() {
        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.userAudioState.collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        // Only trigger loading shimmer if voice state has NEVER been resolved before
                        if (!hasResolvedVoice && currentVoiceUrl.isNullOrBlank()) {
                            setVoiceUiMode(VoiceUiMode.LOADING)
                        }
                    }
                    is Resource.Success -> {
                        hasResolvedVoice = true
                        val audio = resource.data
                        val url = audio?.effectiveUrl
                        if (!url.isNullOrBlank()) {
                            currentVoiceUrl = url
                            val duration = audio.duration ?: 0
                            val min = duration / 60
                            val sec = duration % 60
                            binding.tvVoiceDuration.text = String.format("%02d:%02d", min, sec)
                            setVoiceUiMode(VoiceUiMode.HAS_VOICE)
                        } else {
                            currentVoiceUrl = null
                            setVoiceUiMode(VoiceUiMode.NO_VOICE)
                        }
                    }
                    is Resource.Error -> {
                        hasResolvedVoice = true
                        if (currentVoiceUrl.isNullOrBlank()) {
                            setVoiceUiMode(VoiceUiMode.NO_VOICE)
                        }
                    }
                    null -> Unit
                }
            }
        }
    }

    private fun showVoiceIntroBottomSheet() {
        val bottomSheet = VoiceIntroBottomSheetDialogFragment.newInstance()
        bottomSheet.onVoiceIntroSaved = { url, duration ->
            currentVoiceUrl = url
            val min = duration / 60
            val sec = duration % 60
            binding.tvVoiceDuration.text = String.format("%02d:%02d", min, sec)
            setVoiceUiMode(VoiceUiMode.HAS_VOICE)
            userProfileViewModel.loadUserAudio()
        }
        bottomSheet.show(childFragmentManager, VoiceIntroBottomSheetDialogFragment.TAG)
    }

    enum class VoiceUiMode {
        LOADING,
        HAS_VOICE,
        NO_VOICE
    }

    /**
     * Updates the "My Voice" section UI depending on whether loading, active, or empty.
     * Guarantees that while network is in-flight, a custom skeleton shimmer appears,
     * and smoothly transitions directly into the player or record prompt without sudden visual jumping.
     */
    fun setVoiceUiMode(mode: VoiceUiMode) {
        if (currentVoiceMode == mode) return
        currentVoiceMode = mode
        when (mode) {
            VoiceUiMode.LOADING -> {
                binding.cardVoiceShimmer.visibility = View.VISIBLE
                binding.cardVoiceShimmer.startShimmer()
                binding.cardVoicePlayer.visibility = View.GONE
                binding.cardVoiceRecordPrompt.visibility = View.GONE
            }
            VoiceUiMode.HAS_VOICE -> {
                binding.cardVoiceShimmer.stopShimmer()
                binding.cardVoiceShimmer.visibility = View.GONE
                binding.cardVoicePlayer.visibility = View.VISIBLE
                binding.cardVoiceRecordPrompt.visibility = View.GONE
            }
            VoiceUiMode.NO_VOICE -> {
                binding.cardVoiceShimmer.stopShimmer()
                binding.cardVoiceShimmer.visibility = View.GONE
                binding.cardVoicePlayer.visibility = View.GONE
                binding.cardVoiceRecordPrompt.visibility = View.VISIBLE
            }
        }
    }

    private fun toggleVoicePlayback() {
        val url = currentVoiceUrl
        if (url.isNullOrBlank()) {
            showVoiceIntroBottomSheet()
            return
        }

        if (isVoicePlaying) {
            mediaPlayer?.pause()
            isVoicePlaying = false
            voicePlaybackJob?.cancel()
            binding.ivPlayIcon.setImageResource(R.drawable.ic_play_white)
        } else {
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = android.media.MediaPlayer().apply {
                        setDataSource(url)
                        setOnPreparedListener {
                            start()
                            isVoicePlaying = true
                            binding.ivPlayIcon.setImageResource(R.drawable.ic_pause_white)
                            startWaveformProgressTicker()
                        }
                        setOnCompletionListener {
                            isVoicePlaying = false
                            voicePlaybackJob?.cancel()
                            binding.ivPlayIcon.setImageResource(R.drawable.ic_play_white)
                            binding.audioWaveformView.setProgress(0f)
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Unable to play voice: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                mediaPlayer?.start()
                isVoicePlaying = true
                binding.ivPlayIcon.setImageResource(R.drawable.ic_pause_white)
                startWaveformProgressTicker()
            }
        }
    }

    private fun startWaveformProgressTicker() {
        voicePlaybackJob?.cancel()
        voicePlaybackJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isVoicePlaying && mediaPlayer != null) {
                try {
                    val currentPos = mediaPlayer?.currentPosition ?: 0
                    val totalDuration = mediaPlayer?.duration ?: 1
                    val progress = (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    binding.audioWaveformView.setProgress(progress)
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(100.milliseconds)
            }
        }
    }

    private fun setupTopActionsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            applyTopActionsMargin(statusBarTop)
            insets
        }
        binding.root.requestApplyInsets()

        val rootInsets = ViewCompat.getRootWindowInsets(binding.root)
        val statusBarTop = rootInsets?.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )?.top ?: 0
        if (statusBarTop > 0) {
            applyTopActionsMargin(statusBarTop)
        }
    }

    private fun applyTopActionsMargin(statusBarTop: Int) {
        val extra = (12 * resources.displayMetrics.density).toInt()
        val targetMargin = if (statusBarTop > 0) statusBarTop + extra else (48 * resources.displayMetrics.density).toInt()
        (binding.layoutTopActions.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.topMargin != targetMargin) {
                lp.topMargin = targetMargin
                binding.layoutTopActions.layoutParams = lp
            }
        }

        // Offset swipe refresh spinner so it starts below the status bar over the cover photo
        val progressStart = statusBarTop + (10 * resources.displayMetrics.density).toInt()
        val progressEnd = statusBarTop + (68 * resources.displayMetrics.density).toInt()
        binding.swipeRefreshProfile.setProgressViewOffset(false, progressStart, progressEnd)
    }

    private fun setupPullToRefresh() {
        binding.swipeRefreshProfile.setColorSchemeResources(R.color.brand_red)
        binding.swipeRefreshProfile.setOnRefreshListener {
            userProfileViewModel.refreshProfile()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.isRefreshing.collectLatest { refreshing ->
                binding.swipeRefreshProfile.isRefreshing = refreshing
            }
        }
    }

    private fun observeUploadCoverPhoto() {
        viewLifecycleOwner.lifecycleScope.launch {
            userProfileViewModel.uploadCoverPhotoState.collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        Toast.makeText(requireContext(), "Cover photo updated successfully", Toast.LENGTH_SHORT).show()
                        userProfileViewModel.resetUploadCoverPhotoState()
                    }
                    is Resource.Error -> {
                        Toast.makeText(requireContext(), "Failed to upload cover: ${resource.message}", Toast.LENGTH_LONG).show()
                        val currentUrl = userProfileViewModel.userProfileState.value?.coverPhotoUrl
                        binding.ivCoverPhoto.tag = currentUrl
                        if (!currentUrl.isNullOrBlank()) {
                            binding.ivCoverPhoto.setImageURI(currentUrl)
                        } else {
                            binding.ivCoverPhoto.setActualImageResource(R.drawable.img_login)
                        }
                        userProfileViewModel.resetUploadCoverPhotoState()
                    }
                    is Resource.Loading -> Unit
                    null -> Unit
                }
            }
        }
    }

//    private fun setupQuickMomentsSection() {
//        quickMomentsAdapter = QuickMomentsTrayAdapter { story ->
//            Toast.makeText(requireContext(), "Quick Moment: ${story.relativeAge ?: "Story"}", Toast.LENGTH_SHORT).show()
//        }
//
//        binding.rvQuickMoments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
//            requireContext(),
//            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
//            false
//        )
//        binding.rvQuickMoments.adapter = quickMomentsAdapter
//
//        viewLifecycleOwner.lifecycleScope.launch {
//            userProfileViewModel.myQuickMomentsState.collectLatest { resource ->
//                val stories = resource?.getOrNull()?.getEffectiveStories().orEmpty()
//                quickMomentsAdapter.submitList(stories)
//            }
//        }
//    }

    private fun showCoverPhotoPickerBottomSheet() {
        val bottomSheet = PhotoSourceBottomSheetDialogFragment.newInstance()
        bottomSheet.onCameraSelected = {
            cropperHelper.launchCamera(
                CropConfig(
                    shape = CropShape.RECTANGLE,
                    aspectRatio = CropAspectRatio.Landscape16_9,
                    title = "Crop Cover Photo"
                )
            )
        }
        bottomSheet.onGallerySelected = {
            cropperHelper.launchGallery(
                CropConfig(
                    shape = CropShape.RECTANGLE,
                    aspectRatio = CropAspectRatio.Landscape16_9,
                    title = "Crop Cover Photo"
                )
            )
        }
        bottomSheet.show(childFragmentManager, PhotoSourceBottomSheetDialogFragment.TAG)
    }

    private fun showAddNewMomentBottomSheet() {
        val sheet = AddNewMomentBottomSheetDialogFragment.newInstance()
        sheet.onMomentCreated = {
            userProfileViewModel.loadMyQuickMoments()
            userProfileViewModel.fetchUserProfile()
        }
        sheet.show(childFragmentManager, AddNewMomentBottomSheetDialogFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voicePlaybackJob?.cancel()
        voicePlaybackJob = null
        mediaPlayer?.release()
        mediaPlayer = null
        isVoicePlaying = false
        currentVoiceMode = null
        renderedInterests = null
        _binding = null
    }
}
