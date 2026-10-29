package com.flirttime.di

import com.flirttime.core.common.NetworkConnectivityObserver
import com.flirttime.core.database.SessionManager
import com.flirttime.core.network.ApiService
import com.flirttime.core.network.NetworkModuleProvider
import com.flirttime.feature.auth.AuthApiService
import com.flirttime.feature.auth.AuthRepositoryImpl
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.email.EmailVerifyViewModel
import com.flirttime.feature.auth.login.LoginViewModel
import com.flirttime.feature.auth.otp.OtpViewModel
import com.flirttime.feature.auth.phone.PhoneInputViewModel
import com.flirttime.feature.moments.MomentsViewModel
import com.flirttime.feature.moments.add.AddMomentViewModel
import com.flirttime.feature.main.MainApiService
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.main.MainRepositoryImpl
import com.flirttime.feature.main.matches.MatchesRepository
import com.flirttime.feature.main.matches.MatchesRepositoryImpl
import com.flirttime.feature.main.profile.UserProfileViewModel
import com.flirttime.feature.main.explore.ExploreViewModel
import com.flirttime.feature.main.matches.MatchesViewModel
import com.flirttime.feature.moments.MomentsRepositoryImpl
import com.flirttime.feature.moments.MomentsRepository
import com.flirttime.feature.notification.NotificationApiService
import com.flirttime.feature.notification.NotificationRepository
import com.flirttime.feature.notification.NotificationRepositoryImpl
import com.flirttime.feature.notification.NotificationViewModel
import com.flirttime.feature.profile.UserApiService
import com.flirttime.feature.profile.UserRepositoryImpl
import com.flirttime.feature.profile.UserRepository
import com.flirttime.feature.onboarding.GestureVerificationViewModel
import com.flirttime.feature.onboarding.ProfileViewModel
import com.flirttime.feature.onboarding.PicturePreviewViewModel
import com.flirttime.feature.profile.edit.EditProfileViewModel
import com.flirttime.feature.profile.otherUser.OtherUserProfileViewModel
import com.flirttime.feature.profile.settings.SettingsViewModel
import com.flirttime.feature.splash.SplashViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit

val appModule = module {
    // Database / Storage
    single { SessionManager(get()) }

    // Connectivity
    single { NetworkConnectivityObserver(get()) }

    // Network
    single { NetworkModuleProvider.provideOkHttpClient(get(), get()) }
    single { NetworkModuleProvider.provideRetrofit(get()) }
    single<ApiService> { get<Retrofit>().create(ApiService::class.java) }
    single<AuthApiService> { get<Retrofit>().create(AuthApiService::class.java) }
    single<MainApiService> { get<Retrofit>().create(MainApiService::class.java) }
    single<UserApiService> { get<Retrofit>().create(UserApiService::class.java) }
    single<NotificationApiService> { get<Retrofit>().create(NotificationApiService::class.java) }

    // Auth Feature
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    viewModelOf(::SplashViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::PhoneInputViewModel)
    viewModelOf(::OtpViewModel)
    viewModelOf(::EmailVerifyViewModel)

    // Main Feature Repository
    single<MainRepository> { MainRepositoryImpl(get(), get()) }

    // User Profile / Settings Feature
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    viewModelOf(::ProfileViewModel)
    viewModelOf(::PicturePreviewViewModel)
    viewModelOf(::GestureVerificationViewModel)

    // Moments Feature
    single<MomentsRepository> { MomentsRepositoryImpl(get()) }
    viewModelOf(::MomentsViewModel)
    viewModelOf(::AddMomentViewModel)
    single { UserProfileViewModel(get(), get(), get(), get(), get()) }

    // Notification Feature
    single<NotificationRepository> { NotificationRepositoryImpl(get()) }
    viewModelOf(::NotificationViewModel)

    // Other User Profile
    viewModelOf(::OtherUserProfileViewModel)

    // Settings
    viewModelOf(::SettingsViewModel)

    // Edit Profile Feature
    viewModelOf(::EditProfileViewModel)

    // Matches Feature
    single<MatchesRepository> { MatchesRepositoryImpl(get()) }
    viewModelOf(::MatchesViewModel)

    // Explore Feature
    viewModelOf(::ExploreViewModel)
}
