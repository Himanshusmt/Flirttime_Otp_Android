package com.flirttime.feature.onboarding

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityProfileBinding
import com.flirttime.feature.onboarding.dialog.DobDialogNew
import com.flirttime.feature.onboarding.dialog.GenderSelectionDialog
import com.flirttime.feature.onboarding.dialog.OnDateSelectedListener
import com.flirttime.feature.onboarding.dialog.OnGenderSelectedListener
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileActivity : AppCompatActivity(), OnGenderSelectedListener, OnDateSelectedListener {

    private lateinit var binding: ActivityProfileBinding
    private val sessionManager: SessionManager by inject()
    private val viewModel: ProfileViewModel by viewModel()
    private lateinit var genderDialog: GenderSelectionDialog
    private lateinit var dobDialog: DobDialogNew

    private var selectedGender: String = ""
    private var selectedDob: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHeader()
        initDialogs()
        setupEdgeToEdge()
        setupTextWatchers()
        setupClickListeners()
        validateForm()
        observeViewModel()
    }

    private fun initHeader() {
        binding.ivHeader.tvHeading.text = getString(R.string.introduce_yourself)
        binding.ivHeader.backArrow.visibility = View.GONE
    }



    private fun initDialogs() {
        genderDialog = GenderSelectionDialog(this, this)
        dobDialog = DobDialogNew(this, this)
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val imeInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            // 1. Top status bar insets applied to header padding so it stays below status icons
            val baseHeaderPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp)
            binding.ivHeader.root.updatePadding(
                top = statusBarInsets.top + baseHeaderPadding
            )
            
            // 3. Bottom insets: root padding dynamically handles max of navigation bar or IME keyboard
            val bottomPadding = maxOf(navBarInsets.bottom, imeInsets.bottom)
            binding.root.updatePadding(bottom = bottomPadding)

            // 4. Smooth scroll to reveal About field when keyboard is open and About field is focused
            if (imeInsets.bottom > 0 && binding.etAbout.hasFocus()) {
                binding.scrollView.post {
                    binding.scrollView.smoothScrollTo(0, binding.main.bottom)
                }
            }

            windowInsets
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v != null && (v == binding.etFirstName || v == binding.etNickName || v == binding.etAbout)) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    // Only dismiss keyboard and focus if tap is OUTSIDE all input fields
                    val isTappingAnotherInput = isTouchInsideView(binding.etFirstName, ev) ||
                            isTouchInsideView(binding.etNickName, ev) ||
                            isTouchInsideView(binding.etAbout, ev)

                    if (!isTappingAnotherInput) {
                        v.clearFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchInsideView(view: View, ev: MotionEvent): Boolean {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }

    private fun setupTextWatchers() {
        binding.etFirstName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.textErrorFN.isVisible = false
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etNickName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.textErrorNN.isVisible = false
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etAbout.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                binding.tvCounter.text = "$length/150"
                binding.textErrorAbout.isVisible = false
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClickListeners() {
        binding.etDob.setOnClickListener {
            dobDialog.show(selectedDob)
        }
        binding.imgDob.setOnClickListener {
            dobDialog.show(selectedDob)
        }

        binding.etGender.setOnClickListener {
            genderDialog.showDialog(selectedGender)
        }

        binding.tvContinue.setOnClickListener {
            if (performValidation()) {
                val rawName = binding.etFirstName.text?.toString()?.trim().orEmpty()
                val nameParts = rawName.split("\\s+".toRegex(), limit = 2)
                val firstName = nameParts.getOrNull(0).orEmpty()
                val lastName = if (nameParts.size > 1) nameParts[1] else ""
                val nickName = binding.etNickName.text?.toString()?.trim().orEmpty()
                val about = binding.etAbout.text?.toString()?.trim().orEmpty()

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

                // Update profile on backend
                viewModel.updateProfile(
                    firstName = firstName,
                    lastName = lastName,
                    nickName = nickName,
                    dob = selectedDob,
                    gender = selectedGender,
                    location = null,
                    about = about
                )
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateProfileState.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            binding.tvContinue.isEnabled = false
                            binding.tvContinue.alpha = 0.5f
                        }
                        is Resource.Success -> {
                            binding.tvContinue.isEnabled = true
                            binding.tvContinue.alpha = 1.0f
                            viewModel.resetUpdateProfileState()
                            Toast.makeText(this@ProfileActivity, "Profile saved successfully", Toast.LENGTH_SHORT).show()

                            AppNavigator.navigateTo(
                                context = this@ProfileActivity,
                                destination = Destination.ProfileVerification,
                                finishCurrent = false
                            )
                        }
                        is Resource.Error -> {
                            binding.tvContinue.isEnabled = true
                            binding.tvContinue.alpha = 1.0f
                            val errorMsg = resource.message
                            Toast.makeText(this@ProfileActivity, errorMsg, Toast.LENGTH_LONG).show()

                            // Highlight specific field errors returned by backend
                            if (errorMsg.contains("dateOfBirth", ignoreCase = true)) {
                                binding.textErrorDob.text = errorMsg
                                binding.textErrorDob.isVisible = true
                            }
                            if (errorMsg.contains("gender", ignoreCase = true)) {
                                binding.textErrorGender.text = errorMsg
                                binding.textErrorGender.isVisible = true
                            }
                            if (errorMsg.contains("firstName", ignoreCase = true)) {
                                binding.textErrorFN.text = errorMsg
                                binding.textErrorFN.isVisible = true
                            }
                            if (errorMsg.contains("about", ignoreCase = true)) {
                                binding.textErrorAbout.text = errorMsg
                                binding.textErrorAbout.isVisible = true
                            }
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onGenderSelected(gender: String) {
        if (gender.isNotBlank()) {
            selectedGender = gender
            binding.etGender.setText(gender)
            binding.textErrorGender.isVisible = false
            validateForm()
        }
    }

    override fun onDateSelected(formattedDate: String, age: Int) {
        selectedDob = formattedDate
        binding.etDob.setText(formattedDate)
        binding.textErrorDob.isVisible = false
        validateForm()
    }

    private fun performValidation(): Boolean {
        var isValid = true

        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        if (firstName.isEmpty()) {
            binding.textErrorFN.isVisible = true
            isValid = false
        } else {
            binding.textErrorFN.isVisible = false
        }

        val nickName = binding.etNickName.text?.toString()?.trim().orEmpty()
        if (nickName.isEmpty()) {
            binding.textErrorNN.isVisible = true
            isValid = false
        } else {
            binding.textErrorNN.isVisible = false
        }

        if (selectedDob.isEmpty()) {
            binding.textErrorDob.isVisible = true
            isValid = false
        } else {
            binding.textErrorDob.isVisible = false
        }

        if (selectedGender.isEmpty()) {
            binding.textErrorGender.isVisible = true
            isValid = false
        } else {
            binding.textErrorGender.isVisible = false
        }

        val about = binding.etAbout.text?.toString()?.trim().orEmpty()
        if (about.isEmpty()) {
            binding.textErrorAbout.isVisible = true
            isValid = false
        } else {
            binding.textErrorAbout.isVisible = false
        }

        return isValid
    }

    private fun validateForm() {
        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val nickName = binding.etNickName.text?.toString()?.trim().orEmpty()
        val about = binding.etAbout.text?.toString()?.trim().orEmpty()

        val allFilled = firstName.isNotEmpty() &&
                nickName.isNotEmpty() &&
                selectedDob.isNotEmpty() &&
                selectedGender.isNotEmpty() &&
                about.isNotEmpty()

        binding.tvContinue.isEnabled = allFilled
        binding.tvContinue.alpha = if (allFilled) 1.0f else 0.5f
    }
}
