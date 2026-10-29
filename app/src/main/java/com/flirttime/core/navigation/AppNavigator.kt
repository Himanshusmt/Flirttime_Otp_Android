package com.flirttime.core.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.flirttime.MainActivity
import com.flirttime.feature.auth.login.LoginActivity
import com.flirttime.feature.auth.phone.PhoneInputActivity
import com.flirttime.feature.auth.email.EmailVerifyActivity
import com.flirttime.feature.auth.otp.OtpActivity
import com.flirttime.feature.onboarding.Intro1Activity
import com.flirttime.feature.onboarding.Intro2Activity
import com.flirttime.feature.onboarding.Intro3Activity
import com.flirttime.feature.onboarding.PermissionActivity
import com.flirttime.feature.splash.SplashActivity

/**
 * Type-Safe Navigation Destinations and Router.
 * Replaces unstructured string/Intent extras with statically typed, compile-time verified arguments.
 */
sealed class Destination {
    data object Splash : Destination()
    data object Intro1 : Destination()
    data object Intro2 : Destination()
    data object Intro3 : Destination()
    data object Permission : Destination()
    data object Login : Destination()
    
    data class PhoneInput(
        val prefilledPhone: String? = null,
        val defaultCountryCode: String = "+91"
    ) : Destination()

    data class Otp(
        val otpFrom: String = "mobile",
        val phoneCode: String = "",
        val identifier: String,
        val verificationId: String? = null
    ) : Destination()

    data class Main(
        val targetTab: Int = 2
    ) : Destination()

    data class Webview(
        val title: String,
        val url: String
    ) : Destination()

    data object EmailInput : Destination()
    data object IntroduceYourself : Destination()
    data object ProfileVerification : Destination()
    data class PicturePreview(val imageUri: String) : Destination()
    data object GestureVerification : Destination()
    data object VerificationProcess : Destination()
    data object Notification : Destination()
    data object AddMoment : Destination()
    data object MyMoments : Destination()
    data object Settings : Destination()
    data object PersonalDetails : Destination()
    data object NotificationSettings : Destination()
    data object BlockedReported : Destination()
    data object EditProfile : Destination()
    data class UserProfile(
        val userId: String,
        val username: String? = null,
        val name: String? = null,
        val age: Int? = null,
        val profilePhotoUrl: String? = null,
        val city: String? = null,
        val bio: String? = null
    ) : Destination()
}

object AppNavigator {

    const val EXTRA_OTP_FROM = "extra_otp_from"
    const val EXTRA_PREFILLED_PHONE = "extra_prefilled_phone"
    const val EXTRA_DEFAULT_COUNTRY_CODE = "extra_default_country_code"
    const val EXTRA_PHONE_CODE = "extra_phone_code"
    const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    const val EXTRA_VERIFICATION_ID = "extra_verification_id"
    const val EXTRA_TARGET_TAB = "extra_target_tab"
    const val EXTRA_WEBVIEW_TITLE = "extra_webview_title"
    const val EXTRA_WEBVIEW_URL = "extra_webview_url"
    const val EXTRA_IMAGE_URI = "extra_image_uri"
    const val EXTRA_USER_ID = "extra_user_id"
    const val EXTRA_USERNAME = "extra_username"
    const val EXTRA_NAME = "extra_name"
    const val EXTRA_AGE = "extra_age"
    const val EXTRA_PROFILE_PHOTO_URL = "extra_profile_photo_url"
    const val EXTRA_CITY = "extra_city"
    const val EXTRA_BIO = "extra_bio"

