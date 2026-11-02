package com.flirttime.feature.profile

object MoreAboutMeCatalog {

    data class Spec(
        val key: String,
        val label: String,
        val icon: String,
        val aliases: List<String>,
        val options: Array<String>
    )

    private val heightOptions = arrayOf(
        "4'10\" (147 cm)",
        "5'0\" (152 cm)",
        "5'2\" (157 cm)",
        "5'4\" (163 cm)",
        "5'6\" (168 cm)",
        "5'8\" (173 cm)",
        "5'10\" (178 cm)",
        "6'0\" (183 cm)",
        "6'2\" (188 cm)",
        "6'4\" (193 cm)",
        "6'6\" (198 cm)"
    )

    val specs: List<Spec> = listOf(
        Spec("height", "Height", "📏", listOf("height"), heightOptions),
        Spec("exercise", "Exercise", "🏋️", listOf("exercise"), arrayOf("Never", "Sometimes", "Regularly", "Active")),
        Spec(
            "education",
            "Education",
            "📖",
            listOf("education"),
            arrayOf("High School", "Bachelor's", "Master's", "PHD", "Others")
        ),
        Spec(
            "drinking",
            "Drinking",
            "🍷",
            listOf("drinking"),
            arrayOf("Non-Drinker", "Social Drinker", "Occasional Drinker", "Used to Drink, but Quit")
        ),
        Spec(
            "smoking",
            "Smoking",
            "🚬",
            listOf("smoking"),
            arrayOf("Yes", "No", "Occasionally", "Trying to quit")
        ),
        Spec(
            "looking-for",
            "Looking for",
            "💞",
            listOf("looking-for", "lookingFor", "looking_for"),
            arrayOf("Time Pass", "Something casual", "Relationship", "Marriage", "Not sure")
        ),
        Spec(
            "kids",
            "Kids",
            "👶",
            listOf("kids", "children"),
            arrayOf("Have kids", "Don't have kids", "Want some day", "Don't want kids", "Not sure")
        ),
        Spec(
            "horoscope",
            "Horoscope",
            "♈",
            listOf("horoscope", "zodiac"),
            arrayOf(
                "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
                "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
            )
        ),
        Spec("party", "Party", "🎉", listOf("party"), arrayOf("Never", "Rarely", "Moderate", "Often")),
        Spec(
            "religion",
            "Religion",
            "🛕",
            listOf("religion"),
            arrayOf(
                "Hindu", "Spiritual", "Muslim", "Christian", "Atheist", "Agnostic",
                "Buddhist", "Jewish", "Parsi", "Sikh", "Jain", "Other"
            )
        )
    )

    fun optionsFor(key: String): Array<String> {
        return specs.firstOrNull { spec ->
            spec.key.equals(key, ignoreCase = true) || spec.aliases.any { it.equals(key, ignoreCase = true) }
        }?.options ?: arrayOf("Yes", "No", "Prefer not to say")
    }

    fun emptyItems(): List<MoreAboutMeItemDto> {
        return specs.map { MoreAboutMeItemDto(it.key, it.label, it.icon, null) }
    }

    fun merge(data: MoreAboutMeResponseData): List<MoreAboutMeItemDto> {
        val fromItems = data.items.orEmpty().associateBy { it.key.lowercase() }
        return specs.map { spec ->
            val fromArray = spec.aliases.firstNotNullOfOrNull { alias ->
                fromItems[alias.lowercase()]?.value?.takeIf { it.isNotBlank() }
            }
            val fromField = when (spec.key) {
                "height" -> data.height
                "exercise" -> data.exercise
                "education" -> data.education
                "drinking" -> data.drinking
                "smoking" -> data.smoking
                "looking-for" -> data.lookingFor
                "kids" -> data.kids ?: data.children
                "horoscope" -> data.horoscope
                "party" -> data.party
                "religion" -> data.religion
                else -> null
            }?.takeIf { it.isNotBlank() }
            MoreAboutMeItemDto(spec.key, spec.label, spec.icon, fromArray ?: fromField)
        }
    }
}
