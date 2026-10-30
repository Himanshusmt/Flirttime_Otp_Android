package com.flirttime.feature.quickmoments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.databinding.DialogAddNewMomentBinding
import com.flirttime.feature.main.matches.MatchesRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * AddNewMomentBottomSheetDialogFragment
 * Full-screen bottom sheet dialog for creating 24-hour Quick Moments (Stories).
 * Tapping palette cycles through 10 modern gradients instantly.
 * Images icon opens device gallery directly to set photo background.
 */
class AddNewMomentBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAddNewMomentBinding? = null
    private val binding get() = _binding!!

    private val matchesRepository: MatchesRepository by inject()

    var onMomentCreated: (() -> Unit)? = null

    data class StoryGradient(
        val name: String,
        val startColorHex: String,
        val endColorHex: String
    )

    private val predefinedGradients = listOf(
        StoryGradient("Coral Red", "#E02847", "#FF5E62"),
        StoryGradient("Violet Purple", "#4A148C", "#8E24AA"),
        StoryGradient("Sunset Amber", "#E65100", "#FF9800"),
        StoryGradient("Royal Indigo", "#1A237E", "#3F51B5"),
        StoryGradient("Emerald Green", "#004D40", "#00897B"),
        StoryGradient("Rose Fuchsia", "#880E4F", "#D81B60"),
        StoryGradient("Ocean Teal", "#006064", "#00ACC1"),
        StoryGradient("Electric Blue", "#0D47A1", "#2196F3"),
        StoryGradient("Berry Wine", "#2D0B1E", "#6A1B4D"),
        StoryGradient("Midnight Dark", "#1A1A1A", "#333333")
    )

    private var selectedGradientIndex = 0
    private var selectedImageUri: Uri? = null
    private var isSubmitting = false

    private val pickVisualMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleSelectedImage(uri)
        }
    }

    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleSelectedImage(uri)
        }
    }

    override fun getTheme(): Int = R.style.FullScreenQuickMomentDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.background = null // Remove default white sheet background so our rounded shape displays
                sheet.fitsSystemWindows = false
                sheet.setPadding(0, 0, 0, 0)

                // Eliminate any coordinator-level parent fitsSystemWindows padding
                (sheet.parent as? View)?.let { parentView ->
                    parentView.fitsSystemWindows = false
                    parentView.setPadding(0, 0, 0, 0)
                }

                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false
                behavior.expandedOffset = 0 // Critical: zero top offset
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddNewMomentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWindowInsets()
        applyGradient(predefinedGradients[selectedGradientIndex])
        setupTextWatcher()
        setupActionButtons()
        updateShareButtonState()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val imeOrNavBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.ime()
            )

            // Calculate precise top offset without double padding
            val loc = IntArray(2)
            binding.rootContainer.getLocationOnScreen(loc)
            val rootY = loc[1]

            val effectiveTop = if (rootY <= 0 && statusBars.top > 0) {
                statusBars.top + dpToPx(8)
            } else {
                dpToPx(12)
            }

            binding.btnClose.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = effectiveTop
            }
            binding.layoutTopActions.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = effectiveTop
            }

            val effectiveBottom = if (imeOrNavBars.bottom > 0) {
                imeOrNavBars.bottom + dpToPx(16)
            } else {
                dpToPx(24)
            }

            binding.bottomBarLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = effectiveBottom
            }

            insets
        }

        binding.rootContainer.post {
            binding.rootContainer.requestApplyInsets()
        }
    }

    private fun applyGradient(gradient: StoryGradient) {
        val colors = intArrayOf(
            Color.parseColor(gradient.startColorHex),
            Color.parseColor(gradient.endColorHex)
        )
        val drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        binding.vGradientBg.background = drawable
    }

    private fun setupTextWatcher() {
        binding.etMomentText.addTextChangedListener { editable ->
            val length = editable?.length ?: 0
            binding.tvCharCounter.text = "$length/200"
            updateShareButtonState()
        }
    }

    private fun setupActionButtons() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // Tapping palette icon cycles automatically to the next color gradient
        binding.btnPalette.setOnClickListener {
            selectedGradientIndex = (selectedGradientIndex + 1) % predefinedGradients.size
            applyGradient(predefinedGradients[selectedGradientIndex])
        }

        // Tapping image icon opens gallery picker directly
        binding.btnPickImage.setOnClickListener {
            try {
                pickVisualMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (e: Exception) {
                Log.w("AddNewMoment", "PhotoPicker not supported, falling back to GetContent: ${e.message}")
                getContentLauncher.launch("image/*")
            }
        }

        binding.btnShare.setOnClickListener {
            if (isSubmitting) return@setOnClickListener
            submitMoment()
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        selectedImageUri = uri
        binding.ivPickedImage.setImageURI(uri)
        binding.ivPickedImage.visibility = View.VISIBLE
        binding.vImageScrim.visibility = View.VISIBLE
        updateShareButtonState()
    }

    private fun updateShareButtonState() {
        val hasText = binding.etMomentText.text?.toString()?.trim()?.isNotEmpty() == true
        val hasImage = selectedImageUri != null
        val canShare = (hasText || hasImage) && !isSubmitting

        if (canShare) {
            binding.btnShare.setBackgroundResource(R.drawable.bg_share_btn_enabled)
            binding.btnShare.isClickable = true
            binding.btnShare.isFocusable = true
            binding.tvShareLabel.setTextColor(Color.parseColor("#EA3355"))
            binding.ivShareIcon.setImageResource(R.drawable.ic_paper_plane_red)
        } else {
            binding.btnShare.setBackgroundResource(R.drawable.bg_share_btn_disabled)
            binding.btnShare.isClickable = false
            binding.btnShare.isFocusable = false
            binding.tvShareLabel.setTextColor(Color.parseColor("#80FFFFFF"))
            binding.ivShareIcon.setImageResource(R.drawable.ic_paper_plane_disabled)
        }
    }

    private fun submitMoment() {
        val text = binding.etMomentText.text?.toString()?.trim().orEmpty()
        val imageUri = selectedImageUri
        val gradient = predefinedGradients[selectedGradientIndex]

        if (text.isEmpty() && imageUri == null) {
            return
        }

        isSubmitting = true
        binding.pbShareLoading.visibility = View.VISIBLE
        binding.ivShareIcon.visibility = View.GONE
        binding.btnShare.isClickable = false
        binding.etMomentText.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            if (imageUri != null) {
                matchesRepository.createImageQuickMoment(
                    context = requireContext(),
                    imageUri = imageUri,
                    caption = text.takeIf { it.isNotBlank() }
                ).collectLatest { handleResponse(it) }
            } else {
                matchesRepository.createTextQuickMoment(
                    text = text,
                    backgroundColor = gradient.startColorHex
                ).collectLatest { handleResponse(it) }
            }
        }
    }

    private fun handleResponse(resource: Resource<com.flirttime.feature.quickmoments.QuickMomentSlideDto>) {
        when (resource) {
            is Resource.Loading -> {
                // UI already configured for loading state
            }
            is Resource.Success -> {
                isSubmitting = false
                Toast.makeText(requireContext(), "Quick Moment shared!", Toast.LENGTH_SHORT).show()
                onMomentCreated?.invoke()
                dismiss()
            }
            is Resource.Error -> {
                isSubmitting = false
                binding.pbShareLoading.visibility = View.GONE
                binding.ivShareIcon.visibility = View.VISIBLE
                binding.btnShare.isClickable = true
                binding.etMomentText.isEnabled = true
                updateShareButtonState()

                Log.e("AddNewMoment", "[submitMoment] failed: code=${resource.code} message=${resource.message} errorBody=${resource.errorBody}")
                val message = resource.message.takeIf { it.isNotBlank() } ?: "Failed to share quick moment"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(dp: Int): Int = dpToPx(dp.toFloat())

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddNewMomentSheet"
        fun newInstance() = AddNewMomentBottomSheetDialogFragment()
    }
}
