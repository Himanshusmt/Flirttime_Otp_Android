package com.flirttime.feature.main.presentation.moments.location

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.databinding.BottomSheetWhereAreYouVibingBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class WhereAreYouVibingBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetWhereAreYouVibingBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: VibeCategoryAdapter
    private lateinit var spotsAdapter: VibeSpotsAdapter

    private var allSpots: List<VibeSpotItem> = emptyList()
    private var currentCategory: SpotCategory = SpotCategory.ALL
    private var currentSearchQuery: String = ""

    var onSpotSelected: ((VibeSpotItem) -> Unit)? = null
    var onCitySelected: ((String) -> Unit)? = null

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
        loadData()
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
            onCitySelected?.invoke("Indore")
            Toast.makeText(requireContext(), "Shared City: Indore", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setupCategories() {
        val categories = MockVibeSpotsProvider.getCategories()
        categoryAdapter = VibeCategoryAdapter(categories) { selectedCategory ->
            currentCategory = selectedCategory
            filterSpots()
        }

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupSpotsList() {
        spotsAdapter = VibeSpotsAdapter { spot ->
            onSpotSelected?.invoke(spot)
            Toast.makeText(requireContext(), "Tagged spot: ${spot.name}", Toast.LENGTH_SHORT).show()
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
                filterSpots()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchSpots.text?.clear()
        }
    }

    private fun loadData() {
        allSpots = MockVibeSpotsProvider.getMockSpots()
        filterSpots()
    }

    private fun filterSpots() {
        val filtered = allSpots.filter { spot ->
            val matchesCategory = (currentCategory == SpotCategory.ALL || spot.category == currentCategory)
            val matchesSearch = if (currentSearchQuery.isEmpty()) {
                true
            } else {
                spot.name.contains(currentSearchQuery, ignoreCase = true) ||
                        spot.address.contains(currentSearchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }

        spotsAdapter.submitList(filtered)

        val isEmpty = filtered.isEmpty()
        binding.rvSpots.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.layoutEmptySpots.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WhereAreYouVibingBottomSheet"

        fun newInstance(): WhereAreYouVibingBottomSheetDialogFragment {
            return WhereAreYouVibingBottomSheetDialogFragment()
        }
    }
}
