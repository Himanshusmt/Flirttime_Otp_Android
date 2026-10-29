package com.flirttime.feature.profile.edit

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.image.CropConfig
import com.flirttime.core.image.CropResult
import com.flirttime.core.image.ImageCropperHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityEditProfileBinding
import com.flirttime.databinding.ItemMoreAboutMeRowBinding
import com.flirttime.feature.main.profile.UserProfileViewModel
import com.flirttime.feature.profile.MoreAboutMeItemDto
import com.flirttime.feature.profile.UserInterestDto
import com.flirttime.feature.profile.UserPhotoDto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private enum class PhotoCropTarget {
    AVATAR,
    COVER,
    GALLERY_SLOT
}

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val viewModel: UserProfileViewModel by inject()
    private val sessionManager: SessionManager by inject()
    private lateinit var interestsAdapter: EditInterestsAdapter
    private lateinit var cropperHelper: ImageCropperHelper
    private var photoCropTarget = PhotoCropTarget.AVATAR
    private var selectedGallerySlotIndex = 0

    private var mediaPlayer: MediaPlayer? = null
    private var isVoicePlaying = false
    private var currentAudioUrl: String? = null
    private var currentPhotos: List<UserPhotoDto> = emptyList()

    private var availableCatalog: List<UserInterestDto> = emptyList()
    private var savedInterestIds: MutableSet<String> = mutableSetOf()

    private val moreAboutMeOptions = mapOf(
        "drinking" to arrayOf("Non-Drinker", "Socially", "Regularly", "Planning to quit", "Prefer not to say"),
        "education" to arrayOf("High School", "In College", "Undergraduate", "Bachelor's", "Master's", "PhD / Doctorate", "Trade School"),
        "eyeColor" to arrayOf("Black", "Brown", "Amber", "Hazel", "Blue", "Green", "Gray"),
        "hairColor" to arrayOf("Black", "Brown", "Blonde", "Auburn", "Red", "Gray / White", "Dyed / Colorful"),
        "height" to arrayOf("4'10\" (147 cm)", "5'0\" (152 cm)", "5'2\" (157 cm)", "5'4\" (163 cm)", "5'6\" (168 cm)", "5'8\" (173 cm)", "5'10\" (178 cm)", "6'0\" (183 cm)", "6'2\" (188 cm)", "6'4\" (193 cm)"),
        "living" to arrayOf("Alone", "With roommates", "With parents", "With partner", "Student housing"),
        "relationship" to arrayOf("Single", "In a relationship", "Engaged", "Married", "It's complicated"),
        "religion" to arrayOf("Hindu", "Muslim", "Christian", "Sikh", "Buddhist", "Jain", "Atheist", "Agnostic", "Spiritual", "Other"),
        "sexuality" to arrayOf("Straight", "Gay", "Lesbian", "Bisexual", "Pansexual", "Asexual", "Queer"),
        "smoking" to arrayOf("Non-smoker", "Occasionally", "Regularly", "Trying to quit", "Vape only"),
        "weight" to arrayOf("40-50 kg", "50-60 kg", "60-70 kg", "70-80 kg", "80-90 kg", "90+ kg"),
        "children" to arrayOf("Don't have children", "Have children", "Want children", "Don't want children", "Not sure yet")
    )

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val duration = getAudioDurationSeconds(uri)
            viewModel.uploadAudio(this, uri, duration)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCropper()
        setupInsets()
        setupListeners()
        setupBioWatcher()
        setupInterestsRecyclerView()
        setupMoreAboutMeSection()
        loadLocalProfileData()
        observeViewModel()
    }

    override fun finish() {
        setResult(RESULT_OK)
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setupCropper() {
        cropperHelper = ImageCropperHelper.register(this) { result ->
            when (result) {
                is CropResult.Success -> {
                    when (photoCropTarget) {
                        PhotoCropTarget.AVATAR -> {
                            viewModel.uploadPrimaryPhoto(this, result.croppedUri)
                        }
                        PhotoCropTarget.COVER -> {
                            viewModel.uploadCoverPhoto(this, result.croppedUri)
                        }
                        PhotoCropTarget.GALLERY_SLOT -> {
                            viewModel.addOrReplaceGalleryPhoto(this, selectedGallerySlotIndex, result.croppedUri)
                        }
                    }
                }
                is CropResult.Failure -> {
                    Toast.makeText(this, "Cropping failed: ${result.error?.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                CropResult.Cancelled -> Unit
            }
        }
    }

    private fun setupInsets() {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val fallbackStatusBar = if (resId > 0) resources.getDimensionPixelSize(resId) else 0

        // Set immediate baseline top padding before insets are dispatched
        if (fallbackStatusBar > 0) {
            binding.layoutHeader.updatePadding(top = fallbackStatusBar)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val effectiveStatusTop = maxOf(statusBars.top, fallbackStatusBar)
            binding.layoutHeader.updatePadding(top = effectiveStatusTop)

            val bottomInset = maxOf(navBars.bottom, ime.bottom)
            binding.scrollContent.updatePadding(
                bottom = bottomInset + (24 * resources.displayMetrics.density).toInt()
            )

            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            val bio = binding.etBio.text?.toString()?.trim().orEmpty()
            if (bio.isNotEmpty()) {
                viewModel.saveBio(bio)
            }
            finish()
        }

        binding.btnPreview.setOnClickListener {
            lifecycleScope.launch {
                val userId = sessionManager.getCachedUserId()
                if (userId.isNotBlank()) {
                    AppNavigator.navigateTo(
                        context = this@EditProfileActivity,
                        destination = Destination.UserProfile(userId = userId)
                    )
                } else {
                    Toast.makeText(this@EditProfileActivity, "Previewing profile...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Cover photo (16:9 ratio crop to match profile visual appearance)
        binding.btnChangeCover.setOnClickListener {
            photoCropTarget = PhotoCropTarget.COVER
            cropperHelper.launchGallery(CropConfig.coverPhoto())
        }

        // Primary photo slot
        binding.slotPhotoPrimary.setOnClickListener {
            photoCropTarget = PhotoCropTarget.AVATAR
            cropperHelper.launchGallery(CropConfig.profileAvatar(circular = false))
        }

        // Gallery slots 1 to 5 (individual single image replacement or add)
        val openGallerySlot = { slotIndex: Int ->
            selectedGallerySlotIndex = slotIndex
            photoCropTarget = PhotoCropTarget.GALLERY_SLOT
            cropperHelper.launchGallery(CropConfig.profileAvatar(circular = false))
        }

        binding.slotPhoto1.setOnClickListener { openGallerySlot(0) }
        binding.slotPhoto2.setOnClickListener { openGallerySlot(1) }
        binding.slotPhoto3.setOnClickListener { openGallerySlot(2) }
        binding.slotPhoto4.setOnClickListener { openGallerySlot(3) }
        binding.slotPhoto5.setOnClickListener { openGallerySlot(4) }

        // Voice intro controls
        binding.btnPlayVoice.setOnClickListener {
            toggleVoicePlayback()
        }
        binding.btnReRecordVoice.setOnClickListener {
            val bottomSheet = com.flirttime.feature.profile.VoiceIntroBottomSheetDialogFragment.newInstance()
            bottomSheet.onVoiceIntroSaved = { url, duration ->
                currentAudioUrl = url
                val min = duration / 60
                val sec = duration % 60
                binding.tvVoiceDuration.text = String.format("%02d:%02d", min, sec)
                viewModel.fetchAudioIntro()
            }
            bottomSheet.show(supportFragmentManager, com.flirttime.feature.profile.VoiceIntroBottomSheetDialogFragment.TAG)
        }
        binding.btnDeleteVoice.setOnClickListener {
            viewModel.deleteAudio()
        }
    }

    private fun showPhotoOptionsDialog(photo: UserPhotoDto, slotIndex: Int) {
        val options = arrayOf("Set as Primary Profile Photo", "Replace Photo", "Delete Photo")

        MaterialAlertDialogBuilder(this)
            .setTitle("Photo Options")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Set as Primary Profile Photo" -> {
                        photo.id?.let { id -> viewModel.setPrimaryPhoto(id) }
                    }
                    "Replace Photo" -> {
                        selectedGallerySlotIndex = slotIndex
                        photoCropTarget = PhotoCropTarget.GALLERY_SLOT
                        cropperHelper.launchGallery(CropConfig.profileAvatar(circular = false))
                    }
                    "Delete Photo" -> {
                        photo.id?.let { id -> viewModel.deletePhoto(id) }
                    }
                }
            }
            .show()
    }

    private fun toggleVoicePlayback() {
        val url = currentAudioUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "No voice intro recorded yet", Toast.LENGTH_SHORT).show()
            return
        }

        if (isVoicePlaying) {
            mediaPlayer?.pause()
            isVoicePlaying = false
            binding.ivPlayPauseIcon.setImageResource(R.drawable.direction_arrow)
        } else {
            if (mediaPlayer == null) {
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(url)
                        setOnPreparedListener {
                            start()
                            isVoicePlaying = true
                            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_pause_white)
                        }
                        setOnCompletionListener {
                            isVoicePlaying = false
                            binding.ivPlayPauseIcon.setImageResource(R.drawable.direction_arrow)
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Unable to play audio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                mediaPlayer?.start()
                isVoicePlaying = true
                binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_pause_white)
            }
        }
    }

    private fun getAudioDurationSeconds(uri: Uri): Int {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val durationMs = time?.toLongOrNull() ?: 0L
            (durationMs / 1000).toInt().coerceIn(1, 60)
        } catch (_: Exception) {
            15
        }
    }

    private fun setupBioWatcher() {
        binding.etBio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                binding.tvBioCounter.text = "$length/150"
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupInterestsRecyclerView() {
        interestsAdapter = EditInterestsAdapter(
            onItemClick = { item ->
                Toast.makeText(this, "Interest: ${item.name}", Toast.LENGTH_SHORT).show()
            },
            onAddClick = {
                if (availableCatalog.isNotEmpty()) {
                    showInterestsSelectionDialog(availableCatalog)
                } else {
                    Toast.makeText(this, "Loading interests...", Toast.LENGTH_SHORT).show()
                    viewModel.fetchInterestsCatalog()
                }
            }
        )

        binding.rvInterests.layoutManager = GridLayoutManager(this, 4)
        binding.rvInterests.adapter = interestsAdapter
    }

    private fun showInterestsSelectionDialog(catalog: List<UserInterestDto>) {
        if (catalog.isEmpty()) {
            Toast.makeText(this, "No interests available to select.", Toast.LENGTH_SHORT).show()
            return
        }
        val names = catalog.map { "${it.icon ?: getEmojiForInterest(it.name.orEmpty())} ${it.name}" }.toTypedArray()
        val checkedItems = catalog.map { savedInterestIds.contains(it.id) }.toBooleanArray()
        val tempSelectedIds = savedInterestIds.toMutableSet()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Your Interests")
            .setMultiChoiceItems(names, checkedItems) { _, which, isChecked ->
                val id = catalog[which].id ?: return@setMultiChoiceItems
                if (isChecked) {
                    tempSelectedIds.add(id)
                } else {
                    tempSelectedIds.remove(id)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                savedInterestIds = tempSelectedIds
                viewModel.updateInterests(tempSelectedIds.toList())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getEmojiForInterest(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("art") -> "🎨"
            lower.contains("cook") -> "🍳"
            lower.contains("tennis") -> "🎾"
            lower.contains("travel") -> "✈️"
            lower.contains("game") -> "🕹️"
            lower.contains("music") -> "🎵"
            lower.contains("photo") -> "📷"
            lower.contains("fit") || lower.contains("gym") || lower.contains("sport") -> "💪"
            lower.contains("dance") -> "💃"
            lower.contains("read") || lower.contains("book") -> "📚"
            lower.contains("pet") || lower.contains("dog") || lower.contains("cat") -> "🐶"
            lower.contains("movie") || lower.contains("cinema") -> "🎬"
            lower.contains("coffee") -> "☕"
            lower.contains("party") -> "🎉"
            lower.contains("wine") || lower.contains("drink") -> "🍷"
            else -> "✨"
        }
    }

    private fun setupMoreAboutMeSection() {
        // Initial placeholder rows until network response loads
        val initialItems = listOf(
            MoreAboutMeItemDto("drinking", "Drinking", "🍷", null),
            MoreAboutMeItemDto("education", "Education", "📖", null),
            MoreAboutMeItemDto("eyeColor", "Eyecolor", "👁", null),
            MoreAboutMeItemDto("hairColor", "Haircolor", "💇", null),
            MoreAboutMeItemDto("height", "Height", "📏", null),
            MoreAboutMeItemDto("living", "Living", "🏠", null),
            MoreAboutMeItemDto("relationship", "Relationship", "👩❤️👨", null),
            MoreAboutMeItemDto("religion", "Religion", "🛕", null),
            MoreAboutMeItemDto("sexuality", "Sexuality", "⚧", null),
            MoreAboutMeItemDto("smoking", "Smoking", "🚬", null),
            MoreAboutMeItemDto("weight", "Weight", "⚖️", null),
            MoreAboutMeItemDto("children", "Children", "👶", null)
        )
        bindMoreAboutMeItems(initialItems)
    }

    private fun bindMoreAboutMeItems(items: List<MoreAboutMeItemDto>) {
        val container = binding.layoutMoreAboutMeContainer
        container.removeAllViews()

        items.forEachIndexed { index, item ->
            val rowBinding = ItemMoreAboutMeRowBinding.inflate(
                LayoutInflater.from(this),
                container,
                false
            )
            rowBinding.tvRowEmoji.text = item.icon ?: "✨"
            rowBinding.tvRowLabel.text = item.label
            rowBinding.tvRowValue.text = item.value ?: "Not specified"
            rowBinding.viewDivider.isVisible = (index != items.lastIndex)

            rowBinding.layoutRow.setOnClickListener {
                showMoreAboutMePicker(item)
            }

            container.addView(rowBinding.root)
        }
    }

    private fun showMoreAboutMePicker(item: MoreAboutMeItemDto) {
        val options = moreAboutMeOptions[item.key] ?: arrayOf("Yes", "No", "Prefer not to say")
        val selectedIndex = options.indexOfFirst { it.equals(item.value, ignoreCase = true) }

        MaterialAlertDialogBuilder(this)
            .setTitle(item.label)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val selectedValue = options[which]
                viewModel.updateMoreAboutMe(item.key, selectedValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadLocalProfileData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sessionManager.profileImageUriFlow.collect { photoUri ->
                        if (photoUri.isNotBlank()) {
                            binding.ivPhotoPrimary.setImageURI(photoUri)
                        } else {
                            binding.ivPhotoPrimary.setActualImageResource(R.drawable.img_login)
                        }
                    }
                }
                launch {
                    sessionManager.coverPhotoUrlFlow.collect { coverUri ->
                        if (coverUri.isNotBlank()) {
                            binding.ivCoverPicture.setImageURI(coverUri)
                        } else {
                            binding.ivCoverPicture.setActualImageResource(R.drawable.img_login)
                        }
                    }
                }
                launch {
                    viewModel.userProfileState.collect { profile ->
                        if (profile != null) {
                            binding.tvCompletionPercent.text = "${profile.completionPercent}% complete"
                            if (profile.bio.isNotBlank() && (binding.etBio.text.isNullOrBlank() || !binding.etBio.hasFocus())) {
                                binding.etBio.setText(profile.bio)
                                binding.tvBioCounter.text = "${profile.bio.length}/150"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Photos State
                launch {
                    viewModel.photosState.collect { resource ->
                        if (resource is Resource.Success) {
                            currentPhotos = resource.data
                            bindPhotosToUi(resource.data)
                        } else if (resource is Resource.Error) {
                            android.util.Log.e("EditProfileActivity", "Failed to fetch photos: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                        }
                    }
                }

                // 2. Primary Photo Upload State
                launch {
                    viewModel.uploadPrimaryPhotoState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                Toast.makeText(this@EditProfileActivity, "Uploading profile photo...", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Success -> {
                                Toast.makeText(this@EditProfileActivity, "Profile photo updated", Toast.LENGTH_SHORT).show()
                                val photoUrl = resource.data.profilePicture
                                if (!photoUrl.isNullOrBlank()) {
                                    binding.ivPhotoPrimary.setImageURI(photoUrl)
                                }
                                viewModel.resetUploadPrimaryPhotoState()
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to upload primary photo: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                Toast.makeText(this@EditProfileActivity, resource.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetUploadPrimaryPhotoState()
                            }
                            null -> Unit
                        }
                    }
                }

                // 3. Audio Intro State
                launch {
                    viewModel.audioState.collect { resource ->
                        if (resource is Resource.Success) {
                            val audio = resource.data
                            currentAudioUrl = audio.effectiveUrl
                            val duration = audio.duration ?: 0
                            val min = duration / 60
                            val sec = duration % 60
                            binding.tvVoiceDuration.text = String.format("%02d:%02d", min, sec)
                        } else if (resource is Resource.Error) {
                            android.util.Log.e("EditProfileActivity", "Failed to fetch audio intro: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                        }
                    }
                }

                // 4. Upload Audio State
                launch {
                    viewModel.uploadAudioState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                Toast.makeText(this@EditProfileActivity, "Uploading audio intro...", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Success -> {
                                Toast.makeText(this@EditProfileActivity, "Voice intro uploaded successfully!", Toast.LENGTH_SHORT).show()
                                viewModel.resetUploadAudioState()
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to upload audio: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                Toast.makeText(this@EditProfileActivity, resource.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetUploadAudioState()
                            }
                            null -> Unit
                        }
                    }
                }

                // 5. Delete Audio State
                launch {
                    viewModel.deleteAudioState.collect { resource ->
                        if (resource is Resource.Success) {
                            Toast.makeText(this@EditProfileActivity, "Voice intro deleted", Toast.LENGTH_SHORT).show()
                            currentAudioUrl = null
                            binding.tvVoiceDuration.text = "00:00"
                        } else if (resource is Resource.Error) {
                            android.util.Log.e("EditProfileActivity", "Failed to delete audio: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                        }
                    }
                }

                // 6. Saved Interests State
                launch {
                    viewModel.savedInterestsState.collect { resource ->
                        if (resource is Resource.Success) {
                            val saved = resource.data
                            savedInterestIds.clear()
                            saved.forEach { it.id?.let { id -> savedInterestIds.add(id) } }

                            val uiInterests = saved.map { interest ->
                                EditInterestUiItem(
                                    id = interest.id.orEmpty(),
                                    name = interest.name.orEmpty(),
                                    emoji = interest.icon ?: getEmojiForInterest(interest.name.orEmpty())
                                )
                            }.toMutableList()
                            uiInterests.add(EditInterestUiItem(name = "Add", emoji = "➕", isAddAction = true))
                            interestsAdapter.submitList(uiInterests)
                        } else if (resource is Resource.Error) {
                            android.util.Log.e("EditProfileActivity", "Failed to fetch saved interests: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                        }
                    }
                }

                // 7. Interests Catalog State
                launch {
                    viewModel.interestsCatalogState.collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                availableCatalog = resource.data
                                showInterestsSelectionDialog(resource.data)
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to fetch interests catalog: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                Toast.makeText(this@EditProfileActivity, "Could not load interests catalog: ${resource.message}", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Loading -> Unit
                            null -> Unit
                        }
                    }
                }

                // 8. Update Interests State
                launch {
                    viewModel.updateInterestsState.collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                Toast.makeText(this@EditProfileActivity, "Interests updated successfully!", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to update interests: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                Toast.makeText(this@EditProfileActivity, "Failed to update: ${resource.message}", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Loading -> {
                                Toast.makeText(this@EditProfileActivity, "Saving interests...", Toast.LENGTH_SHORT).show()
                            }
                            null -> Unit
                        }
                    }
                }

                // 9. More About Me State
                launch {
                    viewModel.moreAboutMeState.collect { resource ->
                        if (resource is Resource.Success) {
                            bindMoreAboutMeItems(resource.data.getEffectiveItems())
                        } else if (resource is Resource.Error) {
                            android.util.Log.e("EditProfileActivity", "Failed to fetch more about me: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                        }
                    }
                }

                // 10. Update More About Me State
                launch {
                    viewModel.updateMoreAboutMeState.collect { resource ->
                        if (resource is Resource.Success) {
                            Toast.makeText(this@EditProfileActivity, "Details updated successfully!", Toast.LENGTH_SHORT).show()
                        } else if (resource is Resource.Error) {
                            android.util.Log.e("EditProfileActivity", "Failed to update more about me: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                            Toast.makeText(this@EditProfileActivity, "Failed to update: ${resource.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 11. Cover Photo Upload State
                launch {
                    viewModel.uploadCoverPhotoState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                Toast.makeText(this@EditProfileActivity, "Uploading cover photo...", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Success -> {
                                Toast.makeText(this@EditProfileActivity, "Cover photo updated successfully!", Toast.LENGTH_SHORT).show()
                                val coverUrl = resource.data
                                if (coverUrl.isNotBlank()) {
                                    binding.ivCoverPicture.setImageURI(coverUrl)
                                }
                                viewModel.resetUploadCoverPhotoState()
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to upload cover photo: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                Toast.makeText(this@EditProfileActivity, "Cover upload failed: ${resource.message}", Toast.LENGTH_SHORT).show()
                                viewModel.resetUploadCoverPhotoState()
                            }
                            null -> Unit
                        }
                    }
                }

                // 12. Set Primary Photo State
                launch {
                    viewModel.setPrimaryPhotoState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                Toast.makeText(this@EditProfileActivity, "Setting as primary photo...", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Success -> {
                                Toast.makeText(this@EditProfileActivity, "Primary photo updated successfully!", Toast.LENGTH_SHORT).show()
                                viewModel.resetSetPrimaryPhotoState()
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to set primary photo: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                Toast.makeText(this@EditProfileActivity, "Failed to update primary photo: ${resource.message}", Toast.LENGTH_SHORT).show()
                                viewModel.resetSetPrimaryPhotoState()
                            }
                            null -> Unit
                        }
                    }
                }

                // 13. Update Profile State (Bio save feedback)
                launch {
                    viewModel.updateProfileState.collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                viewModel.resetUpdateProfileState()
                            }
                            is Resource.Error -> {
                                android.util.Log.e("EditProfileActivity", "Failed to save bio: ${resource.message} | code: ${resource.code} | body: ${resource.errorBody}")
                                viewModel.resetUpdateProfileState()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun bindPhotosToUi(photos: List<UserPhotoDto>) {
        currentPhotos = photos

        // Slots 1 to 5: 5 Gallery Photos (separate from Profile Photo)
        bindSlot(
            slotIndex = 0,
            photo = photos.getOrNull(0),
            drawee = binding.ivPhoto1,
            btnDelete = binding.btnDeletePhoto1,
            placeholder = binding.ivSlot1Placeholder
        )

        bindSlot(
            slotIndex = 1,
            photo = photos.getOrNull(1),
            drawee = binding.ivPhoto2,
            btnDelete = binding.btnDeletePhoto2,
            placeholder = binding.ivSlot2Placeholder
        )

        bindSlot(
            slotIndex = 2,
            photo = photos.getOrNull(2),
            drawee = binding.ivPhoto3,
            btnDelete = binding.btnDeletePhoto3,
            placeholder = binding.ivSlot3Placeholder
        )

        bindSlot(
            slotIndex = 3,
            photo = photos.getOrNull(3),
            drawee = binding.ivPhoto4,
            btnDelete = binding.btnDeletePhoto4,
            placeholder = binding.ivSlot4Placeholder
        )

        bindSlot(
            slotIndex = 4,
            photo = photos.getOrNull(4),
            drawee = binding.ivPhoto5,
            btnDelete = binding.btnDeletePhoto5,
            placeholder = binding.ivSlot5Placeholder
        )
    }

    private fun bindSlot(
        slotIndex: Int,
        photo: UserPhotoDto?,
        drawee: com.facebook.drawee.view.SimpleDraweeView,
        btnDelete: android.widget.ImageView,
        placeholder: android.widget.ImageView
    ) {
        val slotParent = drawee.parent as? android.view.View

        if (photo != null && !photo.url.isNullOrBlank()) {
            placeholder.isVisible = false
            btnDelete.isVisible = true
            drawee.setImageURI(photo.url)
            btnDelete.setOnClickListener {
                photo.id?.let { id -> viewModel.deletePhoto(id) }
            }
            slotParent?.setOnClickListener {
                showPhotoOptionsDialog(photo, slotIndex)
            }
            slotParent?.setOnLongClickListener {
                photo.id?.let { id ->
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Set Primary Photo")
                        .setMessage("Do you want to set this photo as your primary profile photo?")
                        .setPositiveButton("Yes") { _, _ ->
                            viewModel.setPrimaryPhoto(id)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                true
            }
        } else {
            drawee.setImageURI(null as String?)
            placeholder.isVisible = true
            btnDelete.isVisible = false
            slotParent?.setOnClickListener {
                selectedGallerySlotIndex = slotIndex
                photoCropTarget = PhotoCropTarget.GALLERY_SLOT
                cropperHelper.launchGallery(CropConfig.profileAvatar(circular = false))
            }
            slotParent?.setOnLongClickListener(null)
        }
    }
}
