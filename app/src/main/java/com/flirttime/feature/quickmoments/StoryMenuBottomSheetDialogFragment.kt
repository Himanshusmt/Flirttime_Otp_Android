package com.flirttime.feature.quickmoments

import android.content.DialogInterface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.flirttime.R
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.BottomSheetStoryMenuBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class StoryMenuBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetStoryMenuBinding? = null
    private val binding get() = _binding!!

    private var isOwnStory: Boolean = false
    private var storyId: String = ""
    private var userName: String = ""
    private var userId: String = ""

    var onDeleteConfirmed: ((storyId: String) -> Unit)? = null
    var onReportConfirmed: ((storyId: String) -> Unit)? = null
    var onDismissListener: (() -> Unit)? = null

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun getTheme(): Int = R.style.TransparentBottomSheetDialogTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOwnStory = arguments?.getBoolean(ARG_IS_OWN_STORY) ?: false
        storyId = arguments?.getString(ARG_STORY_ID).orEmpty()
        userName = arguments?.getString(ARG_USER_NAME).orEmpty()
        userId = arguments?.getString(ARG_USER_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetStoryMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Prevent bottom sheet from overlapping the Android system navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.root.updatePadding(bottom = navBars.bottom + dpToPx(24))
            insets
        }
        binding.root.post {
            _binding?.root?.requestApplyInsets()
        }

        if (isOwnStory) {
            // Own Story: Case A (Delete + Cancel side-by-side)
            binding.layoutOwnStoryMenu.visibility = View.VISIBLE
            binding.layoutOtherStoryMenu.visibility = View.GONE

            binding.btnMenuDeleteStory.setOnClickListener {
                dismiss()
                onDeleteConfirmed?.invoke(storyId)
            }

            binding.btnMenuCancelOwn.setOnClickListener {
                dismiss()
            }
        } else {
            // Other User Story: Case B (Primary: View Profile, Secondary: Report + Cancel)
            binding.layoutOwnStoryMenu.visibility = View.GONE
            binding.layoutOtherStoryMenu.visibility = View.VISIBLE

            binding.tvMenuViewProfileLabel.text = if (userName.isNotBlank()) {
                "View $userName's Profile"
            } else {
                "View Profile"
            }

            binding.btnMenuViewProfile.setOnClickListener {
                dismiss()
                if (userId.isNotBlank()) {
                    AppNavigator.navigateTo(
                        requireContext(),
                        Destination.UserProfile(userId = userId)
                    )
                } else {
                    Toast.makeText(requireContext(), "User profile unavailable", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnMenuReport.setOnClickListener {
                dismiss()
                onReportConfirmed?.invoke(storyId)
                Toast.makeText(requireContext(), "Report submitted. Thank you for keeping our community safe.", Toast.LENGTH_SHORT).show()
            }

            binding.btnMenuCancelOther.setOnClickListener {
                dismiss()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_OWN_STORY = "arg_is_own_story"
        private const val ARG_STORY_ID = "arg_story_id"
        private const val ARG_USER_NAME = "arg_user_name"
        private const val ARG_USER_ID = "arg_user_id"

        fun newInstance(
            isOwnStory: Boolean,
            storyId: String,
            userName: String = "",
            userId: String = ""
        ): StoryMenuBottomSheetDialogFragment {
            return StoryMenuBottomSheetDialogFragment().apply {
                arguments = bundleOf(
                    ARG_IS_OWN_STORY to isOwnStory,
                    ARG_STORY_ID to storyId,
                    ARG_USER_NAME to userName,
                    ARG_USER_ID to userId
                )
            }
        }
    }
}
