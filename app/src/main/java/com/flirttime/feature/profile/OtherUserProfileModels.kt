package com.flirttime.feature.profile

import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.feature.main.matches.DiscoveryFiltersDto
import com.flirttime.feature.moments.MomentDto
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

fun extractStringFromJsonElement(element: JsonElement?): String? {
    if (element == null || element.isJsonNull) return null
    if (element.isJsonPrimitive) {
        val s = element.asString
        return if (s.isNullOrBlank() || s.equals("null", ignoreCase = true)) null else s
    }
    if (element.isJsonObject) {
        val obj = element.asJsonObject
        val url = obj.get("url")?.let { if (it.isJsonPrimitive) it.asString else null }
            ?: obj.get("thumbnailUrl")?.let { if (it.isJsonPrimitive) it.asString else null }
            ?: obj.get("imageUrl")?.let { if (it.isJsonPrimitive) it.asString else null }
            ?: obj.get("key")?.let { if (it.isJsonPrimitive) it.asString else null }
            ?: obj.get("imageKey")?.let { if (it.isJsonPrimitive) it.asString else null }
        return if (url.isNullOrBlank() || url.equals("null", ignoreCase = true)) null else url
    }
    return null
}

data class SearchPaginationDto(
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null
)

data class OtherUserVoiceIntroDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null,
    @SerializedName("playbackUrl") val playbackUrl: String? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("durationSeconds") val durationSeconds: Double? = null,
    @SerializedName("transcript") val transcript: String? = null
) {
    val effectiveUrl: String?
        get() {
            val raw = playbackUrl ?: audioUrl ?: url
            if (raw.isNullOrBlank()) return null
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "${MediaUrlHelper.S3_MEDIA_BASE_URL}${raw.removePrefix("/")}"
        }

    val effectiveDuration: Int
        get() = (durationSeconds ?: duration)?.toInt() ?: 0
}

