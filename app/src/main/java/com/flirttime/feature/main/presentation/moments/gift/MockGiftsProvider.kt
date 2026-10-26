package com.flirttime.feature.main.presentation.moments.gift

object MockGiftsProvider {

    fun getAvailableGifts(): List<GiftItem> = listOf(
        GiftItem(
            id = "gift_rose",
            name = "Rose",
            coinPrice = 1,
            emoji = "🌹"
        ),
        GiftItem(
            id = "gift_heart",
            name = "Heart",
            coinPrice = 5,
            emoji = "💖"
        ),
        GiftItem(
            id = "gift_chocolate",
            name = "Chocolate",
            coinPrice = 10,
            emoji = "🍫"
        ),
        GiftItem(
            id = "gift_teddy",
            name = "Teddy",
            coinPrice = 20,
            emoji = "🧸"
        ),
        GiftItem(
            id = "gift_bouquet",
            name = "Bouquet",
            coinPrice = 50,
            emoji = "💐"
        ),
        GiftItem(
            id = "gift_ring",
            name = "Ring",
            coinPrice = 100,
            emoji = "💍"
        ),
        GiftItem(
            id = "gift_crown",
            name = "Crown",
            coinPrice = 200,
            emoji = "👑"
        ),
        GiftItem(
            id = "gift_sports_car",
            name = "Sports car",
            coinPrice = 500,
            emoji = "🏎️"
        )
    )
}
