package com.flirttime.feature.main.matches

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.databinding.BottomSheetFiltersBinding
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.profile.UserInterestDto
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

enum class GenderFilter {
    WOMEN,
    MEN,
    NON_BINARY,
    EVERYONE
}

class FiltersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    private val matchesRepository: MatchesRepository by inject()
    private val mainRepository: MainRepository by inject()

    var initialFilters: DiscoveryFiltersDto? = null

    private var selectedGender: GenderFilter = GenderFilter.EVERYONE
    private var minAge: Int = 18
    private var maxAge: Int = 70
    private var distanceKm: Int = 100 // 100 means Anywhere

    private var availableInterests: List<UserInterestDto> = emptyList()
    private val pendingSelectedInterestIds = mutableSetOf<String>()

    private var countDebounceJob: Job? = null

    var onApplyFilters: ((DiscoveryFiltersDto) -> Unit)? = null
    var onResetFilters: (() -> Unit)? = null

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
        setupLookingFor()
        setupSwitches()
        setupActions()

        // Apply prefilled or saved filters
        val preset = initialFilters
        if (preset != null) {
            applyFiltersToUi(preset)
        } else {
            loadSavedFiltersFromBackend()
        }

        // Load dynamic interests catalog from API
        loadInterestsCatalog()
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
            requestDynamicCountUpdate()
        }
        binding.optGenderMen.setOnClickListener {
            updateGenderUi(GenderFilter.MEN)
            requestDynamicCountUpdate()
        }
        binding.optGenderNonBinary.setOnClickListener {
            updateGenderUi(GenderFilter.NON_BINARY)
            requestDynamicCountUpdate()
        }
        binding.optGenderEveryone.setOnClickListener {
            updateGenderUi(GenderFilter.EVERYONE)
            requestDynamicCountUpdate()
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
        binding.sliderAgeRange.addOnChangeListener { slider, _, fromUser ->
            val values = slider.values
            if (values.size >= 2) {
                minAge = kotlin.math.round(values[0]).toInt()
                maxAge = kotlin.math.round(values[1]).toInt()
                val maxLabel = if (maxAge >= 70) "70+" else maxAge.toString()
                binding.tvAgeRangeValue.text = "$minAge – $maxLabel"
                if (fromUser) requestDynamicCountUpdate()
            }
        }
    }

    private fun setupDistanceSlider() {
        val thumbRadiusPx = (11 * resources.displayMetrics.density).toInt()
        binding.sliderDistance.thumbRadius = thumbRadiusPx
        binding.sliderDistance.setCustomThumbDrawable(R.drawable.bg_slider_thumb_ring)
        binding.sliderDistance.value = distanceKm.toFloat()
        binding.sliderDistance.addOnChangeListener { _, value, fromUser ->
            distanceKm = kotlin.math.round(value).toInt()
            binding.tvDistanceValue.text = if (distanceKm >= 100) "Anywhere" else "$distanceKm km"
            if (fromUser) requestDynamicCountUpdate()
        }
    }

    private fun setupLookingFor() {
        binding.cgLookingFor.setOnCheckedStateChangeListener { _, _ ->
            requestDynamicCountUpdate()
        }
    }

    private fun setupSwitches() {
        binding.switchVerifiedOnly.setOnCheckedChangeListener { _, _ ->
            requestDynamicCountUpdate()
        }
        binding.switchOnlineNow.setOnCheckedChangeListener { _, _ ->
            requestDynamicCountUpdate()
        }
    }

    private fun loadInterestsCatalog() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainRepository.getInterestsCatalog().collect { resource ->
                if (resource is Resource.Success && resource.data != null) {
                    availableInterests = resource.data
                    populateInterestsChips(availableInterests)
                }
            }
        }
    }

    private fun populateInterestsChips(interests: List<UserInterestDto>) {
        val group = binding.cgInterests
        group.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        interests.forEach { interest ->
            val chip = inflater.inflate(R.layout.item_filter_chip, group, false) as Chip
            val emoji = interest.effectiveEmoji
            val name = interest.name.orEmpty()
            chip.text = if (emoji.isNotBlank()) "$emoji $name" else name
            chip.tag = interest.effectiveId
            chip.isCheckable = true
            chip.isChecked = pendingSelectedInterestIds.contains(interest.effectiveId)

            chip.setOnCheckedChangeListener { _, isChecked ->
                val id = interest.effectiveId
                if (isChecked) {
                    pendingSelectedInterestIds.add(id)
                } else {
                    pendingSelectedInterestIds.remove(id)
                }
                updateInterestsCountLabel()
                requestDynamicCountUpdate()
            }

            group.addView(chip)
        }
        updateInterestsCountLabel()
    }

    private fun updateInterestsCountLabel() {
        val count = pendingSelectedInterestIds.size
        if (count == 0) {
            binding.tvInterestsValue.text = "Any"
            binding.tvInterestsValue.setTextColor(Color.parseColor("#6B7280"))
        } else {
            binding.tvInterestsValue.text = count.toString()
            binding.tvInterestsValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_red))
        }
    }

    private fun loadSavedFiltersFromBackend() {
        viewLifecycleOwner.lifecycleScope.launch {
            matchesRepository.getDiscoveryFilters().collect { resource ->
                if (resource is Resource.Success && resource.data != null) {
                    applyFiltersToUi(resource.data)
                }
            }
        }
    }

    private fun applyFiltersToUi(filters: DiscoveryFiltersDto) {
        // 1. Gender
        val genders = filters.genders
        val mappedGender = when {
            genders?.contains("female") == true -> GenderFilter.WOMEN
            genders?.contains("male") == true -> GenderFilter.MEN
            genders?.contains("non_binary") == true -> GenderFilter.NON_BINARY
            else -> GenderFilter.EVERYONE
        }
        updateGenderUi(mappedGender)

        // 2. Age Range
        minAge = filters.ageMin ?: 18
        maxAge = filters.ageMax ?: 70
        binding.sliderAgeRange.setValues(minAge.toFloat().coerceAtLeast(18f), maxAge.toFloat().coerceAtMost(70f))
        val maxLabel = if (maxAge >= 70) "70+" else maxAge.toString()
        binding.tvAgeRangeValue.text = "$minAge – $maxLabel"

        // 3. Distance
        distanceKm = filters.maxDistanceKm ?: 100
        val clampedDistance = distanceKm.toFloat().coerceIn(1f, 100f)
        binding.sliderDistance.value = clampedDistance
        binding.tvDistanceValue.text = if (distanceKm >= 100) "Anywhere" else "$distanceKm km"

        // 4. Looking For
        val lookingForList = filters.lookingFor.orEmpty()
        binding.chipLookingForLongTerm.isChecked = lookingForList.contains("long_term")
        binding.chipLookingForCasual.isChecked = lookingForList.contains("casual")
        binding.chipLookingForNotSure.isChecked = lookingForList.contains("not_sure")

        // 5. Interests
        pendingSelectedInterestIds.clear()
        filters.interestIds?.let { pendingSelectedInterestIds.addAll(it) }
        for (i in 0 until binding.cgInterests.childCount) {
            val chip = binding.cgInterests.getChildAt(i) as? Chip
            val id = chip?.tag as? String
            chip?.isChecked = id != null && pendingSelectedInterestIds.contains(id)
        }
        updateInterestsCountLabel()

        // 6. Switches
        binding.switchVerifiedOnly.isChecked = filters.verifiedOnly == true
        binding.switchOnlineNow.isChecked = filters.onlineOnly == true

        requestDynamicCountUpdate()
    }

    private fun setupActions() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnReset.setOnClickListener {
            resetFilters()
            onResetFilters?.invoke()
        }

        binding.btnShowMatches.setOnClickListener {
            val filtersDto = buildCurrentFiltersDto()
            onApplyFilters?.invoke(filtersDto)
            dismiss()
        }
    }

    private fun buildCurrentFiltersDto(): DiscoveryFiltersDto {
        val gendersList = when (selectedGender) {
            GenderFilter.WOMEN -> listOf("female")
            GenderFilter.MEN -> listOf("male")
            GenderFilter.NON_BINARY -> listOf("non_binary")
            GenderFilter.EVERYONE -> null
        }
        val maxDist = if (distanceKm >= 100) null else distanceKm

        val lookingForList = mutableListOf<String>()
        if (binding.chipLookingForLongTerm.isChecked) lookingForList.add("long_term")
        if (binding.chipLookingForCasual.isChecked) lookingForList.add("casual")
        if (binding.chipLookingForNotSure.isChecked) lookingForList.add("not_sure")

        val interestIdsList = pendingSelectedInterestIds.toList().takeIf { it.isNotEmpty() }

        return DiscoveryFiltersDto(
            genders = gendersList,
            ageMin = minAge,
            ageMax = if (maxAge >= 70) 70 else maxAge,
            maxDistanceKm = maxDist,
            lookingFor = lookingForList.takeIf { it.isNotEmpty() },
            interestIds = interestIdsList,
            verifiedOnly = binding.switchVerifiedOnly.isChecked,
            onlineOnly = binding.switchOnlineNow.isChecked
        )
    }

    private fun requestDynamicCountUpdate() {
        countDebounceJob?.cancel()
        countDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300.milliseconds)
            val filters = buildCurrentFiltersDto()
            Log.d("FiltersBottomSheetDialogFragment", "requestDynamicCountUpdate: $filters")
            matchesRepository.searchDiscoveryCards(page = 1, limit = 1, countOnly = true, filters = filters)
                .collect { resource ->
                    if (_binding == null) return@collect
                    if (resource is Resource.Success && resource.data != null) {
                        val count = resource.data.pagination?.totalCount ?: 0
                        binding.btnShowMatches.text = if (count > 0) "Show $count matches" else "Show Matches"
                    }
                }
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
        pendingSelectedInterestIds.clear()
        updateInterestsCountLabel()

        binding.switchVerifiedOnly.isChecked = false
        binding.switchOnlineNow.isChecked = false

        binding.btnShowMatches.text = "Show Matches"
        requestDynamicCountUpdate()
    }

    override fun onDestroyView() {
        countDebounceJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FiltersBottomSheetDialogFragment"

        fun newInstance(filters: DiscoveryFiltersDto? = null): FiltersBottomSheetDialogFragment {
            return FiltersBottomSheetDialogFragment().apply {
                this.initialFilters = filters
            }
        }
    }
}
