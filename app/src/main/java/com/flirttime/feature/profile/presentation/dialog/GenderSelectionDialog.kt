package com.flirttime.feature.profile.presentation.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.flirttime.R
import com.flirttime.databinding.PopupGenderBinding

enum class GenderSelection {
    Male,
    Female,
    Other
}

interface OnGenderSelectedListener {
    fun onGenderSelected(gender: String)
}

class GenderSelectionDialog(
    private val context: Context,
    private val listener: OnGenderSelectedListener
) {
    private val dialog: Dialog = Dialog(context)
    private val binding: PopupGenderBinding = PopupGenderBinding.inflate(LayoutInflater.from(context))
    private var selectedGender: String = ""

    init {
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setContentView(binding.root)

        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)

        setupListeners()
    }

    private fun setupListeners() {
        binding.femaleBtn.setOnClickListener {
            selectedGender = GenderSelection.Female.name
            highlightFemaleOption()
        }

        binding.maleBtn.setOnClickListener {
            selectedGender = GenderSelection.Male.name
            highlightMaleOption()
        }

        binding.othersBtn.setOnClickListener {
            selectedGender = GenderSelection.Other.name
            highlightOtherOption()
        }

        binding.tvContinue.setOnClickListener {
            listener.onGenderSelected(selectedGender)
            dialog.dismiss()
        }

        binding.viewPopupHeader.ivCancel.setOnClickListener {
            dialog.dismiss()
        }
    }

    fun showDialog(currentGender: String = "") {
        if (currentGender.isNotBlank()) {
            selectedGender = currentGender
            when (selectedGender) {
                GenderSelection.Female.name -> highlightFemaleOption()
                GenderSelection.Male.name -> highlightMaleOption()
                GenderSelection.Other.name -> highlightOtherOption()
            }
        }
        dialog.show()
    }

    private fun highlightFemaleOption() {
        binding.femaleBtn.elevation = 24f
        binding.maleBtn.elevation = 0f
        binding.othersBtn.elevation = 0f

        binding.femaleBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.female)
        binding.maleBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
        binding.othersBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)

        enableContinue()
    }

    private fun highlightMaleOption() {
        binding.femaleBtn.elevation = 0f
        binding.maleBtn.elevation = 24f
        binding.othersBtn.elevation = 0f

        binding.femaleBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
        binding.maleBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.male)
        binding.othersBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)

        enableContinue()
    }

    private fun highlightOtherOption() {
        binding.femaleBtn.elevation = 0f
        binding.maleBtn.elevation = 0f
        binding.othersBtn.elevation = 24f

        binding.femaleBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
        binding.maleBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
        binding.othersBtn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.others)

        enableContinue()
    }

    private fun enableContinue() {
        binding.tvContinue.isEnabled = true
        binding.tvContinue.alpha = 1.0f
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
