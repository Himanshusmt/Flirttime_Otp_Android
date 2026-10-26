package com.flirttime.feature.main.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.flirttime.R
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var isVoicePlaying = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopActionsInsets()
        setupUserProfileHeader(
            name = "tushar",
            age = 19,
            location = "Siwan, Bihar",
            bio = "“Def a. SCD kasndklat and you will never get a chance for this one day you are not going anywhere with your life in it you will be a legend and you are”",
            completionPercent = 90
        )
        // Set initial voice state: false = empty record prompt, true = voice player
        updateVoiceSectionState(hasVoice = false)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Ensure status bar icons are white over the cover photo
        activity?.window?.let { window ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }
    }

    private fun setupUserProfileHeader(
        name: String,
        age: Int,
        location: String,
        bio: String,
        completionPercent: Int
    ) {
        // 1. Profile Completion Ring & Badge
        binding.profileProgressRing.progress = completionPercent
        binding.tvCompletionPercent.text = "$completionPercent% complete"

        // 2. Name & Age
        binding.tvNameAge.text = "$name, $age"

        // 3. Location
        binding.tvLocation.text = location

        // 4. Bio
        binding.tvBio.text = bio
    }

    private fun setupClickListeners() {
        // Edit Cover Photo
        binding.btnEditCover.setOnClickListener {
            Toast.makeText(requireContext(), "Change Cover Photo", Toast.LENGTH_SHORT).show()
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Settings", Toast.LENGTH_SHORT).show()
        }

        // Profile Completion Pill
        binding.badgeComplete.setOnClickListener {
            Toast.makeText(requireContext(), "Complete profile to unlock all features", Toast.LENGTH_SHORT).show()
        }

        // Edit Profile CTA
        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile", Toast.LENGTH_SHORT).show()
        }

        // Add Moment CTA -> Navigate directly to AddMomentActivity
        binding.btnAddMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // Voice Section: Empty State (Record Prompt)
        binding.cardVoiceRecordPrompt.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Voice Recorder...", Toast.LENGTH_SHORT).show()
        }
        binding.btnRecordMic.setOnClickListener {
            Toast.makeText(requireContext(), "Record your voice intro (up to 30s)", Toast.LENGTH_SHORT).show()
        }

        // Voice Section: Active State (Player & Edit)
        binding.btnPlayVoice.setOnClickListener {
            toggleVoicePlayback()
        }
        binding.cardVoicePlayer.setOnClickListener {
            toggleVoicePlayback()
        }

        // Edit Voice Note
        binding.btnEditVoice.setOnClickListener {
            Toast.makeText(requireContext(), "Record or replace voice note", Toast.LENGTH_SHORT).show()
        }

        // Quick Moments + Add
        binding.btnAddQuickMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // Quick Moments: Add new item
        binding.itemAddNewMoment.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.AddMoment)
        }

        // Quick Moments: Active moment click
        binding.itemActiveMoment.setOnClickListener {
            Toast.makeText(requireContext(), "Viewing moment: Hellow all", Toast.LENGTH_SHORT).show()
        }

        // Premium Benefit Banner
        binding.cardPremiumBanner.setOnClickListener {
            Toast.makeText(requireContext(), "Upgrade to Premium to unlock all benefits", Toast.LENGTH_SHORT).show()
        }
        binding.btnUpgradePremium.setOnClickListener {
            Toast.makeText(requireContext(), "Upgrade to Premium to unlock all benefits", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Updates the "My Voice" section UI depending on whether the user has added a voice note.
     * @param hasVoice true to show player card with waveform, false to show record prompt card.
     */
    fun updateVoiceSectionState(hasVoice: Boolean) {
        if (hasVoice) {
            binding.cardVoiceRecordPrompt.visibility = View.GONE
            binding.cardVoicePlayer.visibility = View.VISIBLE
        } else {
            binding.cardVoiceRecordPrompt.visibility = View.VISIBLE
            binding.cardVoicePlayer.visibility = View.GONE
        }
    }

    private fun toggleVoicePlayback() {
        isVoicePlaying = !isVoicePlaying
        if (isVoicePlaying) {
            binding.ivPlayIcon.setImageResource(R.drawable.ic_pause_white)
            Toast.makeText(requireContext(), "Playing voice note...", Toast.LENGTH_SHORT).show()
        } else {
            binding.ivPlayIcon.setImageResource(R.drawable.ic_play_white)
        }
    }

    private fun setupTopActionsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            applyTopActionsMargin(statusBarTop)
            insets
        }
        binding.root.requestApplyInsets()

        val rootInsets = ViewCompat.getRootWindowInsets(binding.root)
        val statusBarTop = rootInsets?.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )?.top ?: 0
        if (statusBarTop > 0) {
            applyTopActionsMargin(statusBarTop)
        }
    }

    private fun applyTopActionsMargin(statusBarTop: Int) {
        val extra = (12 * resources.displayMetrics.density).toInt()
        val targetMargin = if (statusBarTop > 0) statusBarTop + extra else (48 * resources.displayMetrics.density).toInt()
        (binding.layoutTopActions.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.topMargin != targetMargin) {
                lp.topMargin = targetMargin
                binding.layoutTopActions.layoutParams = lp
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
