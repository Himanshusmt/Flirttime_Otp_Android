package com.flirttime.feature.profile.settings

import android.Manifest
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.core.location.CitySuggestion
import com.flirttime.core.location.DeviceLocationHelper
import com.flirttime.core.location.PlaceSearchHelper
import com.flirttime.core.location.ResolvedPlace
import com.flirttime.databinding.BottomSheetEditLocationBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditLocationBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditLocationBinding? = null
    private val binding get() = _binding!!

    private lateinit var suggestionsAdapter: CitySuggestionAdapter
    private var searchJob: Job? = null

    var onPlaceResolved: ((ResolvedPlace) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            useCurrentLocation()
        } else {
            Toast.makeText(
                requireContext(),
                "Allow location access in Settings to use your current location.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = BottomSheetEditLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        suggestionsAdapter = CitySuggestionAdapter { suggestion ->
            resolveSuggestion(suggestion)
        }
        binding.rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuggestions.adapter = suggestionsAdapter

        binding.btnUseCurrentLocation.setOnClickListener {
            requestCurrentLocation()
        }
        binding.btnSaveCity.setOnClickListener {
            saveTypedCity()
        }
        binding.etCity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleSearch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateSaveEnabled()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState) as BottomSheetDialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.background = ColorDrawable(Color.TRANSPARENT)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    private fun requestCurrentLocation() {
        if (DeviceLocationHelper.hasLocationPermission(requireContext())) {
            useCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun useCurrentLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            setBusy(true)
            val location = DeviceLocationHelper.getCurrentLocation(requireContext())
            if (location == null) {
                setBusy(false)
                Toast.makeText(
                    requireContext(),
                    "Couldn't find your location. Please try again or enter a city.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val address = DeviceLocationHelper.reverseGeocode(
                requireContext(),
                location.latitude,
                location.longitude
            )
            val place = address?.let { DeviceLocationHelper.toResolvedPlace(it, location) }
            setBusy(false)
            if (place == null) {
                Toast.makeText(
                    requireContext(),
                    "Couldn't find a city for that place. Please enter a city.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            finishWith(place)
        }
    }

    private fun saveTypedCity() {
        val query = binding.etCity.text?.toString()?.trim().orEmpty()
        if (query.length < 2) {
            Toast.makeText(requireContext(), "Please enter a city.", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            setBusy(true)
            val addresses = DeviceLocationHelper.searchAddresses(requireContext(), query, 1)
            val place = addresses.firstOrNull()?.let { DeviceLocationHelper.toResolvedPlace(it) }
            setBusy(false)
            if (place == null) {
                Toast.makeText(
                    requireContext(),
                    "We couldn't find \"$query\". Please check the spelling.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            finishWith(place)
        }
    }

    private fun resolveSuggestion(suggestion: CitySuggestion) {
        finishWith(
            ResolvedPlace(
                label = suggestion.subtitle,
                city = suggestion.title,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude
            )
        )
    }

    private fun scheduleSearch(raw: String) {
        updateSaveEnabled()
        searchJob?.cancel()
        val query = raw.trim()
        if (query.length < 2) {
            binding.rvSuggestions.isVisible = false
            suggestionsAdapter.submitList(emptyList())
            return
        }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(280)
            val addresses = DeviceLocationHelper.searchAddresses(requireContext(), query, 5)
            val suggestions = addresses.mapNotNull(PlaceSearchHelper::toCitySuggestion)
                .distinctBy { it.subtitle.lowercase() }
            suggestionsAdapter.submitList(suggestions)
            binding.rvSuggestions.isVisible = suggestions.isNotEmpty()
        }
    }

    private fun finishWith(place: ResolvedPlace) {
        onPlaceResolved?.invoke(place)
        dismiss()
    }

    private fun setBusy(busy: Boolean) {
        binding.layoutBusy.isVisible = busy
    }

    private fun updateSaveEnabled() {
        binding.btnSaveCity.isEnabled = binding.etCity.text?.toString()?.trim().orEmpty().length >= 2
        binding.btnSaveCity.alpha = if (binding.btnSaveCity.isEnabled) 1f else 0.5f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }

    companion object {
        const val TAG = "EditLocationBottomSheet"

        fun newInstance(): EditLocationBottomSheetDialogFragment {
            return EditLocationBottomSheetDialogFragment()
        }
    }
}