data class UserSearchItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: JsonElement? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("coverPhoto") val coverPhoto: JsonElement? = null,
    @SerializedName("coverPhotoUrl") val coverPhotoUrl: String? = null,
    @SerializedName("coverPicture") val coverPicture: String? = null,
    @SerializedName("banner") val banner: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("location") val location: UserLocationDto? = null,
    @SerializedName("fullLocation") val fullLocation: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("interests") val interests: List<UserInterestDto>? = null,
    @SerializedName("sharedInterests") val sharedInterests: List<String>? = null,
    @SerializedName("commonInterestsCount") val commonInterestsCount: Int? = null,
    @SerializedName("profileMatchPercentage") val profileMatchPercentage: Int? = null,
    @SerializedName("momentCount") val momentCount: Int? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("moments") val moments: List<MomentDto>? = null,
    @SerializedName("voiceIntro") val voiceIntro: OtherUserVoiceIntroDto? = null,
    @SerializedName("moreAboutMeItems") val moreAboutMeItems: List<MoreAboutMeItemDto>? = null,
    @SerializedName("compatibility") val compatibility: UserCompatibilityDto? = null,
    @SerializedName("hasViewedQuickMoment") val hasViewedQuickMoment: Boolean? = null,
    @SerializedName("moreAboutMe") val moreAboutMe: MoreAboutMeResponseData? = null,
    @SerializedName("sexuality") val sexuality: String? = null,
    @SerializedName("height") val height: String? = null,
    @SerializedName("weight") val weight: String? = null,
    @SerializedName("smokingHabit") val smokingHabit: String? = null,
    @SerializedName("smoking") val smoking: String? = null,
    @SerializedName("drinkingHabit") val drinkingHabit: String? = null,
    @SerializedName("drinking") val drinking: String? = null,
    @SerializedName("eyeColor") val eyeColor: String? = null,
    @SerializedName("hairColor") val hairColor: String? = null,
    @SerializedName("space") val space: String? = null,
    @SerializedName("living") val living: String? = null,
    @SerializedName("religion") val religion: String? = null,
    @SerializedName("education") val education: String? = null,
    @SerializedName("relationshipStatus") val relationshipStatus: String? = null,
    @SerializedName("relationship") val relationship: String? = null,
    @SerializedName("children") val children: String? = null
) {
    val effectiveId: String
        get() = userId ?: id.orEmpty()

    val effectiveName: String
        get() = username ?: name.orEmpty()

    val effectivePhoto: String
        get() {
            val raw = profilePhotoUrl ?: extractStringFromJsonElement(profilePhoto) ?: avatar
            if (raw.isNullOrBlank()) return ""
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "${MediaUrlHelper.S3_MEDIA_BASE_URL}${raw.removePrefix("/")}"
        }

    val effectiveCover: String
        get() {
            val raw = coverPhotoUrl ?: extractStringFromJsonElement(coverPhoto) ?: coverPicture ?: banner
            if (raw.isNullOrBlank()) return ""
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "${MediaUrlHelper.S3_MEDIA_BASE_URL}${raw.removePrefix("/")}"
        }

    val effectiveBio: String
        get() = about ?: bio.orEmpty()

    val effectiveLocation: String
        get() {
            val dist = distanceText ?: distanceKm?.let { String.format("%.1f km away", it) }
            val loc = fullLocation ?: location?.label ?: location?.name ?: city ?: ""
            return if (dist != null && loc.isNotBlank()) "$loc · $dist" else loc.ifBlank { dist ?: "" }
        }

    fun getEffectiveMoreAboutMeItems(): List<MoreAboutMeItemDto> {
        if (!moreAboutMeItems.isNullOrEmpty()) return moreAboutMeItems

        val items = mutableListOf<MoreAboutMeItemDto>()
        val nested = moreAboutMe?.getEffectiveItems()

        fun findVal(directVal: String?, vararg keys: String): String? {
            if (!directVal.isNullOrBlank()) return directVal
            for (k in keys) {
                val found = nested?.firstOrNull { it.key.equals(k, ignoreCase = true) || it.label.equals(k, ignoreCase = true) }?.value
                if (!found.isNullOrBlank()) return found
            }
            return null
        }

        val sSex = findVal(sexuality, "sexuality")
        val sHeight = findVal(height, "height")
        val sWeight = findVal(weight, "weight")
        val sSmoke = findVal(smokingHabit ?: smoking, "smoking", "smokingHabit")
        val sDrink = findVal(drinkingHabit ?: drinking, "drinking", "drinkingHabit")
        val sEye = findVal(eyeColor, "eyeColor", "eyecolor")
        val sHair = findVal(hairColor, "hairColor", "haircolor")
        val sSpace = findVal(space ?: living, "space", "living")
        val sReligion = findVal(religion, "religion")
        val sEdu = findVal(education, "education")
        val sRel = findVal(relationshipStatus ?: relationship, "relationshipStatus", "relationship")
        val sChild = findVal(children, "children")

        if (!sSex.isNullOrBlank()) items.add(MoreAboutMeItemDto("sexuality", "Sexuality", "⚧", sSex))
        if (!sHeight.isNullOrBlank()) items.add(MoreAboutMeItemDto("height", "Height", "↕", sHeight))
        if (!sWeight.isNullOrBlank()) items.add(MoreAboutMeItemDto("weight", "Weight", "⚖️", sWeight))
        if (!sSmoke.isNullOrBlank()) items.add(MoreAboutMeItemDto("smoking", "Smoking Habit", "🚬", sSmoke))
        if (!sDrink.isNullOrBlank()) items.add(MoreAboutMeItemDto("drinking", "Drinking Habit", "🍷", sDrink))
        if (!sEye.isNullOrBlank()) items.add(MoreAboutMeItemDto("eyeColor", "Eye Color", "👁", sEye))
        if (!sHair.isNullOrBlank()) items.add(MoreAboutMeItemDto("hairColor", "Hair Color", "💇", sHair))
        if (!sSpace.isNullOrBlank()) items.add(MoreAboutMeItemDto("living", "Space", "🏠", sSpace))
        if (!sReligion.isNullOrBlank()) items.add(MoreAboutMeItemDto("religion", "Religion", "🙏", sReligion))
        if (!sEdu.isNullOrBlank()) items.add(MoreAboutMeItemDto("education", "Education", "🎓", sEdu))
        if (!sRel.isNullOrBlank()) items.add(MoreAboutMeItemDto("relationship", "Relationship Status", "👫", sRel))
        if (!sChild.isNullOrBlank()) items.add(MoreAboutMeItemDto("children", "Children", "👶", sChild))

        return items
    }
}

