package com.flirttime.feature.profile.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.Resource
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.databinding.ActivityPrivacySafetyBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PrivacySafetyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySafetyBinding
    private val viewModel: SettingsViewModel by viewModel()
    private var isBinding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityPrivacySafetyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupListeners()
        observeViewModel()
        viewModel.loadVisibilitySettings()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            view.updatePadding(
                top = statusBarInsets.top,
                bottom = navBarInsets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.switchShowAge.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleAgeShow(isChecked)
        }
        binding.switchShowDistance.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleDistanceShow(isChecked)
        }
        binding.switchShowOnline.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleOnlineShow(isChecked)
        }
        binding.switchPublicSearch.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.togglePublicSearch(isChecked)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.visibilitySettingsState.collectLatest { resource ->
                        if (resource is Resource.Success) {
                            val settings = resource.data
                            isBinding = true
                            binding.switchShowAge.isChecked = settings.ageShow ?: true
                            binding.switchShowDistance.isChecked = settings.distanceShow ?: true
                            binding.switchShowOnline.isChecked = settings.onlineShow ?: true
                            binding.switchPublicSearch.isChecked = settings.enablePublicSearch ?: true
                            isBinding = false
                        }
                    }
                }
                launch {
                    viewModel.updateVisibilityState.collect { resource ->
                        if (resource is Resource.Error) {
                            Toast.makeText(
                                this@PrivacySafetyActivity,
                                "Failed to update: ${resource.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
