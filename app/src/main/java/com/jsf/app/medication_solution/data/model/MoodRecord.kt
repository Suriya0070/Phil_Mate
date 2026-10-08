package com.jsf.app.medication_solution.data.model

data class MoodRecord(
    val id: String = "",
    val seniorId: String = "",
    val mood: String = MoodLevel.GOOD.name,
    val timestamp: Long = 0L,
    val afterDoseId: String = ""
) {
    fun moodLevel(): MoodLevel = try { MoodLevel.valueOf(mood) } catch (_: Exception) { MoodLevel.GOOD }
}

enum class MoodLevel(val emoji: String, val label: String, val score: Int) {
    GREAT("😄", "Great", 5),
    GOOD("😊", "Good", 4),
    OKAY("😐", "Okay", 3),
    NOT_GOOD("😟", "Not Good", 2),
    BAD("😢", "Bad", 1)
}