data class UserLocationDto(
    @SerializedName("city") val city: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("fullLocation") val fullLocation: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class UserInterestDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("interestId") val interestId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("emoji") val emoji: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
    @SerializedName("displayOrder") val displayOrder: Int? = null,
    @SerializedName("isCommon") var isCommon: Boolean? = null
) {
    val effectiveId: String
        get() = id ?: interestId ?: ""

    val effectiveEmoji: String
        get() = emoji ?: icon ?: "✨"
}

data class UserPhotoDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("imageKey") val imageKey: String? = null,
    @SerializedName("isPrimary") val isPrimary: Boolean? = null,
    @SerializedName("displayOrder") val displayOrder: Int? = null
) {
    val effectiveKey: String?
        get() = imageKey ?: key

    val effectiveUrl: String
        get() {
            val raw = imageUrl ?: url ?: thumbnailUrl ?: effectiveKey
            if (raw.isNullOrBlank()) return ""
            return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "${MediaUrlHelper.S3_MEDIA_BASE_URL}${raw.removePrefix("/")}"
        }
}

data class UserCompatibilityDto(
    @SerializedName("interestMatchPercentage") val interestMatchPercentage: Int? = null,
    @SerializedName("vibeScore") val vibeScore: Int? = null,
    @SerializedName("explanation") val explanation: String? = null
)

