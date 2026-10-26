package com.flirttime.feature.notification.presentation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.databinding.ActivityNotificationBinding
import com.flirttime.feature.notification.presentation.adapter.NotificationsAdapter
import kotlinx.coroutines.launch

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var notificationsAdapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupViews()
        observeNotifications()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            binding.layoutHeader.updatePadding(top = statusBarInsets.top + 8)
            binding.rvNotifications.updatePadding(bottom = navBarInsets.bottom + 24)

            windowInsets
        }
    }

    private fun setupViews() {
        notificationsAdapter = NotificationsAdapter { item ->
            viewModel.onNotificationClicked(item)
        }

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = notificationsAdapter
            itemAnimator = null // Prevent blink during in-place item updates
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMarkAllRead.setOnClickListener {
            viewModel.onMarkAllAsRead()
        }
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notificationsState.collect { items ->
                    notificationsAdapter.submitList(items)
                }
            }
        }
    }
}
