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
import com.flirttime.databinding.ActivityNotificationSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private val viewModel: SettingsViewModel by viewModel()
    private var isBinding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupListeners()
        observeViewModel()
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
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.switchNewMessages.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleNewMessages(isChecked)
        }

        binding.switchNewMatches.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleNewMatches(isChecked)
        }

        binding.switchLikeComments.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleLikeComments(isChecked)
        }

        binding.switchProfileVisits.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleProfileVisits(isChecked)
        }

        binding.switchDatingTips.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleDatingTips(isChecked)
        }

        binding.switchPushNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.togglePushNotifications(isChecked)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Settings state
                launch {
                    viewModel.notificationSettingsState.collectLatest { resource ->
                        if (resource is Resource.Success) {
                            val settings = resource.data
                            isBinding = true
                            binding.switchNewMessages.isChecked = settings.newMessages ?: true
                            binding.switchNewMatches.isChecked = settings.newMatches ?: true
                            binding.switchLikeComments.isChecked = settings.likeComments ?: true
                            binding.switchProfileVisits.isChecked = settings.profileVisits ?: true
                            binding.switchDatingTips.isChecked = settings.datingTips ?: false
                            binding.switchPushNotifications.isChecked = settings.pushNotifications ?: true
                            isBinding = false
                        }
                    }
                }

                // Error feedback
                launch {
                    viewModel.updateSettingsState.collect { resource ->
                        if (resource is Resource.Error) {
                            Toast.makeText(
                                this@NotificationSettingsActivity,
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
