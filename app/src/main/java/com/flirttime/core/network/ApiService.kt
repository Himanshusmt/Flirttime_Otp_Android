package com.flirttime.core.network

import com.flirttime.feature.auth.CountryDataResponse
import com.flirttime.feature.auth.LoginByPhoneResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @GET("get-countries")
    suspend fun getCountries(): CountryDataResponse

    @POST("login-with-phone")
    suspend fun loginByPhone(@Body body: Map<String, String>): LoginByPhoneResponse

    @POST("login-with-email")
    suspend fun loginByEmail(@Body body: Map<String, String>): LoginByPhoneResponse

    @POST("login-with-google")
    suspend fun loginByGoogle(@Body body: Map<String, String>): LoginByPhoneResponse
}
