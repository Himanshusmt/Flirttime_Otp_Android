package com.flirttime.feature.moments.gift

data class GiftItem(
    val id: String,
    val name: String,
    val coinPrice: Int,
    val emoji: String,
    val giftCode: String = id.replace("gift_", ""),
    var isSelected: Boolean = false
)