    fun navigateTo(
        context: Context,
        destination: Destination,
        clearBackStack: Boolean = false,
        finishCurrent: Boolean = false
    ) {
        val targetClass = when (destination) {
            is Destination.Splash -> SplashActivity::class.java
            is Destination.Intro1 -> Intro1Activity::class.java
            is Destination.Intro2 -> Intro2Activity::class.java
            is Destination.Intro3 -> Intro3Activity::class.java
            is Destination.Permission -> PermissionActivity::class.java
            is Destination.Login -> LoginActivity::class.java
            is Destination.PhoneInput -> PhoneInputActivity::class.java
            is Destination.Otp -> OtpActivity::class.java
            is Destination.Main -> MainActivity::class.java
            is Destination.Webview -> com.flirttime.feature.webview.WebviewActivity::class.java
            is Destination.EmailInput -> EmailVerifyActivity::class.java
            is Destination.IntroduceYourself -> com.flirttime.feature.onboarding.ProfileActivity::class.java
            is Destination.ProfileVerification -> com.flirttime.feature.onboarding.ProfileVerificationActivity::class.java
            is Destination.PicturePreview -> com.flirttime.feature.onboarding.PicturePreviewActivity::class.java
            is Destination.GestureVerification -> com.flirttime.feature.onboarding.GestureVerificationActivity::class.java
            is Destination.VerificationProcess -> com.flirttime.feature.onboarding.VerificationProcessActivity::class.java
            is Destination.Notification -> com.flirttime.feature.notification.NotificationActivity::class.java
            is Destination.AddMoment -> com.flirttime.feature.moments.add.AddMomentActivity::class.java
            is Destination.MyMoments -> com.flirttime.feature.main.profile.MyMomentsActivity::class.java
            is Destination.Settings -> com.flirttime.feature.profile.settings.SettingsActivity::class.java
            is Destination.PersonalDetails -> com.flirttime.feature.profile.settings.PersonalDetailsActivity::class.java
            is Destination.NotificationSettings -> com.flirttime.feature.profile.settings.NotificationSettingsActivity::class.java
            is Destination.BlockedReported -> com.flirttime.feature.profile.settings.BlockedReportedActivity::class.java
            is Destination.EditProfile -> com.flirttime.feature.profile.edit.EditProfileActivity::class.java
            is Destination.UserProfile -> com.flirttime.feature.profile.otherUser.OtherUserProfileActivity::class.java
        }

        val intent = Intent(context, targetClass).apply {
            when (destination) {
                is Destination.PhoneInput -> {
                    destination.prefilledPhone?.let { putExtra(EXTRA_PREFILLED_PHONE, it) }
                    putExtra(EXTRA_DEFAULT_COUNTRY_CODE, destination.defaultCountryCode)
                }
                is Destination.Otp -> {
                    putExtra(EXTRA_OTP_FROM, destination.otpFrom)
                    putExtra(EXTRA_PHONE_CODE, destination.phoneCode)
                    putExtra(EXTRA_PHONE_NUMBER, destination.identifier)
                    destination.verificationId?.let { putExtra(EXTRA_VERIFICATION_ID, it) }
                }
                is Destination.Main -> {
                    putExtra(EXTRA_TARGET_TAB, destination.targetTab)
                }
                is Destination.Webview -> {
                    putExtra(EXTRA_WEBVIEW_TITLE, destination.title)
                    putExtra(EXTRA_WEBVIEW_URL, destination.url)
                }
                is Destination.PicturePreview -> {
                    putExtra(EXTRA_IMAGE_URI, destination.imageUri)
                }
                is Destination.UserProfile -> {
                    putExtra(EXTRA_USER_ID, destination.userId)
                    destination.username?.let { putExtra(EXTRA_USERNAME, it) }
                    destination.name?.let { putExtra(EXTRA_NAME, it) }
                    destination.age?.let { putExtra(EXTRA_AGE, it) }
                    destination.profilePhotoUrl?.let { putExtra(EXTRA_PROFILE_PHOTO_URL, it) }
                    destination.city?.let { putExtra(EXTRA_CITY, it) }
                    destination.bio?.let { putExtra(EXTRA_BIO, it) }
                }
                else -> Unit
            }

            if (clearBackStack) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        context.startActivity(intent)

        if (finishCurrent && context is Activity) {
            context.finish()
        }
    }
}