// 2. Full Other User Profile Response (Directly maps root fields from "data")
data class OtherUserProfileResponseData(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("location") val location: UserLocationDto? = null,
    @SerializedName("fullLocation") val fullLocation: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("distance") val distance: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("profilePhoto") val profilePhoto: JsonElement? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("profileThumbnail") val profileThumbnail: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("coverPhoto") val coverPhoto: JsonElement? = null,
    @SerializedName("coverPhotoUrl") val coverPhotoUrl: String? = null,
    @SerializedName("coverPicture") val coverPicture: String? = null,
    @SerializedName("banner") val banner: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("voiceIntro") val voiceIntro: OtherUserVoiceIntroDto? = null,
    @SerializedName("audioIntro") val audioIntro: AudioIntroDto? = null,
    @SerializedName("interests") val interests: List<UserInterestDto>? = null,
    @SerializedName("sharedInterests") val sharedInterests: JsonElement? = null,
    @SerializedName("commonInterestsCount") val commonInterestsCount: Int? = null,
    @SerializedName("profileMatchPercentage") val profileMatchPercentage: Int? = null,
    @SerializedName("vibeScore") val vibeScore: Int? = null,
    @SerializedName("vibeExplanation") val vibeExplanation: JsonElement? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("moments") val moments: List<MomentDto>? = null,
    @SerializedName("moreAboutMe") val moreAboutMeRaw: JsonElement? = null,
    @SerializedName("sexuality") val sexuality: String? = null,
    @SerializedName("height") val height: String? = null,
    @SerializedName("weight") val weight: String? = null,
    @SerializedName("smokingHabit") val smokingHabit: String? = null,
    @SerializedName("smoking") val smoking: String? = null,
    @SerializedName("drinkingHabit") val drinkingHabit: String? = null,
    @SerializedName("drinking") val drinking: String? = null,
    @SerializedName("eyeColor") val eyeColor: String? = null,
    @SerializedName("hairColor") val hairColor: String? = null,
    @SerializedName("space") val space: String? = null,
    @SerializedName("living") val living: String? = null,
    @SerializedName("religion") val religion: String? = null,
    @SerializedName("education") val education: String? = null,
    @SerializedName("relationshipStatus") val relationshipStatus: String? = null,
    @SerializedName("relationship") val relationship: String? = null,
    @SerializedName("children") val children: String? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("myAction") val myAction: String? = null,
    @SerializedName("isMatched") val isMatched: Boolean? = null,
    @SerializedName("canConnect") val canConnect: Boolean? = null,
    @SerializedName("canChat") val canChat: Boolean? = null,
    @SerializedName("conversationId") val conversationId: String? = null,
    // Nested wrappers if backend wraps in user or profile:
    @SerializedName("user") val user: OtherUserProfileResponseData? = null,
    @SerializedName("profile") val profile: OtherUserProfileResponseData? = null,
    @SerializedName("targetUser") val targetUserObj: OtherUserProfileResponseData? = null,
    @SerializedName("data") val data: OtherUserProfileResponseData? = null
) {
    val targetUser: UserSearchItemDto
        get() {
            val src = profile ?: user ?: targetUserObj ?: data ?: this
            val resolvedCover = extractStringFromJsonElement(src.coverPhoto)
                ?: src.coverPhotoUrl
                ?: src.coverPicture
                ?: src.banner

            val resolvedPhoto = extractStringFromJsonElement(src.profilePhoto)
                ?: src.profilePhotoUrl
                ?: src.profileThumbnail
                ?: src.avatar

            val resolvedVoice = src.voiceIntro ?: src.audioIntro?.let {
                OtherUserVoiceIntroDto(url = it.effectiveUrl, duration = it.duration?.toDouble())
            }

            // Extract shared interests strings
            val sharedStrings = mutableListOf<String>()
            src.sharedInterests?.let { elem ->
                if (elem.isJsonArray) {
                    elem.asJsonArray.forEach { item ->
                        if (item.isJsonPrimitive) {
                            val str = item.asString
                            if (!str.isNullOrBlank()) sharedStrings.add(str)
                        } else if (item.isJsonObject) {
                            val name = item.asJsonObject.get("name")?.asString
                            if (!name.isNullOrBlank()) sharedStrings.add(name)
                        }
                    }
                } else if (elem.isJsonPrimitive) {
                    val str = elem.asString
                    if (!str.isNullOrBlank()) sharedStrings.add(str)
                }
            }

            // If empty, also check vibeExplanation for sharedInterests
            if (sharedStrings.isEmpty() && src.vibeExplanation != null && src.vibeExplanation.isJsonObject) {
                val vibeObj = src.vibeExplanation.asJsonObject
                if (vibeObj.has("sharedInterests") && vibeObj.get("sharedInterests").isJsonArray) {
                    vibeObj.getAsJsonArray("sharedInterests").forEach { item ->
                        if (item.isJsonPrimitive) {
                            val str = item.asString
                            if (!str.isNullOrBlank()) sharedStrings.add(str)
                        }
                    }
                }
            }

            // Mark common interests
            val processedInterests = src.interests?.map { interest ->
                val isCommon = sharedStrings.any { it.equals(interest.name, ignoreCase = true) }
                if (isCommon) interest.copy(isCommon = true) else interest
            }

            val aboutItems = parseMoreAboutMe(src.moreAboutMeRaw, src)

            val resolvedCommonCount = src.commonInterestsCount
                ?: sharedStrings.size.takeIf { it > 0 }
                ?: processedInterests?.count { it.isCommon == true }

            return UserSearchItemDto(
                id = src.id ?: src.userId,
                userId = src.userId ?: src.id,
                name = src.name ?: src.fullName,
                username = src.username ?: src.name,
                fullName = src.fullName,
                age = src.age,
                gender = src.gender,
                profilePhotoUrl = resolvedPhoto,
                coverPhotoUrl = resolvedCover,
                isVerified = src.isVerified ?: src.isFaceVerified,
                isOnline = src.isOnline,
                city = src.city ?: src.location?.city,
                fullLocation = src.fullLocation ?: src.location?.fullLocation,
                distanceKm = src.distanceKm,
                distanceText = src.distanceText ?: src.distance,
                about = src.about ?: src.bio,
                bio = src.bio ?: src.about,
                interests = processedInterests,
                sharedInterests = sharedStrings,
                commonInterestsCount = resolvedCommonCount,
                profileMatchPercentage = src.profileMatchPercentage ?: src.vibeScore,
                photos = src.photos,
                moments = src.moments,
                voiceIntro = resolvedVoice,
                moreAboutMeItems = aboutItems,
                sexuality = src.sexuality,
                height = src.height,
                weight = src.weight,
                smokingHabit = src.smokingHabit ?: src.smoking,
                drinkingHabit = src.drinkingHabit ?: src.drinking,
                eyeColor = src.eyeColor,
                hairColor = src.hairColor,
                space = src.space ?: src.living,
                religion = src.religion,
                education = src.education,
                relationshipStatus = src.relationshipStatus ?: src.relationship,
                children = src.children
            )
        }

    private fun parseMoreAboutMe(element: JsonElement?, src: OtherUserProfileResponseData): List<MoreAboutMeItemDto> {
        val items = mutableListOf<MoreAboutMeItemDto>()

        if (element != null && element.isJsonObject) {
            val obj = element.asJsonObject
            // 1. If backend sends "items" array inside moreAboutMe
            if (obj.has("items") && obj.get("items").isJsonArray) {
                obj.getAsJsonArray("items").forEach { itemElem ->
                    if (itemElem.isJsonObject) {
                        val itemObj = itemElem.asJsonObject
                        val key = itemObj.get("key")?.asString ?: itemObj.get("alias")?.asString ?: return@forEach
                        val label = itemObj.get("label")?.asString ?: itemObj.get("title")?.asString ?: key
                        val icon = itemObj.get("icon")?.asString
                        val value = itemObj.get("value")?.let {
                            if (it.isJsonPrimitive) it.asString else null
                        }
                        if (key.isNotBlank() && !value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
                            items.add(MoreAboutMeItemDto(key = key, label = label, icon = icon, value = value))
                        }
                    }
                }
            }

            // 2. Add other direct keys in moreAboutMe object
            obj.entrySet().forEach { (k, v) ->
                if (k != "items" && !v.isJsonNull && items.none { it.key.equals(k, ignoreCase = true) }) {
                    val vStr = if (v.isJsonPrimitive) v.asString else null
                    if (!vStr.isNullOrBlank() && !vStr.equals("null", ignoreCase = true)) {
                        items.add(MoreAboutMeItemDto(key = k, label = k, value = vStr))
                    }
                }
            }
        } else if (element != null && element.isJsonArray) {
            element.asJsonArray.forEach { elem ->
                if (elem.isJsonObject) {
                    val itemObj = elem.asJsonObject
                    val key = itemObj.get("key")?.asString ?: itemObj.get("alias")?.asString ?: return@forEach
                    val label = itemObj.get("label")?.asString ?: itemObj.get("title")?.asString ?: key
                    val icon = itemObj.get("icon")?.asString
                    val value = itemObj.get("value")?.let {
                        if (it.isJsonPrimitive) it.asString else null
                    }
                    if (key.isNotBlank() && !value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
                        items.add(MoreAboutMeItemDto(key = key, label = label, icon = icon, value = value))
                    }
                }
            }
        }

        // 3. Fallback to top-level fields on profile response
        fun addIfMissing(k: String, v: String?) {
            if (!v.isNullOrBlank() && !v.equals("null", ignoreCase = true) && items.none { it.key.equals(k, ignoreCase = true) }) {
                items.add(MoreAboutMeItemDto(key = k, label = k, value = v))
            }
        }

        addIfMissing("sexuality", src.sexuality)
        addIfMissing("height", src.height)
        addIfMissing("weight", src.weight)
        addIfMissing("smoking", src.smokingHabit ?: src.smoking)
        addIfMissing("drinking", src.drinkingHabit ?: src.drinking)
        addIfMissing("eyeColor", src.eyeColor)
        addIfMissing("hairColor", src.hairColor)
        addIfMissing("living", src.space ?: src.living)
        addIfMissing("religion", src.religion)
        addIfMissing("education", src.education)
        addIfMissing("relationship", src.relationshipStatus ?: src.relationship)
        addIfMissing("children", src.children)

        // Preferred iOS order:
        val preferredOrder = listOf(
            "sexuality",
            "height",
            "weight",
            "smoking",
            "drinking",
            "eyecolor",
            "haircolor",
            "living",
            "space",
            "religion",
            "education",
            "relationship",
            "children"
        )
        fun getOrder(k: String): Int {
            val clean = k.lowercase().replace("habit", "").replace("status", "").trim()
            val idx = preferredOrder.indexOf(clean)
            return if (idx >= 0) idx else 100
        }

        return items.sortedBy { getOrder(it.key) }
    }
}

