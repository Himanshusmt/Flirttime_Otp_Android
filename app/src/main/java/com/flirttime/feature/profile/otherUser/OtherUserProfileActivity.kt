package com.flirttime.feature.profile.otherUser

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.databinding.ActivityOtherUserProfileBinding
import com.flirttime.feature.profile.UserSearchItemDto
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class OtherUserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtherUserProfileBinding
    private val viewModel: OtherUserProfileViewModel by viewModel()

    private val photosAdapter by lazy {
        OtherUserPhotosAdapter { url, pos ->
            // View full screen photo if needed
        }
    }
    private val interestsAdapter by lazy { OtherUserInterestsAdapter() }
    private val momentsAdapter by lazy {
        OtherUserMomentsAdapter { moment ->
            Toast.makeText(this, moment.caption ?: "Moment clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private var targetUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentEdgeToEdge()
        binding = ActivityOtherUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupClickListeners()
        setupInsets()
        observeViewModel()
        parseIntentData()
    }

    private fun setupTransparentEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    private fun parseIntentData() {
        targetUserId = intent.getStringExtra(AppNavigator.EXTRA_USER_ID).orEmpty()
        val username = intent.getStringExtra(AppNavigator.EXTRA_USERNAME)
        val name = intent.getStringExtra(AppNavigator.EXTRA_NAME)
        val age = intent.getIntExtra(AppNavigator.EXTRA_AGE, 0).takeIf { it > 0 }
        val photoUrl = intent.getStringExtra(AppNavigator.EXTRA_PROFILE_PHOTO_URL)
        val city = intent.getStringExtra(AppNavigator.EXTRA_CITY)
        val bio = intent.getStringExtra(AppNavigator.EXTRA_BIO)

        val initialData = if (targetUserId.isNotEmpty() && (!username.isNullOrEmpty() || !name.isNullOrEmpty())) {
            UserSearchItemDto(
                id = targetUserId,
                userId = targetUserId,
                username = username,
                name = name,
                age = age,
                profilePhotoUrl = photoUrl,
                city = city,
                about = bio
            )
        } else null

        if (initialData != null) {
            bindUserToUi(initialData)
        }

        if (targetUserId.isNotEmpty()) {
            viewModel.loadProfile(userId = targetUserId, initialData = initialData)
        } else {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupAdapters() {
        binding.rvPhotos.apply {
            layoutManager = LinearLayoutManager(this@OtherUserProfileActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = photosAdapter
        }

        binding.rvInterests.apply {
            layoutManager = LinearLayoutManager(this@OtherUserProfileActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = interestsAdapter
        }

        binding.rvMoments.apply {
            layoutManager = LinearLayoutManager(this@OtherUserProfileActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = momentsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "Block or Report user", Toast.LENGTH_SHORT).show()
        }

        binding.btnNope.setOnClickListener {
            animateButtonClick(it)
            viewModel.submitAction(targetUserId, "nope")
        }

        binding.btnLike.setOnClickListener {
            animateButtonClick(it)
            viewModel.submitAction(targetUserId, "like")
        }

        binding.btnChat.setOnClickListener {
            animateButtonClick(it)
            Toast.makeText(this, "Starting chat...", Toast.LENGTH_SHORT).show()
        }

        binding.btnSeeAllMoments.setOnClickListener {
            Toast.makeText(this, "See all moments", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOtherUserProfile) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.layoutTopActions.updatePadding(top = statusBarInsets.top + 8)

            binding.layoutBottomActions.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = navBarInsets.bottom + 20
            }

            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Profile State
                launch {
                    viewModel.profileState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                if (binding.tvNameAge.text.isNullOrEmpty()) {
                                    binding.progressBar.visibility = View.VISIBLE
                                }
                            }
                            is Resource.Success -> {
                                binding.progressBar.visibility = View.GONE
                                bindUserToUi(resource.data)
                            }
                            is Resource.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@OtherUserProfileActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // 2. Moments State
                launch {
                    viewModel.momentsState.collect { moments ->
                        momentsAdapter.submitList(moments)
                        val count = moments.size
                        binding.tvMomentsTitle.text = "Moments · $count"
                        binding.layoutMomentsHeader.visibility = if (count > 0) View.VISIBLE else View.GONE
                        binding.rvMoments.visibility = if (count > 0) View.VISIBLE else View.GONE
                    }
                }

                // 3. Action Events (Like / Nope)
                launch {
                    viewModel.actionEvent.collect { (action, data) ->
                        if (action == "like") {
                            if (data?.isMatched == true) {
                                Toast.makeText(this@OtherUserProfileActivity, "It's a Match! 🎉", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@OtherUserProfileActivity, "Profile Liked ❤️", Toast.LENGTH_SHORT).show()
                            }
                        } else if (action == "nope") {
                            Toast.makeText(this@OtherUserProfileActivity, "Passed", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }

                // 4. Error Messages
                launch {
                    viewModel.errorMessage.collect { msg ->
                        Toast.makeText(this@OtherUserProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun bindUserToUi(user: UserSearchItemDto) {
        // Name & Age
        val displayName = user.effectiveName
        val ageText = user.age?.let { ", $it" } ?: ""
        binding.tvNameAge.text = "$displayName$ageText"

        // Verified Badge
        binding.ivVerifiedBadge.visibility = if (user.isVerified == true) View.VISIBLE else View.GONE

        // Location & Distance
        val locText = user.effectiveLocation
        if (locText.isNotEmpty()) {
            binding.layoutLocation.visibility = View.VISIBLE
            binding.tvLocation.text = locText
        } else {
            binding.layoutLocation.visibility = View.GONE
        }

        // Bio Quote
        val bioText = user.effectiveBio
        if (bioText.isNotEmpty()) {
            binding.tvBio.visibility = View.VISIBLE
            binding.tvBio.text = if (bioText.startsWith("“") || bioText.startsWith("\"")) bioText else "“$bioText”"
        } else {
            binding.tvBio.visibility = View.GONE
        }

        // Profile Photo & Cover
        val photoUrl = user.effectivePhoto
        val coverUrl = user.coverPhotoUrl ?: user.coverPhoto ?: photoUrl

        if (!photoUrl.isNullOrBlank()) {
            binding.ivAvatar.setImageURI(photoUrl)
        } else {
            binding.ivAvatar.setActualImageResource(R.drawable.img_login)
        }

        if (!coverUrl.isNullOrBlank()) {
            binding.ivCoverPhoto.setImageURI(coverUrl)
        } else {
            binding.ivCoverPhoto.setActualImageResource(R.drawable.img_login)
        }

        // Photos List
        val photoUrls = mutableListOf<String>()
        user.photos?.forEach { p ->
            val u = p.url ?: p.thumbnailUrl
            if (!u.isNullOrBlank()) photoUrls.add(u)
        }
        if (photoUrls.isEmpty() && photoUrl.isNotBlank()) {
            photoUrls.add(photoUrl)
        }
        photosAdapter.submitList(photoUrls)
        binding.tvPhotosCount.text = "${photoUrls.size}"
        binding.layoutPhotosHeader.visibility = if (photoUrls.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvPhotos.visibility = if (photoUrls.isNotEmpty()) View.VISIBLE else View.GONE

        // Interests List
        val interestsList = user.interests.orEmpty()
        interestsAdapter.submitList(interestsList)
        binding.layoutInterestsHeader.visibility = if (interestsList.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvInterests.visibility = if (interestsList.isNotEmpty()) View.VISIBLE else View.GONE

        // Common Interests Badge
        val commonCount = user.sharedInterests?.size ?: interestsList.count { it.isCommon == true }
        if (commonCount > 0) {
            binding.tvCommonInterestsBadge.visibility = View.VISIBLE
            binding.tvCommonInterestsBadge.text = "💎 $commonCount in common"
        } else {
            binding.tvCommonInterestsBadge.visibility = View.GONE
        }
    }

    private fun animateButtonClick(view: View) {
        view.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
}
