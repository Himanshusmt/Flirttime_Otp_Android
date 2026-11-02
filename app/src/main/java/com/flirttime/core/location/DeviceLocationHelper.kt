package com.flirttime.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

object DeviceLocationHelper {

    private const val FRESH_LOCATION_MS = 2 * 60 * 1000L
    private const val LOCATION_TIMEOUT_MS = 12_000L

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val lastKnown = lastKnownLocation(manager)
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= FRESH_LOCATION_MS) {
            return lastKnown
        }
        val live = requestFreshLocation(manager)
        return live ?: lastKnown
    }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): Address? {
        return geocodeAddresses(context) { geocoder, callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { callback(it) }
            } else {
                @Suppress("DEPRECATION")
                callback(geocoder.getFromLocation(latitude, longitude, 1))
            }
        }.firstOrNull()
    }

    suspend fun searchAddresses(context: Context, query: String, maxResults: Int = 8): List<Address> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return geocodeAddresses(context) { geocoder, callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(trimmed, maxResults) { callback(it) }
            } else {
                @Suppress("DEPRECATION")
                callback(geocoder.getFromLocationName(trimmed, maxResults))
            }
        }
    }

    fun toCityStateCountry(address: Address): String? {
        val parts = listOfNotNull(
            address.locality ?: address.subAdminArea,
            address.adminArea,
            address.countryName
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    fun toCityOnly(address: Address): String? {
        return listOfNotNull(address.locality, address.subAdminArea, address.featureName)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    fun toResolvedPlace(address: Address, fallback: Location? = null): ResolvedPlace? {
        val location = address.locationOrNull() ?: fallback ?: return null
        val label = toCityStateCountry(address) ?: return null
        val city = toCityOnly(address) ?: label.substringBefore(",")
        return ResolvedPlace(
            label = label,
            city = city,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private fun Address.locationOrNull(): Location? {
        if (!hasLatitude() || !hasLongitude()) return null
        return Location("geocoder").apply {
            latitude = this@locationOrNull.latitude
            longitude = this@locationOrNull.longitude
        }
    }

    private fun lastKnownLocation(manager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }
    }

    private suspend fun requestFreshLocation(manager: LocationManager): Location? {
        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    cleanup()
                    if (continuation.isActive) continuation.resume(location)
                }

                override fun onProviderDisabled(provider: String) = Unit
                override fun onProviderEnabled(provider: String) = Unit

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                fun cleanup() {
                    try {
                        manager.removeUpdates(this)
                    } catch (_: Exception) {
                    }
                    handler.removeCallbacksAndMessages(null)
                }
            }

            val timeout = Runnable {
                listener.cleanup()
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                listener.cleanup()
            }

            try {
                val gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!gpsEnabled && !networkEnabled) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
                handler.postDelayed(timeout, LOCATION_TIMEOUT_MS)
                if (gpsEnabled) {
                    manager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
                if (networkEnabled) {
                    manager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            } catch (_: SecurityException) {
                listener.cleanup()
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private suspend fun geocodeAddresses(
        context: Context,
        request: (Geocoder, (List<Address>?) -> Unit) -> Unit
    ): List<Address> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                request(geocoder) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses.orEmpty())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    var result: List<Address> = emptyList()
                    request(geocoder) { addresses ->
                        result = addresses.orEmpty()
                    }
                    result
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }
}