// 3. Discovery Action Request / Response
data class DiscoveryActionRequest(
    @SerializedName("targetUserId") val targetUserId: String,
    @SerializedName("action") val action: String // "like", "nope", "super_like"
)

data class DiscoveryActionDailyQuotaDto(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("used") val used: Int? = null,
    @SerializedName("remaining") val remaining: Int? = null,
    @SerializedName("resetsAt") val resetsAt: String? = null
)

data class DiscoveryActionResponseData(
    @SerializedName("action") val action: String? = null,
    @SerializedName("targetUserId") val targetUserId: String? = null,
    @SerializedName("isMatched") val isMatched: Boolean? = null,
    @SerializedName("matchId") val matchId: String? = null,
    @SerializedName("coinsDeducted") val coinsDeducted: Int? = null,
    @SerializedName("remainingCoins") val remainingCoins: Int? = null,
    @SerializedName("dailyQuota") val dailyQuota: DiscoveryActionDailyQuotaDto? = null,
    @SerializedName("message") val message: String? = null
)

// 4. Discovery People Response
data class DiscoveryPeopleResponseData(
    @SerializedName("filter") val filter: DiscoveryFiltersDto? = null,
    @SerializedName("users") val users: List<DiscoveryUserDto>? = null,
    @SerializedName("profiles") val profiles: List<DiscoveryUserDto>? = null,
    @SerializedName("data") val data: List<DiscoveryUserDto>? = null,
    @SerializedName("items") val items: List<DiscoveryUserDto>? = null,
    @SerializedName("pagination") val pagination: SearchPaginationDto? = null
) {
    fun getEffectiveUsers(): List<DiscoveryUserDto> = users ?: profiles ?: data ?: items ?: emptyList()
}

