package com.flirttime.feature.moments.location

import com.flirttime.R

object MockVibeSpotsProvider {

    fun getCategories(): List<VibeCategory> {
        return listOf(
            VibeCategory("cat_coffee", "☕", "Coffee", SpotCategory.CAFE),
            VibeCategory("cat_drinks", "🍷", "Drinks", SpotCategory.DRINKS),
            VibeCategory("cat_dinner", "🍴", "Dinner", SpotCategory.RESTAURANT),
            VibeCategory("cat_outdoors", "🍃", "Outdoors", SpotCategory.OUTDOORS)
        )
    }

    fun getMockSpots(): List<VibeSpotItem> {
        return listOf(
            VibeSpotItem(
                id = "spot_1",
                name = "Chotu Chinese Center",
                address = "Badi Bhamori, Indore",
                distance = "50 ft",
                category = SpotCategory.RESTAURANT,
                iconRes = R.drawable.ic_spot_cutlery_red,
                backgroundRes = R.drawable.bg_squircle_pink
            ),
            VibeSpotItem(
                id = "spot_2",
                name = "Chai Tapri",
                address = "Indore",
                distance = "90 ft",
                category = SpotCategory.CAFE,
                iconRes = R.drawable.ic_spot_cutlery_red,
                backgroundRes = R.drawable.bg_squircle_pink
            ),
            VibeSpotItem(
                id = "spot_3",
                name = "SK Bakers",
                address = "Badi Bhamori, Indore",
                distance = "150 ft",
                category = SpotCategory.CAFE,
                iconRes = R.drawable.ic_spot_coffee_brown,
                backgroundRes = R.drawable.bg_squircle_cream
            ),
            VibeSpotItem(
                id = "spot_4",
                name = "RK Club Cafe Bar",
                address = "Badi Bhamori, Indore",
                distance = "200 ft",
                category = SpotCategory.DRINKS,
                iconRes = R.drawable.ic_spot_coffee_brown,
                backgroundRes = R.drawable.bg_squircle_cream
            ),
            VibeSpotItem(
                id = "spot_5",
                name = "Manmandir Multiplex",
                address = "Badi Bhamori, Indore",
                distance = "200 ft",
                category = SpotCategory.CINEMA,
                iconRes = R.drawable.ic_spot_cinema_gray,
                backgroundRes = R.drawable.bg_squircle_gray
            ),
            VibeSpotItem(
                id = "spot_6",
                name = "TCI Foods",
                address = "Badi Bhamori, Indore",
                distance = "200 ft",
                category = SpotCategory.RESTAURANT,
                iconRes = R.drawable.ic_spot_cutlery_red,
                backgroundRes = R.drawable.bg_squircle_pink
            ),
            VibeSpotItem(
                id = "spot_7",
                name = "Time out Cinemas",
                address = "Indore",
                distance = "200 ft",
                category = SpotCategory.CINEMA,
                iconRes = R.drawable.ic_spot_cinema_gray,
                backgroundRes = R.drawable.bg_squircle_gray
            ),
            VibeSpotItem(
                id = "spot_8",
                name = "M24 Restaurant & Cafe",
                address = "Indore",
                distance = "200 ft",
                category = SpotCategory.RESTAURANT,
                iconRes = R.drawable.ic_spot_cutlery_red,
                backgroundRes = R.drawable.bg_squircle_pink
            )
        )
    }
}
