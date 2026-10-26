package com.flirttime.feature.main.presentation.moments.add

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.ShimmerHelper
import java.io.File
import com.flirttime.databinding.ActivityAddMomentBinding
import com.flirttime.feature.main.presentation.moments.location.WhereAreYouVibingBottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AddMomentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMomentBinding
    private val viewModel: AddMomentViewModel by viewModel()
    private val selectedPhotos = mutableListOf<Uri>()
    private var selectedLocation: String? = null
    private var pendingCameraUri: Uri? = null
    private var replacingIndex: Int? = null

    private val cropLauncher = registerForActivityResult(com.flirttime.core.image.FlirtTimeCropContract()) { result ->
        when (result) {
            is com.flirttime.core.image.CropResult.Success -> {
                val index = replacingIndex
                if (index != null && index in selectedPhotos.indices) {
                    selectedPhotos[index] = result.croppedUri
                    replacingIndex = null
                } else {
                    val availableSlots = MAX_PHOTOS - selectedPhotos.size
                    val toAdd = result.croppedUris.take(availableSlots)
                    selectedPhotos.addAll(toAdd)
                }
                updatePhotosUi()
                validateShareButton()
            }
            is com.flirttime.core.image.CropResult.Cancelled -> {
                replacingIndex = null
            }
            is com.flirttime.core.image.CropResult.Failure -> {
                replacingIndex = null
                Toast.makeText(this, result.error?.localizedMessage ?: "Cropping failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val availableSlots = MAX_PHOTOS - selectedPhotos.size
            val toCrop = uris.take(availableSlots)
            cropLauncher.launch(
                com.flirttime.core.image.CropRequest(
                    sourceUris = toCrop,
                    config = com.flirttime.core.image.CropConfig.momentPost()
                )
            )
        }
    }

    private val pickSingleMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cropLauncher.launch(
                com.flirttime.core.image.CropRequest(
                    sourceUri = uri,
                    config = com.flirttime.core.image.CropConfig.momentPost()
                )
            )
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            cropLauncher.launch(
                com.flirttime.core.image.CropRequest(
                    sourceUri = uri,
                    config = com.flirttime.core.image.CropConfig.momentPost()
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)

        binding = ActivityAddMomentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupUserHeader()
        setupGallery()
        setupListeners()
        updatePhotosUi()
        validateShareButton()
        observePublishState()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            binding.layoutHeader.updatePadding(top = statusBarInsets.top + 8)
            binding.layoutBottomActions.updatePadding(bottom = navBarInsets.bottom + 16)

            windowInsets
        }
    }

    private fun setupUserHeader() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.profileImageUriFlow.collect { uri ->
                        if (uri.isNotBlank()) {
                            ShimmerHelper.loadWithShimmer(binding.ivUserAvatar, uri)
                        }
                    }
                }
                launch {
                    viewModel.nickNameFlow.collect { nickname ->
                        if (nickname.isNotBlank()) {
                            binding.tvUsername.text = nickname
                        }
                    }
                }
                launch {
                    viewModel.firstNameFlow.collect { firstName ->
                        if (binding.tvUsername.text.isNullOrBlank() && firstName.isNotBlank()) {
                            binding.tvUsername.text = firstName
                        }
                    }
                }
            }
        }
    }

    private fun setupGallery() {
        binding.gallerySelectedPhotos.apply {
            isEditable = true
            onReplaceClickListener = { index, _ ->
                showPhotoSourceBottomSheet(targetIndex = index)
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDiscard.setOnClickListener {
            finish()
        }

        binding.btnSelectPhotos.setOnClickListener {
            showPhotoSourceBottomSheet()
        }

        binding.btnAddMorePhotos.setOnClickListener {
            showPhotoSourceBottomSheet()
        }

        binding.btnAllSet.setOnClickListener {
            Toast.makeText(this, "Maximum $MAX_PHOTOS photos selected", Toast.LENGTH_SHORT).show()
        }

        binding.layoutAddLocation.setOnClickListener {
            openLocationBottomSheet()
        }

        binding.btnRemoveLocation.setOnClickListener {
            selectedLocation = null
            updateLocationUi()
        }

        binding.btnChangeLocation.setOnClickListener {
            openLocationBottomSheet()
        }

        binding.layoutPrivacy.setOnClickListener {
            Toast.makeText(this, "Visibility: Everyone", Toast.LENGTH_SHORT).show()
        }

        binding.etCaption.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                binding.tvCharCount.text = "$length/$MAX_CHARACTERS"
                validateShareButton()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnShare.setOnClickListener {
            publishMoment()
        }
    }

    private fun showPhotoSourceBottomSheet(targetIndex: Int? = null) {
        if (targetIndex == null && selectedPhotos.size >= MAX_PHOTOS) {
            Toast.makeText(this, "You have reached the maximum of 5 photos", Toast.LENGTH_SHORT).show()
            return
        }

        replacingIndex = targetIndex

        val bottomSheet = PhotoSourceBottomSheetDialogFragment.newInstance(
            onTakePhoto = {
                launchCameraCropper()
            },
            onChooseLibrary = {
                launchGalleryCropper()
            }
        )
        bottomSheet.show(supportFragmentManager, PhotoSourceBottomSheetDialogFragment.TAG)
    }

    private fun launchCameraCropper() {
        try {
            val photosFolder = File(cacheDir, "moment_photos").apply { mkdirs() }
            val photoFile = File(photosFolder, "moment_${System.currentTimeMillis()}.jpg")
            if (!photoFile.exists()) {
                photoFile.createNewFile()
            }
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unable to launch camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGalleryCropper() {
        if (replacingIndex != null) {
            pickSingleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            val remainingSlots = MAX_PHOTOS - selectedPhotos.size
            if (remainingSlots > 0) {
                pickMultipleMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    private fun openLocationBottomSheet() {
        val locationSheet = WhereAreYouVibingBottomSheetDialogFragment()
        locationSheet.onSpotSelected = { spot ->
            selectedLocation = "${spot.name}, ${spot.address}"
            updateLocationUi()
        }
        locationSheet.onCitySelected = { city ->
            selectedLocation = city
            updateLocationUi()
        }
        locationSheet.show(supportFragmentManager, "WhereAreYouVibingBottomSheetDialogFragment")
    }

    private fun updateLocationUi() {
        if (!selectedLocation.isNullOrBlank()) {
            binding.layoutAddLocation.visibility = View.GONE
            binding.layoutSelectedLocationRow.visibility = View.VISIBLE
            binding.tvSelectedLocationName.text = selectedLocation
        } else {
            binding.layoutAddLocation.visibility = View.VISIBLE
            binding.layoutSelectedLocationRow.visibility = View.GONE
        }
    }

    private fun updatePhotosUi() {
        if (selectedPhotos.isEmpty()) {
            binding.layoutUploadCard.visibility = View.VISIBLE
            binding.layoutSelectedGalleryContainer.visibility = View.GONE
        } else {
            binding.layoutUploadCard.visibility = View.GONE
            binding.layoutSelectedGalleryContainer.visibility = View.VISIBLE
            binding.gallerySelectedPhotos.setImages(selectedPhotos.toList())

            val count = selectedPhotos.size
            binding.tvPhotoCount.text = "$count of $MAX_PHOTOS photos"

            val dashes = listOf(
                binding.dash1,
                binding.dash2,
                binding.dash3,
                binding.dash4,
                binding.dash5
            )
            dashes.forEachIndexed { index, dashView ->
                val bgRes = if (index < count) {
                    R.drawable.bg_dash_indicator_active
                } else {
                    R.drawable.bg_dash_indicator_inactive
                }
                dashView.setBackgroundResource(bgRes)
            }

            if (count >= MAX_PHOTOS) {
                binding.btnAddMorePhotos.visibility = View.GONE
                binding.btnAllSet.visibility = View.VISIBLE
            } else {
                binding.btnAddMorePhotos.visibility = View.VISIBLE
                binding.btnAllSet.visibility = View.GONE
            }
        }
    }

    private fun validateShareButton() {
        val hasPhotos = selectedPhotos.isNotEmpty()
        binding.btnShare.isEnabled = hasPhotos
        if (hasPhotos) {
            binding.btnShare.setBackgroundResource(R.drawable.bg_btn_pill_red)
        } else {
            binding.btnShare.setBackgroundResource(R.drawable.bg_btn_pill_salmon_inactive)
        }
    }

    private fun publishMoment() {
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "Please select at least 1 photo for your moment", Toast.LENGTH_SHORT).show()
            return
        }

        val caption = binding.etCaption.text?.toString()?.trim().orEmpty()
        val audience = "everyone"
        val location = selectedLocation

        viewModel.publishMoment(
            context = this,
            caption = caption,
            audience = audience,
            location = location,
            imageUris = selectedPhotos.toList()
        )
    }

    private fun observePublishState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publishState.collect { state ->
                    when (state) {
                        is Resource.Loading -> {
                            binding.btnShare.isEnabled = false
                            binding.btnShare.text = "Sharing..."
                        }
                        is Resource.Success -> {
                            binding.btnShare.isEnabled = true
                            binding.btnShare.text = "Share"
                            Toast.makeText(this@AddMomentActivity, "Moment shared successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        is Resource.Error -> {
                            binding.btnShare.isEnabled = true
                            binding.btnShare.text = "Share"
                            Toast.makeText(this@AddMomentActivity, state.message ?: "Failed to share moment", Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                            validateShareButton()
                        }
                        null -> {
                            binding.btnShare.text = "Share"
                            validateShareButton()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_PHOTOS = 5
        private const val MAX_CHARACTERS = 500
    }
}
