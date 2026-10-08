package com.jsf.app.medication_solution.ui.theme

import androidx.compose.ui.graphics.Color

object MedPalette {
    val colors = listOf(
        Color(0xFFE53935), // 0 Red
        Color(0xFF1E88E5), // 1 Blue
        Color(0xFF43A047), // 2 Green
        Color(0xFFFF8F00), // 3 Orange
        Color(0xFF8E24AA), // 4 Purple
        Color(0xFF00ACC1), // 5 Teal
        Color(0xFFE91E63), // 6 Pink
        Color(0xFF6D4C41), // 7 Brown
    )
    val colorNames = listOf("Red", "Blue", "Green", "Orange", "Purple", "Teal", "Pink", "Brown")

    fun colorForMedication(pillColorHex: String, colorIndex: Int): Color {
        if (colorIndex in 0..7) return colors[colorIndex]
        return try { Color(android.graphics.Color.parseColor(pillColorHex)) } catch (_: Exception) { colors[0] }
    }

    fun contrastTextColor(bg: Color): Color {
        val lum = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
        return if (lum > 0.55f) Color(0xFF212121) else Color.White
    }
}
