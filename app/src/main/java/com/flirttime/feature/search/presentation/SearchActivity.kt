package com.flirttime.feature.search.presentation

import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.ActivitySearchBinding
import com.flirttime.feature.main.presentation.explore.ExploreProfileAdapter
import com.flirttime.feature.search.data.MockSearchDataProvider

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var searchAdapter: ExploreProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupHeader()
        setupGrid()
        setupSearchInput()

        // Initialize with default search matching the reference design ("tush")
        binding.etSearch.setText("tush")
        binding.etSearch.setSelection(binding.etSearch.text.length)
        performSearch("tush")
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootSearch) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.layoutHeader.updatePadding(top = statusBarInsets.top + dpToPx(8f))
            binding.rvSearchResults.updatePadding(bottom = navBarInsets.bottom + dpToPx(16f))

            insets
        }
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupGrid() {
        searchAdapter = ExploreProfileAdapter { profile ->
            Toast.makeText(this, "Selected ${profile.username}", Toast.LENGTH_SHORT).show()
        }

        val spacing = dpToPx(12f)
        binding.rvSearchResults.apply {
            layoutManager = GridLayoutManager(this@SearchActivity, 2)
            adapter = searchAdapter
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position == RecyclerView.NO_POSITION) return

                    val column = position % 2
                    if (column == 0) {
                        outRect.right = spacing / 2
                    } else {
                        outRect.left = spacing / 2
                    }
                    outRect.bottom = spacing
                }
            })
        }
    }

    private fun setupSearchInput() {
        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            performSearch("")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun performSearch(query: String) {
        val results = MockSearchDataProvider.searchProfiles(query)
        searchAdapter.submitList(results)

        if (query.isNotEmpty()) {
            binding.tvResultsCount.visibility = View.VISIBLE
            binding.tvResultsCount.text = "${results.size} results for “$query”"
        } else {
            binding.tvResultsCount.visibility = View.VISIBLE
            binding.tvResultsCount.text = "Popular Profiles"
        }

        if (results.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvSearchResults.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvSearchResults.visibility = View.VISIBLE
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
