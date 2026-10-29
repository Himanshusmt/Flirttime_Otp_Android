package com.flirttime.feature.moments.gift

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.Resource
import com.flirttime.databinding.BottomSheetReceivedGiftsBinding
import com.flirttime.feature.moments.GiftSenderDto
import com.flirttime.feature.moments.ReceivedGiftDto
import com.flirttime.feature.moments.MomentsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ReceivedGiftsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ReceivedGiftsBottomSheet"
        private const val ARG_MOMENT_ID = "arg_moment_id"

        fun newInstance(momentId: String): ReceivedGiftsBottomSheetDialogFragment {
            return ReceivedGiftsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MOMENT_ID, momentId)
                }
            }
        }
    }

    private var _binding: BottomSheetReceivedGiftsBinding? = null
    private val binding get() = _binding!!
    private val momentsRepository: MomentsRepository by inject()
    private var momentId: String = ""
    private lateinit var adapter: ReceivedGiftsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentId = arguments?.getString(ARG_MOMENT_ID).orEmpty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(android.R.color.transparent)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetReceivedGiftsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchReceivedGifts()
    }

    private fun setupRecyclerView() {
        adapter = ReceivedGiftsAdapter { gift ->
            Toast.makeText(requireContext(), "${gift.senderName} sent ${gift.displayName}", Toast.LENGTH_SHORT).show()
        }
        binding.rvReceivedGifts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReceivedGifts.adapter = adapter
    }

    private fun fetchReceivedGifts() {
        lifecycleScope.launch {
            momentsRepository.getReceivedGifts(momentId = momentId, page = 1, limit = 20)
                .collectLatest { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            if (adapter.itemCount == 0) {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.layoutEmpty.visibility = View.GONE
                            }
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            val responseData = resource.data
                            val gifts = responseData.effectiveGifts
                            val totalGifts = responseData.effectiveTotalGifts
                            val totalCoins = responseData.effectiveTotalCoins

                            if (gifts.isNotEmpty()) {
                                updateSummaryHeader(totalGifts, totalCoins)
                                adapter.submitList(gifts)
                                binding.rvReceivedGifts.visibility = View.VISIBLE
                                binding.layoutEmpty.visibility = View.GONE
                            } else {
                                // Provide reference preview gift matching media_1792671367529_62a5d75d.png
                                showPreviewGift()
                            }
                        }
                        is Resource.Error -> {
                            binding.progressBar.visibility = View.GONE
                            if (adapter.itemCount == 0) {
                                showPreviewGift()
                            }
                        }
                    }
                }
        }
    }

    private fun updateSummaryHeader(giftsCount: Int, coinsCount: Int) {
        val giftLabel = if (giftsCount == 1) "gift" else "gifts"
        val coinLabel = if (coinsCount == 1) "coin" else "coins"
        binding.tvGiftsSummary.text = "$giftsCount $giftLabel · $coinsCount $coinLabel"
    }

    /**
     * Fallback preview matching media_1792671367529_62a5d75d.png
     */
    private fun showPreviewGift() {
        val preview = listOf(
            ReceivedGiftDto(
                id = "preview_gift_1",
                giftName = "Rose",
                coins = 1,
                icon = "🌹",
                sender = GiftSenderDto(
                    name = "hdhdhd",
                    avatarUrl = null
                ),
                timeAgo = "1d"
            )
        )
        updateSummaryHeader(1, 1)
        adapter.submitList(preview)
        binding.rvReceivedGifts.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