data class DiscoveryUserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("lookingFor") val lookingFor: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: JsonElement? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("primaryPhotoUrl") val primaryPhotoUrl: String? = null,
    @SerializedName("coverPhoto") val coverPhoto: JsonElement? = null,
    @SerializedName("coverPhotoUrl") val coverPhotoUrl: String? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("vibeScore") val vibeScore: Int? = null,
    @SerializedName("sharedInterests") val sharedInterests: List<String>? = null,
    @SerializedName("interestTags") val interestTags: List<String>? = null,
    @SerializedName("momentCount") val momentCount: Int? = null,
    @SerializedName("totalMoments") val totalMoments: Int? = null,
    @SerializedName("compatibility") val compatibility: UserCompatibilityDto? = null,
    @SerializedName("hasViewedQuickMoment") val hasViewedQuickMoment: Boolean? = null
) {
    val effectiveId: String
        get() = userId ?: id.orEmpty()

    val effectiveName: String
        get() = name ?: username.orEmpty()

    val effectivePhoto: String
        get() = primaryPhotoUrl ?: profilePhotoUrl ?: extractStringFromJsonElement(profilePhoto) ?: photos?.firstOrNull()?.let { it.url ?: it.effectiveKey } ?: ""

    val effectiveCover: String
        get() = coverPhotoUrl ?: extractStringFromJsonElement(coverPhoto) ?: ""
}
