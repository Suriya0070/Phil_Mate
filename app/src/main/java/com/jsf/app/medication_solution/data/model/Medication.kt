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
    val scheduleDays: List<Int> = emptyList(), // 0=Mon..6=Sun; empty means every day
    val instructions: String = "",
    val remainingPills: Int = 30,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val shape: String = "circle",   // circle, oval, capsule, square
    val imagePath: String = "",      // Firebase Storage path (set by family)
    val colorIndex: Int = -1         // -1=use pillColorHex; 0-7=palette index
)
