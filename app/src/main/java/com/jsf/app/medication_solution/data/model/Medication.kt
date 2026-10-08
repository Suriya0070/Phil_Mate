package com.jsf.app.medication_solution.data.model

data class Medication(
    val id: String = "",
    val seniorId: String = "",
    val name: String = "",
    val purpose: String = "",
    val dosage: String = "",
    val pillColorHex: String = "#4CAF50",
    val pillEmoji: String = "💊",
    val scheduleTimes: List<String> = emptyList(),
    val instructions: String = "",
    val remainingPills: Int = 30,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
)
