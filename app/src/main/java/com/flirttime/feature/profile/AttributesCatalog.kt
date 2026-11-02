package com.flirttime.feature.profile

import android.content.Context
import com.flirttime.core.common.MediaUrlHelper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class AttributeCatalogModel(
    @SerializedName("data") val data: List<AttributeItemDto>? = null
)

data class AttributeItemDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("alias") val alias: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("order_num") val orderNum: Int? = null
)

object AttributesCatalog {
    private var catalog: List<AttributeItemDto>? = null

    fun getAttributes(context: Context): List<AttributeItemDto> {
        catalog?.let { return it }
        return try {
            val json = context.assets.open("Attributes.json").bufferedReader().use { it.readText() }
            val model = Gson().fromJson(json, AttributeCatalogModel::class.java)
            val list = model.data.orEmpty().sortedBy { it.orderNum ?: Int.MAX_VALUE }
            catalog = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getIconUrl(context: Context, keyOrAlias: String): String? {
        val list = getAttributes(context)
        val cleanKey = keyOrAlias.lowercase().trim()
        val item = list.firstOrNull {
            it.alias?.lowercase() == cleanKey ||
            it.title?.lowercase() == cleanKey ||
            (cleanKey == "living" && it.alias?.lowercase() == "living") ||
            (cleanKey == "space" && it.alias?.lowercase() == "living") ||
            (cleanKey == "smokinghabit" && it.alias?.lowercase() == "smoking") ||
            (cleanKey == "drinkinghabit" && it.alias?.lowercase() == "drinking") ||
            (cleanKey == "relationshipstatus" && it.alias?.lowercase() == "relationship")
        }
        val photo = item?.photo ?: return null
        return if (photo.startsWith("http://") || photo.startsWith("https://")) photo else "${MediaUrlHelper.S3_MEDIA_BASE_URL}${photo.removePrefix("/")}"
    }

    fun getTitle(context: Context, keyOrAlias: String): String {
        val cleanKey = keyOrAlias.lowercase().trim()
        val list = getAttributes(context)
        val item = list.firstOrNull {
            it.alias?.lowercase() == cleanKey ||
            it.title?.lowercase() == cleanKey ||
            (cleanKey == "space" && it.alias?.lowercase() == "living") ||
            (cleanKey == "smokinghabit" && it.alias?.lowercase() == "smoking") ||
            (cleanKey == "drinkinghabit" && it.alias?.lowercase() == "drinking") ||
            (cleanKey == "relationshipstatus" && it.alias?.lowercase() == "relationship")
        }

        return when (cleanKey) {
            "smoking", "smokinghabit" -> "Smoking Habit"
            "drinking", "drinkinghabit" -> "Drinking Habit"
            "eyecolor" -> "Eye Color"
            "haircolor" -> "Hair Color"
            "space", "living" -> "Space"
            "relationship", "relationshipstatus" -> "Relationship Status"
            else -> item?.title ?: keyOrAlias.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}

