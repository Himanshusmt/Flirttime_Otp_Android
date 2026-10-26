package com.flirttime.feature.main.presentation.matches.interest

import android.graphics.Color

object MockInterestsProvider {

    fun getInterests(): List<InterestItem> {
        return listOf(
            InterestItem(
                id = "art",
                name = "Art",
                category = "Creativity",
                emoji = "🎨",
                iconBgColor = Color.parseColor("#FFEAD7"),
                isSelected = true
            ),
            InterestItem(
                id = "cinema",
                name = "Cinema",
                category = "Entertainment",
                emoji = "🎬",
                iconBgColor = Color.parseColor("#EFEAFF"),
                isSelected = false
            ),
            InterestItem(
                id = "cooking",
                name = "Cooking",
                category = "Lifestyle",
                emoji = "🍳",
                iconBgColor = Color.parseColor("#FEF3C7"),
                isSelected = false
            ),
            InterestItem(
                id = "dance",
                name = "Dance",
                category = "Arts",
                emoji = "💃",
                iconBgColor = Color.parseColor("#FCE7F3"),
                isSelected = false
            ),
            InterestItem(
                id = "drawing",
                name = "Drawing",
                category = "Creativity",
                emoji = "🎨",
                iconBgColor = Color.parseColor("#E0EDFF"),
                isSelected = false
            ),
            InterestItem(
                id = "drinks",
                name = "Drinks",
                category = "Social",
                emoji = "🍹",
                iconBgColor = Color.parseColor("#F3E8FF"),
                isSelected = false
            )
        )
    }
}
