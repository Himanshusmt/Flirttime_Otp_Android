package com.flirttime.feature.profile.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.databinding.ActivityFaqBinding

class FaqActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFaqBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val category = FAQCategory.fromKey(intent.getStringExtra(AppNavigator.EXTRA_FAQ_CATEGORY))
        binding.tvHeaderTitle.text = category?.title ?: "FAQs"

        setupInsets()
        setupList(category)
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(top = statusBarInsets.top, bottom = navBarInsets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupList(category: FAQCategory?) {
        val items = FAQCatalog.items(category)
        lateinit var adapter: FaqAdapter
        adapter = FaqAdapter(items) { position ->
            if (position == RecyclerView.NO_POSITION) return@FaqAdapter
            adapter.expandedIndex = if (adapter.expandedIndex == position) null else position
        }
        binding.rvFaqs.layoutManager = LinearLayoutManager(this)
        binding.rvFaqs.adapter = adapter
        binding.rvFaqs.itemAnimator = null
    }
}
