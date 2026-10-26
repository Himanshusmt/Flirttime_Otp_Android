package com.flirttime.feature.main.presentation.matches.filter

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.flirttime.R
import com.flirttime.databinding.BottomSheetFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

enum class GenderFilter {
    WOMEN,
    MEN,
    NON_BINARY,
    EVERYONE
}

class FiltersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    private var selectedGender: GenderFilter = GenderFilter.EVERYONE
    private var minAge: Int = 18
    private var maxAge: Int = 70
    private var distanceKm: Int = 100 // 100 means Anywhere

    var onApplyFilters: ((gender: GenderFilter, minAge: Int, maxAge: Int, distanceKm: Int) -> Unit)? = null

    override fun getTheme(): Int = R.style.FlirtTimeBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleBottomNavigationInsets()
        setupGenderOptions()
        setupAgeRangeSlider()
        setupDistanceSlider()
        setupLookingForAndInterests()
        setupActions()
        updateGenderUi(selectedGender)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightNavigationBars = true
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.background = ColorDrawable(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    private fun handleBottomNavigationInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottomActions) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val basePaddingBottom = (24 * resources.displayMetrics.density).toInt()
            v.updatePadding(bottom = basePaddingBottom + navInsets.bottom)
            insets
        }
    }

    private fun setupGenderOptions() {
        binding.optGenderWomen.setOnClickListener {
            updateGenderUi(GenderFilter.WOMEN)
        }
        binding.optGenderMen.setOnClickListener {
            updateGenderUi(GenderFilter.MEN)
        }
        binding.optGenderNonBinary.setOnClickListener {
            updateGenderUi(GenderFilter.NON_BINARY)
        }
        binding.optGenderEveryone.setOnClickListener {
            updateGenderUi(GenderFilter.EVERYONE)
        }
    }

    private fun updateGenderUi(gender: GenderFilter) {
        selectedGender = gender

        val brandRed = ContextCompat.getColor(requireContext(), R.color.brand_red)
        val normalText = Color.parseColor("#18181B")
        val semibold = ResourcesCompat.getFont(requireContext(), R.font.fredokasemibold)

        bindGenderOption(binding.optGenderWomen, binding.tvGenderWomen, gender == GenderFilter.WOMEN, brandRed, normalText, semibold)
        bindGenderOption(binding.optGenderMen, binding.tvGenderMen, gender == GenderFilter.MEN, brandRed, normalText, semibold)
        bindGenderOption(binding.optGenderNonBinary, binding.tvGenderNonBinary, gender == GenderFilter.NON_BINARY, brandRed, normalText, semibold)
        bindGenderOption(binding.optGenderEveryone, binding.tvGenderEveryone, gender == GenderFilter.EVERYONE, brandRed, normalText, semibold)
    }

    private fun bindGenderOption(
        container: LinearLayout,
        textView: TextView,
        isSelected: Boolean,
        activeColor: Int,
        inactiveColor: Int,
        font: android.graphics.Typeface?
    ) {
        if (isSelected) {
            container.setBackgroundResource(R.drawable.bg_filter_option_selected)
            textView.setTextColor(activeColor)
        } else {
            container.setBackgroundResource(R.drawable.bg_filter_option_unselected)
            textView.setTextColor(inactiveColor)
        }
        textView.typeface = font
    }

    private fun setupAgeRangeSlider() {
        val thumbRadiusPx = (11 * resources.displayMetrics.density).toInt()
        binding.sliderAgeRange.thumbRadius = thumbRadiusPx
        binding.sliderAgeRange.setCustomThumbDrawable(R.drawable.bg_slider_thumb_ring)
        binding.sliderAgeRange.setValues(minAge.toFloat(), maxAge.toFloat())
        binding.sliderAgeRange.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            if (values.size >= 2) {
                minAge = kotlin.math.round(values[0]).toInt()
                maxAge = kotlin.math.round(values[1]).toInt()
                val maxLabel = if (maxAge >= 70) "70+" else maxAge.toString()
                binding.tvAgeRangeValue.text = "$minAge – $maxLabel"
            }
        }
    }

    private fun setupDistanceSlider() {
        val thumbRadiusPx = (11 * resources.displayMetrics.density).toInt()
        binding.sliderDistance.thumbRadius = thumbRadiusPx
        binding.sliderDistance.setCustomThumbDrawable(R.drawable.bg_slider_thumb_ring)
        binding.sliderDistance.value = distanceKm.toFloat()
        binding.sliderDistance.addOnChangeListener { _, value, _ ->
            distanceKm = kotlin.math.round(value).toInt()
            binding.tvDistanceValue.text = if (distanceKm >= 100) "Anywhere" else "$distanceKm km"
        }
    }

    private fun setupLookingForAndInterests() {
        binding.cgInterests.setOnCheckedStateChangeListener { _, checkedIds ->
            val count = checkedIds.size
            if (count == 0) {
                binding.tvInterestsValue.text = "Any"
                binding.tvInterestsValue.setTextColor(Color.parseColor("#6B7280"))
            } else {
                binding.tvInterestsValue.text = count.toString()
                binding.tvInterestsValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_red))
            }
        }
    }

    private fun setupActions() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnReset.setOnClickListener {
            resetFilters()
        }

        binding.btnShowMatches.setOnClickListener {
            onApplyFilters?.invoke(selectedGender, minAge, maxAge, distanceKm)
            dismiss()
        }
    }

    private fun resetFilters() {
        updateGenderUi(GenderFilter.EVERYONE)
        minAge = 18
        maxAge = 70
        binding.sliderAgeRange.setValues(18f, 70f)
        binding.tvAgeRangeValue.text = "18 – 70+"

        distanceKm = 100
        binding.sliderDistance.value = 100f
        binding.tvDistanceValue.text = "Anywhere"

        binding.cgLookingFor.clearCheck()
        binding.cgInterests.clearCheck()
        binding.tvInterestsValue.text = "Any"
        binding.tvInterestsValue.setTextColor(Color.parseColor("#6B7280"))

        binding.switchVerifiedOnly.isChecked = false
        binding.switchOnlineNow.isChecked = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FiltersBottomSheetDialogFragment"
    }
}
