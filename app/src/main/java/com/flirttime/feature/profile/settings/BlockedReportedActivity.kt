package com.flirttime.feature.profile.settings

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.databinding.ActivityBlockedReportedBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class BlockedReportedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedReportedBinding
    private val viewModel: SettingsViewModel by viewModel()

    private lateinit var blockedAdapter: BlockedUsersAdapter
    private lateinit var reportedAdapter: ReportedUsersAdapter

    private var selectedTabIndex = 0 // 0 = Blocked, 1 = Reported
    private var blockedCount = 0
    private var reportedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityBlockedReportedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupAdapters()
        setupListeners()
        observeViewModel()

        viewModel.loadBlockedUsers()
        viewModel.loadReports()
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

    private fun setupAdapters() {
        blockedAdapter = BlockedUsersAdapter { blockedUser ->
            com.flirttime.core.ui.dialog.CommonConfirmationDialog.show(
                context = this,
                title = "Unblock ${blockedUser.name ?: blockedUser.username ?: "this user"}?",
                message = "They will be able to see your profile and message you again.",
                positiveText = "Yes",
                negativeText = "No"
            ) {
                viewModel.unblockUser(blockedUser.userId)
            }
        }
        binding.rvBlockedUsers.layoutManager = LinearLayoutManager(this)
        binding.rvBlockedUsers.adapter = blockedAdapter

        reportedAdapter = ReportedUsersAdapter()
        binding.rvReportedUsers.layoutManager = LinearLayoutManager(this)
        binding.rvReportedUsers.adapter = reportedAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvTabBlocked.setOnClickListener {
            switchTab(0)
        }

        binding.tvTabReported.setOnClickListener {
            switchTab(1)
        }
    }

    private fun switchTab(tabIndex: Int) {
        selectedTabIndex = tabIndex
        if (tabIndex == 0) {
            binding.tvTabBlocked.setBackgroundResource(R.drawable.bg_segmented_active_tab)
            binding.tvTabBlocked.setTextColor(Color.WHITE)

            binding.tvTabReported.setBackgroundColor(Color.TRANSPARENT)
            binding.tvTabReported.setTextColor(Color.parseColor("#8E8E93"))

            binding.layoutBlockedContent.isVisible = true
            binding.layoutReportedContent.isVisible = false
        } else {
            binding.tvTabReported.setBackgroundResource(R.drawable.bg_segmented_active_tab)
            binding.tvTabReported.setTextColor(Color.WHITE)

            binding.tvTabBlocked.setBackgroundColor(Color.TRANSPARENT)
            binding.tvTabBlocked.setTextColor(Color.parseColor("#8E8E93"))

            binding.layoutReportedContent.isVisible = true
            binding.layoutBlockedContent.isVisible = false
        }
    }

    private fun updateTabLabels() {
        binding.tvTabBlocked.text = "Blocked ($blockedCount)"
        binding.tvTabReported.text = "Reported ($reportedCount)"
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Blocked users
                launch {
                    viewModel.blockedUsersState.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.pbLoading.isVisible = selectedTabIndex == 0 && blockedCount == 0
                            }
                            is Resource.Success -> {
                                binding.pbLoading.isVisible = false
                                val list = resource.data.blockedUsers
                                blockedCount = list.size
                                updateTabLabels()
                                blockedAdapter.submitList(list)
                                binding.rvBlockedUsers.isVisible = list.isNotEmpty()
                                binding.layoutBlockedEmpty.isVisible = list.isEmpty()
                            }
                            is Resource.Error -> {
                                binding.pbLoading.isVisible = false
                            }
                            null -> Unit
                        }
                    }
                }

                // Reported users
                launch {
                    viewModel.reportsState.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.pbLoading.isVisible = selectedTabIndex == 1 && reportedCount == 0
                            }
                            is Resource.Success -> {
                                binding.pbLoading.isVisible = false
                                val list = resource.data.reports
                                reportedCount = list.size
                                updateTabLabels()
                                reportedAdapter.submitList(list)
                                binding.rvReportedUsers.isVisible = list.isNotEmpty()
                                binding.layoutReportedEmpty.isVisible = list.isEmpty()
                            }
                            is Resource.Error -> {
                                binding.pbLoading.isVisible = false
                            }
                            null -> Unit
                        }
                    }
                }

                // Unblock feedback
                launch {
                    viewModel.unblockState.collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                Toast.makeText(this@BlockedReportedActivity, "User unblocked successfully", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                Toast.makeText(this@BlockedReportedActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
