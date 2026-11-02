package com.flirttime.feature.chat.list

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.feature.chat.model.ConversationUi

/**
 * Inbox swipe gestures — the Android twin of the iOS `ChatRowView` reveal actions.
 *
 * Swipe right → Mark as read / unread, swipe left → Archive / Unarchive. Delete stays in the
 * long-press sheet because a destructive swipe has no undo on Android.
 */
class ChatRowSwipeCallback(
    private val context: Context,
    private val onArchiveToggled: (ConversationUi) -> Unit,
    private val onReadToggled: (ConversationUi) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconSize = (22 * context.resources.displayMetrics.density).toInt()
    private val iconPadding = (20 * context.resources.displayMetrics.density).toInt()

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val adapter = (viewHolder.itemView.parent as? RecyclerView)
            ?.adapter as? ConversationAdapter ?: return
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val conversation = adapter.currentList.getOrNull(position) ?: return

        // The VM patches the row in place, so undo the drag translation before it re-renders.
        adapter.notifyItemChanged(position)
        when (direction) {
            ItemTouchHelper.LEFT -> onArchiveToggled(conversation)
            ItemTouchHelper.RIGHT -> onReadToggled(conversation)
        }
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val conversation = (recyclerView.adapter as? ConversationAdapter)
                ?.currentList
                ?.getOrNull(viewHolder.adapterPosition)

            if (dX < 0) {
                // Revealed from the right: archive.
                backgroundPaint.color = ARCHIVE_TINT
                canvas.drawRect(
                    RectF(itemView.right + dX, itemView.top.toFloat(),
                        itemView.right.toFloat(), itemView.bottom.toFloat()),
                    backgroundPaint
                )
                drawIcon(canvas, R.drawable.ic_action_archive, itemView.right - iconPadding - iconSize, itemView)
            } else if (dX > 0) {
                // Revealed from the left: read / unread toggle.
                backgroundPaint.color = READ_TINT
                val markingRead = (conversation?.unreadCount ?: 0) > 0
                canvas.drawRect(
                    RectF(itemView.left.toFloat(), itemView.top.toFloat(),
                        itemView.left + dX, itemView.bottom.toFloat()),
                    backgroundPaint
                )
                drawIcon(
                    canvas,
                    if (markingRead) R.drawable.ic_action_check_circle else R.drawable.ic_action_circle_outline,
                    itemView.left + iconPadding,
                    itemView
                )
            }
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawIcon(canvas: Canvas, drawableRes: Int, left: Int, itemView: android.view.View) {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, Color.WHITE)
        val top = itemView.top + (itemView.height - iconSize) / 2
        wrapped.setBounds(left, top, left + iconSize, top + iconSize)
        wrapped.draw(canvas)
    }

    private companion object {
        val ARCHIVE_TINT = Color.parseColor("#9CA3AF")
        val READ_TINT = Color.parseColor("#E53935")
    }
}
