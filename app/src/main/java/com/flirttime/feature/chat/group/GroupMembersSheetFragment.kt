package com.flirttime.feature.chat.group

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.core.navigation.ChatMember
import com.flirttime.databinding.DialogGroupMembersBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Group member list opened from the thread header (iOS taps the nav-bar title to reach the
 * group detail). Members come from the inbox row plus any sender seen in the thread.
 */
class GroupMembersSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogGroupMembersBinding? = null
    private val binding get() = _binding!!

    private var title: String = ""
    private var members: List<ChatMember> = emptyList()
    private var currentUserId: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_top)
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
        _binding = DialogGroupMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        title = args.getString(ARG_TITLE).orEmpty()
        currentUserId = args.getString(ARG_CURRENT_USER_ID).orEmpty()
        members = runCatching {
            val raw = args.getString(ARG_MEMBERS).orEmpty()
            val type = object : TypeToken<List<ChatMember>>() {}.type
            Gson().fromJson<List<ChatMember>>(raw, type)
        }.getOrNull().orEmpty()

        binding.tvGroupMembersTitle.text = title.takeIf { it.isNotBlank() } ?: "Group chat"
        binding.tvGroupMembersCount.text = when (members.size) {
            0 -> "No members yet"
            1 -> "1 member"
            else -> "${members.size} members"
        }
        binding.rvGroupMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroupMembers.adapter = GroupMemberAdapter(currentUserId)
        (binding.rvGroupMembers.adapter as GroupMemberAdapter).submitList(members)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "GroupMembersSheetFragment"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_MEMBERS = "arg_members"
        private const val ARG_CURRENT_USER_ID = "arg_current_user_id"

        fun create(
            title: String,
            members: List<ChatMember>,
            currentUserId: String
        ): GroupMembersSheetFragment = GroupMembersSheetFragment().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_MEMBERS to Gson().toJson(members),
                ARG_CURRENT_USER_ID to currentUserId
            )
        }
    }
}
