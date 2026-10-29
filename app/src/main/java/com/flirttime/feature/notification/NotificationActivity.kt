package com.flirttime.feature.notification

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.ActivityNotificationBinding
import com.flirttime.feature.notification.NotificationListItem
import com.flirttime.feature.notification.NotificationsAdapter
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private val viewModel: NotificationViewModel by viewModel()
    private lateinit var notificationsAdapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupViews()
        setupSwipeToDelete()
        setupPagination()
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
            viewModel.markAllAsRead()
            Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = notificationsAdapter.currentList.getOrNull(position)
                    if (item is NotificationListItem.Item) {
                        return super.getSwipeDirs(recyclerView, viewHolder)
                    }
                }
                return 0 // Headers are not swipeable
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = notificationsAdapter.currentList.getOrNull(position)
                    if (item is NotificationListItem.Item) {
                        viewModel.deleteNotification(item.notification)
                        Toast.makeText(this@NotificationActivity, "Notification deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvNotifications)
    }

    private fun setupPagination() {
        binding.rvNotifications.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (totalItemCount <= lastVisibleItem + 2 && viewModel.hasMore) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notificationsState.collect { items ->
                        notificationsAdapter.submitList(items)
                    }
                }

                launch {
                    viewModel.errorMessage.collect { message ->
                        Toast.makeText(this@NotificationActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
