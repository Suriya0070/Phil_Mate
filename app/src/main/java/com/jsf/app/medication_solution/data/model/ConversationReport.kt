package com.jsf.app.medication_solution.data.model

data class ConversationReport(
    val id: String = "",
    val seniorId: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val turns: Int = 0,
    val dominantEmotion: String = "NEUTRAL",
    val emotionCounts: Map<String, Int> = emptyMap(),
    val triggerType: String = "MANUAL",
    val summary: String = ""
)
