package com.flirttime.feature.moments.location

import android.Manifest
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.core.location.DeviceLocationHelper
import com.flirttime.core.location.PlaceSearchHelper
import com.flirttime.databinding.BottomSheetWhereAreYouVibingBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WhereAreYouVibingBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetWhereAreYouVibingBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: VibeCategoryAdapter
    private lateinit var spotsAdapter: VibeSpotsAdapter

    private var allSpots: List<VibeSpotItem> = emptyList()
    private var currentCategory: SpotCategory = SpotCategory.ALL
    private var currentSearchQuery: String = ""
    private var userLocation: Location? = null
    private var searchJob: Job? = null

    var onSpotSelected: ((VibeSpotItem) -> Unit)? = null
    var onCitySelected: ((String) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            loadNearbySpots()
        } else {
            Toast.makeText(
                requireContext(),
                "Allow location access to find spots near you.",
                Toast.LENGTH_LONG
            ).show()
            binding.layoutEmptySpots.isVisible = true
            binding.rvSpots.isVisible = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWhereAreYouVibingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupKeyboardAndHeightObserver()
        setupHeaderAndBanner()
        setupCategories()
        setupSpotsList()
        setupSearch()
        requestLocationAndLoad()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return

        bottomSheet.background = ColorDrawable(Color.TRANSPARENT)

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true

        val displayHeight = resources.displayMetrics.heightPixels
        val defaultHeight = (displayHeight * 0.90).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = defaultHeight
        }
    }

    private fun setupKeyboardAndHeightObserver() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            val dialog = dialog as? BottomSheetDialog
            val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val displayHeight = resources.displayMetrics.heightPixels
                val defaultHeight = (displayHeight * 0.90).toInt()

                if (isImeVisible && imeInsets.bottom > 0) {
                    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                } else {
                    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                        height = defaultHeight
                    }
                }
            }
            windowInsets
        }
    }

    private fun setupHeaderAndBanner() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnShareCity.setOnClickListener {
            shareCityOnly()
        }
    }

    private fun setupCategories() {
        val categories = MockVibeSpotsProvider.getCategories()
        categoryAdapter = VibeCategoryAdapter(categories) { selectedCategory ->
            currentCategory = selectedCategory
            if (currentSearchQuery.isBlank()) {
                loadNearbySpots()
            } else {
                filterSpots()
            }
        }

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupSpotsList() {
        spotsAdapter = VibeSpotsAdapter { spot ->
            onSpotSelected?.invoke(spot)
            dismiss()
        }

        binding.rvSpots.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = spotsAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearchSpots.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                scheduleSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchSpots.text?.clear()
        }
    }

    private fun requestLocationAndLoad() {
        if (DeviceLocationHelper.hasLocationPermission(requireContext())) {
            loadNearbySpots()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(320)
            if (currentSearchQuery.length >= 2) {
                searchPlaces(currentSearchQuery)
            } else if (currentSearchQuery.isEmpty()) {
                loadNearbySpots()
            } else {
                filterSpots()
            }
        }
    }

    private fun loadNearbySpots() {
        viewLifecycleOwner.lifecycleScope.launch {
            setSpotsLoading(true)
            val location = userLocation ?: DeviceLocationHelper.getCurrentLocation(requireContext())
            userLocation = location
            if (location == null) {
                setSpotsLoading(false)
                allSpots = emptyList()
                filterSpots()
                Toast.makeText(
                    requireContext(),
                    "Couldn't get your location. Try searching instead.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            allSpots = PlaceSearchHelper.searchNearby(
                latitude = location.latitude,
                longitude = location.longitude,
                category = currentCategory
            )
            setSpotsLoading(false)
            filterSpots()
        }
    }

    private fun searchPlaces(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            setSpotsLoading(true)
            val location = userLocation ?: DeviceLocationHelper.getCurrentLocation(requireContext())
            userLocation = location
            allSpots = PlaceSearchHelper.searchText(
                query = query,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            setSpotsLoading(false)
            filterSpots()
        }
    }

    private fun shareCityOnly() {
        if (!DeviceLocationHelper.hasLocationPermission(requireContext())) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            setShareCityLoading(true)
            val location = userLocation ?: DeviceLocationHelper.getCurrentLocation(requireContext())
            userLocation = location
            if (location == null) {
                setShareCityLoading(false)
                Toast.makeText(
                    requireContext(),
                    "Couldn't get your location. Try searching instead.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val address = DeviceLocationHelper.reverseGeocode(
                requireContext(),
                location.latitude,
                location.longitude
            )
            val city = address?.let { DeviceLocationHelper.toCityOnly(it) }
            setShareCityLoading(false)
            if (city.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Couldn't find your city. Try searching instead.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            onCitySelected?.invoke(city)
            dismiss()
        }
    }

    private fun filterSpots() {
        val filtered = allSpots.filter { spot ->
            val matchesCategory = currentCategory == SpotCategory.ALL ||
                spot.category == currentCategory ||
                spot.category == SpotCategory.ALL
            val matchesSearch = if (currentSearchQuery.isEmpty()) {
                true
            } else {
                spot.name.contains(currentSearchQuery, ignoreCase = true) ||
                    spot.address.contains(currentSearchQuery, ignoreCase = true) ||
                    spot.city.orEmpty().contains(currentSearchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }

        spotsAdapter.submitList(filtered)

        val isEmpty = filtered.isEmpty() && !binding.progressSpots.isVisible
        binding.rvSpots.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.layoutEmptySpots.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun setSpotsLoading(loading: Boolean) {
        binding.progressSpots.isVisible = loading
        if (loading) {
            binding.rvSpots.isVisible = false
            binding.layoutEmptySpots.isVisible = false
        }
    }

    private fun setShareCityLoading(loading: Boolean) {
        binding.progressShareCity.isVisible = loading
        binding.ivShareCityChevron.isVisible = !loading
        binding.btnShareCity.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }

    companion object {
        const val TAG = "WhereAreYouVibingBottomSheet"

        fun newInstance(): WhereAreYouVibingBottomSheetDialogFragment {
            return WhereAreYouVibingBottomSheetDialogFragment()
        }
    }
}
