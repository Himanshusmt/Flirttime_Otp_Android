package com.flirttime.di

import com.flirttime.core.common.NetworkConnectivityObserver
import com.flirttime.core.database.SessionManager
import com.flirttime.core.network.ApiService
import com.flirttime.core.network.NetworkModuleProvider
import com.flirttime.feature.auth.data.network.AuthApiService
import com.flirttime.feature.auth.data.repository.AuthRepositoryImpl
import com.flirttime.feature.auth.domain.repository.AuthRepository
import com.flirttime.feature.auth.presentation.email.EmailVerifyViewModel
import com.flirttime.feature.auth.presentation.login.LoginViewModel
import com.flirttime.feature.auth.presentation.otp.OtpViewModel
import com.flirttime.feature.auth.presentation.phone.PhoneInputViewModel
import com.flirttime.feature.main.presentation.moments.MomentsViewModel
import com.flirttime.feature.main.presentation.moments.add.AddMomentViewModel
import com.flirttime.feature.moments.data.network.MomentsApiService
import com.flirttime.feature.moments.data.repository.MomentsRepositoryImpl
import com.flirttime.feature.moments.domain.repository.MomentsRepository
import com.flirttime.feature.profile.data.network.UserApiService
import com.flirttime.feature.profile.data.repository.UserRepositoryImpl
import com.flirttime.feature.profile.domain.repository.UserRepository
import com.flirttime.feature.profile.presentation.gesture.GestureVerificationViewModel
import com.flirttime.feature.profile.presentation.intro.ProfileViewModel
import com.flirttime.feature.profile.presentation.preview.PicturePreviewViewModel
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
    single<UserApiService> { get<Retrofit>().create(UserApiService::class.java) }
    single<MomentsApiService> { get<Retrofit>().create(MomentsApiService::class.java) }

    // Auth Feature
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    viewModelOf(::LoginViewModel)
    viewModelOf(::PhoneInputViewModel)
    viewModelOf(::OtpViewModel)
    viewModelOf(::EmailVerifyViewModel)

    // User Profile Feature
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    viewModelOf(::ProfileViewModel)
    viewModelOf(::PicturePreviewViewModel)
    viewModelOf(::GestureVerificationViewModel)

    // Moments Feature
    single<MomentsRepository> { MomentsRepositoryImpl(get()) }
    viewModelOf(::MomentsViewModel)
    viewModelOf(::AddMomentViewModel)
}
