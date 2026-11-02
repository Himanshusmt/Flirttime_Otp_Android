package com.flirttime.core.location

import android.location.Address
import android.location.Location
import com.flirttime.R
import com.flirttime.feature.moments.location.SpotCategory
import com.flirttime.feature.moments.location.VibeSpotItem
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object PlaceSearchHelper {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private const val USER_AGENT = "FlirtTimeAndroid/1.0 (location picker)"

    suspend fun searchNearby(
        latitude: Double,
        longitude: Double,
        category: SpotCategory,
        query: String = ""
    ): List<VibeSpotItem> {
        val trimmed = query.trim()
        val overpass = if (trimmed.isEmpty()) {
            queryOverpass(latitude, longitude, category)
        } else {
            emptyList()
        }
        if (overpass.isNotEmpty()) return overpass

        val searchQuery = when {
            trimmed.isNotEmpty() -> trimmed
            category == SpotCategory.ALL -> "cafe"
            else -> category.searchQuery
        }
        return queryNominatim(searchQuery, latitude, longitude, category)
    }

    suspend fun searchText(
        query: String,
        latitude: Double?,
        longitude: Double?
    ): List<VibeSpotItem> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return queryNominatim(trimmed, latitude, longitude, SpotCategory.ALL)
    }

    fun toCitySuggestion(address: Address): CitySuggestion? {
        val label = DeviceLocationHelper.toCityStateCountry(address) ?: return null
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        val city = DeviceLocationHelper.toCityOnly(address) ?: label.substringBefore(",")
        return CitySuggestion(
            title = city,
            subtitle = label,
            latitude = address.latitude,
            longitude = address.longitude
        )
    }

    private suspend fun queryOverpass(
        latitude: Double,
        longitude: Double,
        category: SpotCategory
    ): List<VibeSpotItem> = withContext(Dispatchers.IO) {
        val filters = category.overpassFilters.joinToString("\n") { filter ->
            """node$filter(around:4000,$latitude,$longitude);"""
        }
        val body = """
            [out:json][timeout:15];
            (
            $filters
            );
            out body 30;
        """.trimIndent()
        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = response.body?.string().orEmpty()
                parseOverpass(json, latitude, longitude)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun queryNominatim(
        query: String,
        latitude: Double?,
        longitude: Double?,
        fallbackCategory: SpotCategory
    ): List<VibeSpotItem> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = buildString {
            append("https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=15&q=")
            append(encoded)
            if (latitude != null && longitude != null) {
                append("&lat=").append(latitude)
                append("&lon=").append(longitude)
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = response.body?.string().orEmpty()
                parseNominatim(json, latitude, longitude, fallbackCategory)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseOverpass(json: String, userLat: Double, userLng: Double): List<VibeSpotItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val elements = root.getAsJsonArray("elements") ?: return emptyList()
            elements.mapNotNull { element ->
                val obj = element.asJsonObject
                val tags = obj.getAsJsonObject("tags") ?: return@mapNotNull null
                val name = tags.get("name")?.asString?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val lat = obj.get("lat")?.asDouble ?: return@mapNotNull null
                val lng = obj.get("lon")?.asDouble ?: return@mapNotNull null
                val city = tags.get("addr:city")?.asString
                    ?: tags.get("addr:suburb")?.asString
                val street = listOfNotNull(
                    tags.get("addr:housenumber")?.asString,
                    tags.get("addr:street")?.asString
                ).joinToString(" ").ifBlank { null }
                val amenity = tags.get("amenity")?.asString
                    ?: tags.get("leisure")?.asString
                    ?: tags.get("tourism")?.asString
                    ?: tags.get("shop")?.asString
                toSpot(
                    id = obj.get("id")?.asString ?: "$lat,$lng",
                    name = name,
                    city = city,
                    address = city ?: street.orEmpty(),
                    latitude = lat,
                    longitude = lng,
                    userLat = userLat,
                    userLng = userLng,
                    category = categoryFromAmenity(amenity)
                )
            }.distinctBy { it.name.lowercase() to it.address.lowercase() }
                .sortedBy { distanceMeters(userLat, userLng, it.latitude ?: userLat, it.longitude ?: userLng) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseNominatim(
        json: String,
        userLat: Double?,
        userLng: Double?,
        fallbackCategory: SpotCategory
    ): List<VibeSpotItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JsonParser.parseString(json).asJsonArray
            array.mapNotNull { element ->
                val obj = element.asJsonObject
                val lat = obj.get("lat")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                val lng = obj.get("lon")?.asString?.toDoubleOrNull() ?: return@mapNotNull null
                val address = obj.getAsJsonObject("address")
                val name = obj.get("name")?.asString?.trim()
                    ?.ifBlank { null }
                    ?: address?.get("amenity")?.asString
                    ?: obj.get("display_name")?.asString?.substringBefore(",")
                    ?: return@mapNotNull null
                val city = address?.get("city")?.asString
                    ?: address?.get("town")?.asString
                    ?: address?.get("village")?.asString
                    ?: address?.get("suburb")?.asString
                val type = obj.get("type")?.asString ?: obj.get("class")?.asString
                toSpot(
                    id = obj.get("place_id")?.asString ?: "$lat,$lng",
                    name = name,
                    city = city,
                    address = city.orEmpty(),
                    latitude = lat,
                    longitude = lng,
                    userLat = userLat,
                    userLng = userLng,
                    category = categoryFromAmenity(type) ?: fallbackCategory
                )
            }.distinctBy { it.name.lowercase() to it.address.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toSpot(
        id: String,
        name: String,
        city: String?,
        address: String,
        latitude: Double,
        longitude: Double,
        userLat: Double?,
        userLng: Double?,
        category: SpotCategory
    ): VibeSpotItem {
        val style = styleFor(category)
        val distance = if (userLat != null && userLng != null) {
            formatDistance(distanceMeters(userLat, userLng, latitude, longitude))
        } else {
            ""
        }
        return VibeSpotItem(
            id = id,
            name = name,
            address = address.ifBlank { city.orEmpty() },
            distance = distance,
            category = category,
            iconRes = style.first,
            backgroundRes = style.second,
            city = city,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun categoryFromAmenity(value: String?): SpotCategory {
        return when (value?.lowercase()) {
            "cafe", "coffee", "bakery" -> SpotCategory.CAFE
            "bar", "pub", "nightclub", "biergarten" -> SpotCategory.DRINKS
            "restaurant", "fast_food", "food_court" -> SpotCategory.RESTAURANT
            "park", "garden", "beach" -> SpotCategory.OUTDOORS
            "cinema", "movie_theatre" -> SpotCategory.CINEMA
            "museum", "theatre", "gallery", "arts_centre" -> SpotCategory.CULTURE
            else -> SpotCategory.ALL
        }
    }

    private fun styleFor(category: SpotCategory): Pair<Int, Int> {
        return when (category) {
            SpotCategory.CAFE -> R.drawable.ic_spot_coffee_brown to R.drawable.bg_squircle_cream
            SpotCategory.DRINKS -> R.drawable.ic_spot_coffee_brown to R.drawable.bg_squircle_cream
            SpotCategory.RESTAURANT -> R.drawable.ic_spot_cutlery_red to R.drawable.bg_squircle_pink
            SpotCategory.CINEMA -> R.drawable.ic_spot_cinema_gray to R.drawable.bg_squircle_gray
            SpotCategory.OUTDOORS -> R.drawable.ic_spot_cutlery_red to R.drawable.bg_squircle_pink
            SpotCategory.CULTURE -> R.drawable.ic_spot_cinema_gray to R.drawable.bg_squircle_gray
            SpotCategory.ALL -> R.drawable.ic_spot_cutlery_red to R.drawable.bg_squircle_pink
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0]
    }

    private fun formatDistance(meters: Float): String {
        return if (meters < 1000f) {
            "${meters.toInt()} m"
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000f)
        }
    }

    private val SpotCategory.searchQuery: String
        get() = when (this) {
            SpotCategory.CAFE -> "coffee"
            SpotCategory.DRINKS -> "bar"
            SpotCategory.RESTAURANT -> "restaurant"
            SpotCategory.OUTDOORS -> "park"
            SpotCategory.CINEMA -> "cinema"
            SpotCategory.CULTURE -> "museum"
            SpotCategory.ALL -> "cafe"
        }

    private val SpotCategory.overpassFilters: List<String>
        get() = when (this) {
            SpotCategory.CAFE -> listOf("[\"amenity\"=\"cafe\"]", "[\"shop\"=\"coffee\"]")
            SpotCategory.DRINKS -> listOf("[\"amenity\"=\"bar\"]", "[\"amenity\"=\"pub\"]")
            SpotCategory.RESTAURANT -> listOf("[\"amenity\"=\"restaurant\"]")
            SpotCategory.OUTDOORS -> listOf("[\"leisure\"=\"park\"]")
            SpotCategory.CINEMA -> listOf("[\"amenity\"=\"cinema\"]")
            SpotCategory.CULTURE -> listOf("[\"tourism\"=\"museum\"]", "[\"amenity\"=\"theatre\"]")
            SpotCategory.ALL -> listOf(
                "[\"amenity\"=\"cafe\"]",
                "[\"amenity\"=\"bar\"]",
                "[\"amenity\"=\"restaurant\"]",
                "[\"leisure\"=\"park\"]",
                "[\"amenity\"=\"cinema\"]",
                "[\"tourism\"=\"museum\"]"
            )
        }
}

data class CitySuggestion(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)
