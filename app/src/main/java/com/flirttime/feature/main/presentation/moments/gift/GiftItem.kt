package com.flirttime.feature.main.presentation.moments.gift

data class GiftItem(
    val id: String,
    val name: String,
    val coinPrice: Int,
    val emoji: String,
    var isSelected: Boolean = false
)
