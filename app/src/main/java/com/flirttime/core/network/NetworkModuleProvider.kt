package com.flirttime.core.network

import android.content.Context
import com.flirttime.BuildConfig
import com.flirttime.core.common.NetworkUtils
import com.flirttime.core.database.SessionManager
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModuleProvider {

    private const val CACHE_SIZE = 20L * 1024 * 1024 // 20 MB Cache
    private const val TIMEOUT_SECONDS = 30L

    fun provideOkHttpClient(context: Context, sessionManager: SessionManager): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, CACHE_SIZE)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            val token = sessionManager.getCachedToken()
            if (token.isNotBlank()) {
                val bearerToken = if (token.startsWith("Bearer ", ignoreCase = true)) {
                    token
                } else {
                    "Bearer $token"
                }
                requestBuilder.header("Authorization", bearerToken)
            }
            requestBuilder.header("Accept", "application/json")
            chain.proceed(requestBuilder.build())
        }

        val offlineCacheInterceptor = Interceptor { chain ->
            var request = chain.request()
            if (!NetworkUtils.isNetworkAvailable(context)) {
                // When offline, force cached responses if available (max 7 days old)
                val cacheControl = CacheControl.Builder()
                    .onlyIfCached()
                    .maxStale(7, TimeUnit.DAYS)
                    .build()
                request = request.newBuilder()
                    .cacheControl(cacheControl)
                    .build()
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(offlineCacheInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
