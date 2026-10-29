package com.flirttime.feature.profile

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.databinding.DialogVoiceIntroBottomSheetBinding
import com.flirttime.feature.main.MainRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

class VoiceIntroBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogVoiceIntroBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val mainRepository: MainRepository by inject()
    private val sessionManager: SessionManager by inject()

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordedAudioFile: File? = null

    private var isRecording = false
    private var isPlayingPreview = false
    private var recordedDurationSeconds = 0
    private var timerJob: Job? = null
    private var previewJob: Job? = null

    var onVoiceIntroSaved: ((audioUrl: String, duration: Int) -> Unit)? = null

    private val ideaPrompts = listOf(
        "The quickest way to win me over is...",
        "I'm looking for someone who...",
        "My ideal Sunday looks like...",
        "Tell me about your favorite travel memory...",
        "Two truths and a lie about me...",
        "A secret talent I have is...",
        "My friends would describe me as...",
        "The best advice I ever received was...",
        "What makes me laugh without fail...",
        "My favorite playlist right now is...",
        "One thing on my bucket list is..."
    )
    private var promptIndex = 0

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(
                requireContext(),
                "Microphone permission is required to record your voice intro.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVoiceIntroBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                val displayMetrics = resources.displayMetrics
                // Open sheet generously high (~84% height) leaving spacious empty white space below
                val targetHeight = (displayMetrics.heightPixels * 0.84).toInt()
                sheet.layoutParams.height = targetHeight
                sheet.requestLayout()
                behavior.peekHeight = targetHeight
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnShuffleIdea.setOnClickListener {
            promptIndex = (promptIndex + 1) % ideaPrompts.size
            val nextPrompt = ideaPrompts[promptIndex]
            val fadeOut = AlphaAnimation(1f, 0f).apply {
                duration = 150
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        binding.tvIdeaPrompt.text = nextPrompt
                        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 150 }
                        binding.tvIdeaPrompt.startAnimation(fadeIn)
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
            }
            binding.tvIdeaPrompt.startAnimation(fadeOut)
        }

        binding.btnRecordMic.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionAndStartRecording()
            }
        }

        binding.btnPreviewPlay.setOnClickListener {
            togglePreviewPlayback()
        }

        binding.btnReRecord.setOnClickListener {
            resetToRecordState()
        }

        binding.btnSaveVoice.setOnClickListener {
            saveAndUploadVoiceIntro()
        }
    }

    private fun checkPermissionAndStartRecording() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startRecording()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        try {
            stopPreviewPlayback()
            val cacheDir = requireContext().cacheDir
            recordedAudioFile = File(cacheDir, "voice_intro_${System.currentTimeMillis()}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordedAudioFile!!.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true

            // UI updates
            binding.flRecordContainer.visibility = View.VISIBLE
            binding.layoutReviewRow.visibility = View.GONE
            binding.ivMicIcon.setImageResource(R.drawable.ic_stop_white)
            binding.tvRecordStatus.text = "Recording... Tap to stop (at least 3s)"
            binding.audioWaveformView.setProgress(0f)
            startPulseAnimation()

            var elapsedMs = 0L
            recordedDurationSeconds = 0
            timerJob?.cancel()
            timerJob = lifecycleScope.launch {
                while (isActive && isRecording) {
                    delay(100)
                    elapsedMs += 100
                    val currentSec = (elapsedMs / 1000).toInt()
                    val progress = (elapsedMs / 30000f).coerceIn(0f, 1f)
                    binding.audioWaveformView.setProgress(progress)
                    binding.tvTimer.text = String.format("0:%02d / 0:30", currentSec)
                    recordedDurationSeconds = currentSec.coerceAtLeast(1)

                    if (elapsedMs >= 30000L) {
                        stopRecording()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            Toast.makeText(requireContext(), "Failed to start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        stopPulseAnimation()

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }
        timerJob?.cancel()

        // Validate duration >= 3 seconds
        if (recordedDurationSeconds < 3) {
            Toast.makeText(requireContext(), "Voice intro must be at least 3 seconds.", Toast.LENGTH_SHORT).show()
            recordedAudioFile?.delete()
            recordedAudioFile = null
            resetToRecordState()
            return
        }

        // Valid recording -> Switch to 3-button review row (Matching Image 3)
        binding.flRecordContainer.visibility = View.GONE
        binding.layoutReviewRow.visibility = View.VISIBLE
        binding.ivMicIcon.setImageResource(R.drawable.ic_mic_white)
        binding.ivPreviewPlayIcon.setImageResource(R.drawable.ic_play_white)
        binding.tvTimer.text = String.format("0:%02d", recordedDurationSeconds)
        binding.tvRecordStatus.text = "Listen back, then save or record again."
        binding.audioWaveformView.setProgress(0f)
    }

    private fun togglePreviewPlayback() {
        val file = recordedAudioFile
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), "No recording found", Toast.LENGTH_SHORT).show()
            return
        }

        if (isPlayingPreview) {
            stopPreviewPlayback()
        } else {
            startPreviewPlayback(file)
        }
    }

    private fun startPreviewPlayback(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    start()
                    isPlayingPreview = true
                    binding.ivPreviewPlayIcon.setImageResource(R.drawable.ic_pause_white)
                }
                setOnCompletionListener {
                    stopPreviewPlayback()
                }
                prepareAsync()
            }

            previewJob?.cancel()
            previewJob = lifecycleScope.launch {
                while (isActive && isPlayingPreview) {
                    val currentPos = mediaPlayer?.currentPosition ?: 0
                    val totalDuration = mediaPlayer?.duration ?: (recordedDurationSeconds * 1000)
                    val currentSec = currentPos / 1000
                    binding.tvTimer.text = String.format("0:%02d", currentSec)
                    val progress = (currentPos.toFloat() / totalDuration.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    binding.audioWaveformView.setProgress(progress)
                    delay(100)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Playback error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreviewPlayback() {
        isPlayingPreview = false
        previewJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        _binding?.let { b ->
            b.ivPreviewPlayIcon.setImageResource(R.drawable.ic_play_white)
            b.tvTimer.text = String.format("0:%02d", recordedDurationSeconds)
            b.audioWaveformView.setProgress(0f)
        }
    }

    private fun resetToRecordState() {
        stopPreviewPlayback()
        recordedAudioFile?.delete()
        recordedAudioFile = null
        recordedDurationSeconds = 0

        binding.flRecordContainer.visibility = View.VISIBLE
        binding.layoutReviewRow.visibility = View.GONE
        binding.tvTimer.text = "0:00 / 0:30"
        binding.tvRecordStatus.text = "Tap the mic to start. At least 3 seconds."
        binding.ivMicIcon.setImageResource(R.drawable.ic_mic_white)
        binding.audioWaveformView.setProgress(0f)
    }

    private fun saveAndUploadVoiceIntro() {
        val file = recordedAudioFile
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), "Please record a voice intro first.", Toast.LENGTH_SHORT).show()
            return
        }

        stopPreviewPlayback()

        binding.btnSaveVoice.visibility = View.INVISIBLE
        binding.pbSave.visibility = View.VISIBLE

        lifecycleScope.launch {
            val uri = Uri.fromFile(file)
            mainRepository.uploadAudioIntro(requireContext(), uri, recordedDurationSeconds)
                .collect { resource ->
                    when (resource) {
                        is Resource.Loading -> Unit
                        is Resource.Success -> {
                            val audioDto = resource.data
                            val effectiveUrl = audioDto.effectiveUrl ?: audioDto.audioUrl ?: audioDto.url ?: uri.toString()
                            val duration = audioDto.duration ?: recordedDurationSeconds

                            sessionManager.setAudioIntroUrl(effectiveUrl)
                            sessionManager.setAudioIntroDuration(duration)

                            Toast.makeText(requireContext(), "Voice intro uploaded successfully!", Toast.LENGTH_SHORT).show()
                            onVoiceIntroSaved?.invoke(effectiveUrl, duration)
                            dismiss()
                        }
                        is Resource.Error -> {
                            binding.btnSaveVoice.visibility = View.VISIBLE
                            binding.pbSave.visibility = View.GONE
                            Toast.makeText(requireContext(), "Upload failed: ${resource.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
    }

    private fun startPulseAnimation() {
        binding.viewPulseRing.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .alpha(0.6f)
            .setDuration(600)
            .withEndAction {
                if (isRecording) {
                    binding.viewPulseRing.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(600)
                        .withEndAction {
                            if (isRecording) startPulseAnimation()
                        }
                        .start()
                }
            }
            .start()
    }

    private fun stopPulseAnimation() {
        binding.viewPulseRing.animate().cancel()
        binding.viewPulseRing.scaleX = 1f
        binding.viewPulseRing.scaleY = 1f
        binding.viewPulseRing.alpha = 1f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
        previewJob?.cancel()
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
        }
        stopPreviewPlayback()
        _binding = null
    }

    companion object {
        const val TAG = "VoiceIntroBottomSheet"

        fun newInstance(): VoiceIntroBottomSheetDialogFragment {
            return VoiceIntroBottomSheetDialogFragment()
        }
    }
}
