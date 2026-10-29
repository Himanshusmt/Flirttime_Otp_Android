package com.flirttime.feature.main.matches.interest

data class InterestItem(
    val id: String,
    val name: String,
    val category: String,
    val emoji: String,
    val iconBgColor: Int,
    var isSelected: Boolean = false
)
