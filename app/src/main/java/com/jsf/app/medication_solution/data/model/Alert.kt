package com.jsf.app.medication_solution.data.model

data class Alert(
    val id: String = "",
    val seniorId: String = "",
    val seniorName: String = "",
    val type: String = AlertType.MISSED_DOSE.name,
    val message: String = "",
    val severity: String = AlertSeverity.MEDIUM.name,
    val timestamp: Long = 0L,
    val isResolved: Boolean = false,
    val medicationName: String = ""
) {
    fun alertType(): AlertType = try { AlertType.valueOf(type) } catch (_: Exception) { AlertType.MISSED_DOSE }
    fun alertSeverity(): AlertSeverity = try { AlertSeverity.valueOf(severity) } catch (_: Exception) { AlertSeverity.MEDIUM }
}

enum class AlertType(val emoji: String, val label: String) {
    MISSED_DOSE("⚠️", "Missed Dose"),
    INACTIVITY("😴", "Inactive"),
    MOOD_LOW("😟", "Low Mood"),
    DOSE_CONFIRMED("✅", "Dose Taken"),
    REFILL_NEEDED("💊", "Refill Needed"),
    DOUBLE_DOSE_PREVENTED("🛡️", "Double Dose Prevented")
}

enum class AlertSeverity { LOW, MEDIUM, HIGH, CRITICAL }
