package com.flirttime.feature.main.matches.interest

import android.graphics.Color

object MockInterestsProvider {

    fun getInterests(): List<InterestItem> {
        return listOf(
            InterestItem(
                id = "276dfd27-a6a7-4606-a2da-cebec7d1b2bb",
                name = "Cooking",
                category = "Lifestyle",
                emoji = "🍳",
                iconBgColor = Color.parseColor("#FEF3C7"),
                isSelected = true
            ),
            InterestItem(
                id = "67201932-7e93-41c1-8ae9-fb98aa32762b",
                name = "Travelling",
                category = "Adventure",
                emoji = "✈️",
                iconBgColor = Color.parseColor("#E0EDFF"),
                isSelected = false
            ),
            InterestItem(
                id = "37c4d965-167e-4647-a5f8-438ea8f7d863",
                name = "Fitness",
                category = "Health",
                emoji = "💪",
                iconBgColor = Color.parseColor("#FFEAD7"),
                isSelected = false
            ),
            InterestItem(
                id = "a32abc73-fa05-42c4-974c-6dbfb71e109e",
                name = "Pets",
                category = "Lifestyle",
                emoji = "🐶",
                iconBgColor = Color.parseColor("#FCE7F3"),
                isSelected = false
            ),
            InterestItem(
                id = "0c4e0b2a-9995-4a3a-9072-ac023c860b0b",
                name = "Music",
                category = "Entertainment",
                emoji = "🎵",
                iconBgColor = Color.parseColor("#EFEAFF"),
                isSelected = false
            ),
            InterestItem(
                id = "art",
                name = "Art",
                category = "Creativity",
                emoji = "🎨",
                iconBgColor = Color.parseColor("#FFEAD7"),
                isSelected = false
            )
        )
    }
}
