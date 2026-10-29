package com.flirttime.feature.auth.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.CountryItem
import com.flirttime.feature.auth.OtpResponseData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneInputViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _countriesState = MutableStateFlow<Resource<List<CountryItem>>>(Resource.Loading)
    val countriesState: StateFlow<Resource<List<CountryItem>>> = _countriesState.asStateFlow()

    private val _requestOtpState = MutableStateFlow<Resource<OtpResponseData>?>(null)
    val requestOtpState: StateFlow<Resource<OtpResponseData>?> = _requestOtpState.asStateFlow()

    init {
        loadCountries()
    }

    fun loadCountries() {
        viewModelScope.launch {
            authRepository.getCountries().collect { result ->
                _countriesState.value = result
            }
        }
    }

    fun requestPhoneOtp(countryCode: String, phone: String) {
        viewModelScope.launch {
            authRepository.requestPhoneOtp(countryCode, phone).collect { result ->
                _requestOtpState.value = result
            }
        }
    }

    fun resetRequestOtpState() {
        _requestOtpState.value = null
    }
}