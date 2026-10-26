package com.flirttime.feature.main.presentation.matches.interest

object MockInterestPeopleProvider {

    private val allPeople = listOf(
        InterestPersonItem(
            id = "p1",
            name = "dummy_yash,...",
            city = "Indore",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&fit=crop&crop=faces",
            isVerified = true,
            interestId = "art"
        ),
        InterestPersonItem(
            id = "p2",
            name = "dummy_dhru...",
            city = "Delhi",
            imageUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&fit=crop&crop=faces",
            isVerified = true,
            interestId = "art"
        ),
        InterestPersonItem(
            id = "p3",
            name = "dummy_arya...",
            city = "Mumbai",
            imageUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=600&fit=crop&crop=faces",
            isVerified = true,
            interestId = "art"
        ),
        InterestPersonItem(
            id = "p4",
            name = "dummy_kabir",
            city = "Pune",
            imageUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=600&fit=crop&crop=faces",
            isVerified = true,
            interestId = "art"
        ),
        InterestPersonItem(
            id = "p5",
            name = "dummy_rohit",
            city = "Jaipur",
            imageUrl = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=600&fit=crop&crop=faces",
            isVerified = true,
            interestId = "art"
        ),
        InterestPersonItem(
            id = "p6",
            name = "dummy_aditya",
            city = "Bengaluru",
            imageUrl = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=600&fit=crop&crop=faces",
            isVerified = true,
            interestId = "art"
        )
    )

    fun getPeopleForInterest(interestId: String): List<InterestPersonItem> {
        // Return full curated list for demo; if specific interest, can return full or tailored list
        return allPeople
    }
}
