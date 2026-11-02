package com.flirttime.feature.chat.detail

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.DialogForwardPickerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Recipient picker for “Forward” — the Android twin of the iOS
 * `ForwardRecipientPickerView`.
 *
 * Search, multi-select, infinite scroll and a “Forward (n)” confirmation bar.
 */
class ForwardPickerFragment : BottomSheetDialogFragment() {

    /** Receives the selected user ids once the user confirms. */
    var onForwardSelected: ((List<String>) -> Unit)? = null

    private var _binding: DialogForwardPickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ForwardPickerViewModel by viewModel()

    private val recipientAdapter = ForwardRecipientAdapter { recipient ->
        viewModel.toggle(recipient.userId)
    }

    fun show(manager: FragmentManager) {
        show(manager, TAG)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_top)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogForwardPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRecipients.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecipients.adapter = recipientAdapter
        binding.rvRecipients.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (layoutManager.findLastVisibleItemPosition() >= recipientAdapter.itemCount - 3) {
                    viewModel.loadMore()
                }
            }
        })

        binding.etSearch.doAfterTextChanged { viewModel.onSearchChanged(it?.toString().orEmpty()) }

        binding.btnForward.setOnClickListener {
            val selected = viewModel.selectedIds.value.toList()
            if (selected.isEmpty()) return@setOnClickListener
            val callback = onForwardSelected
            dismissAllowingStateLoss()
            callback?.invoke(selected)
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recipients.collect { recipients ->
                        recipientAdapter.submit(recipients, viewModel.selectedIds.value)
                        renderEmptyState(recipients.isEmpty())
                    }
                }
                launch {
                    viewModel.selectedIds.collect { selected ->
                        recipientAdapter.submit(viewModel.recipients.value, selected)
                        renderForwardButton(selected.size)
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.pbLoading.isVisible = loading
                        if (loading) binding.tvEmpty.isVisible = false
                    }
                }
            }
        }
    }

    private fun renderEmptyState(empty: Boolean) {
        val showEmpty = empty && !viewModel.isLoading.value && viewModel.loadFailed.value
        binding.tvEmpty.isVisible = showEmpty
        binding.tvEmpty.text = if (viewModel.loadFailed.value) {
            "Could not load contacts"
        } else {
            "No contacts found"
        }
    }

    private fun renderForwardButton(count: Int) {
        binding.btnForward.isEnabled = count > 0
        binding.btnForward.text = if (count > 0) "Forward ($count)" else "Forward"
    }

    override fun onDestroyView() {
        binding.rvRecipients.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ForwardPicker"

        /** Convenience factory so callers can chain `show()` and the callback. */
        fun create(
            onForwardSelected: (List<String>) -> Unit
        ): ForwardPickerFragment = ForwardPickerFragment().apply {
            this.onForwardSelected = onForwardSelected
        }

        @Suppress("unused")
        internal fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}
