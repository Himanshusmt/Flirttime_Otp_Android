package com.flirttime.feature.main.presentation.moments.gift

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.flirttime.R
import com.flirttime.databinding.BottomSheetSendGiftBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet dialog allowing users to send interactive gifts to moment creators.
 */
class SendGiftBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SendGiftBottomSheet"
        private const val ARG_RECIPIENT_NAME = "arg_recipient_name"
        private const val ARG_RECIPIENT_ID = "arg_recipient_id"
        private const val ARG_CURRENT_COINS = "arg_current_coins"

        fun newInstance(
            recipientName: String,
            recipientId: String? = null,
            currentCoins: Int = 607
        ): SendGiftBottomSheetDialogFragment {
            return SendGiftBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPIENT_NAME, recipientName)
                    putString(ARG_RECIPIENT_ID, recipientId)
                    putInt(ARG_CURRENT_COINS, currentCoins)
                }
            }
        }
    }

    private var _binding: BottomSheetSendGiftBinding? = null
    private val binding get() = _binding!!

    private var recipientName: String = "User"
    private var recipientId: String? = null
    private var userCoins: Int = 607
    private var selectedGift: GiftItem? = null
    private lateinit var giftAdapter: GiftAdapter

    var onGiftSent: ((GiftItem, remainingCoins: Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipientName = arguments?.getString(ARG_RECIPIENT_NAME) ?: "User"
        recipientId = arguments?.getString(ARG_RECIPIENT_ID)
        userCoins = arguments?.getInt(ARG_CURRENT_COINS, 607) ?: 607
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
        _binding = BottomSheetSendGiftBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeaders()
        setupGiftGrid()
        setupSendButton()
    }

    private fun setupHeaders() {
        binding.tvGiftTitle.text = "Send a gift to $recipientName"
        updateBalanceUi()
    }

    private fun updateBalanceUi() {
        binding.tvBalance.text = "Balance: $userCoins coins"
    }

    private fun setupGiftGrid() {
        val gifts = MockGiftsProvider.getAvailableGifts()
        giftAdapter = GiftAdapter(gifts) { gift ->
            selectedGift = gift
            updateSendButtonState(gift)
        }

        binding.rvGifts.apply {
            layoutManager = GridLayoutManager(requireContext(), 4)
            adapter = giftAdapter
        }
    }

    private fun updateSendButtonState(gift: GiftItem?) {
        if (gift != null) {
            binding.btnSendGift.isEnabled = true
            binding.btnSendGift.setBackgroundResource(R.drawable.bg_btn_pill_red)
            binding.btnSendGift.setTextColor(Color.WHITE)
            binding.btnSendGift.text = "Send Gift (${gift.coinPrice} coins)"
        } else {
            binding.btnSendGift.isEnabled = false
            binding.btnSendGift.setBackgroundResource(R.drawable.bg_btn_pill_disabled_gray)
            binding.btnSendGift.setTextColor(Color.parseColor("#A0A0A0"))
            binding.btnSendGift.text = "Select a gift"
        }
    }

    private fun setupSendButton() {
        binding.btnSendGift.setOnClickListener {
            val gift = selectedGift ?: return@setOnClickListener

            if (userCoins >= gift.coinPrice) {
                userCoins -= gift.coinPrice
                updateBalanceUi()

                Toast.makeText(
                    requireContext(),
                    "Gift ${gift.name} ${gift.emoji} sent to $recipientName!",
                    Toast.LENGTH_SHORT
                ).show()

                onGiftSent?.invoke(gift, userCoins)
                dismiss()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Insufficient coins. Please recharge your balance.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
