package com.jsf.app.medication_solution.data.model

data class DoseRecord(
    val id: String = "",
    val medicationId: String = "",
    val medicationName: String = "",
    val medicationPurpose: String = "",
    val seniorId: String = "",
    val scheduledTime: Long = 0L,
    val takenAt: Long = 0L,
    val status: String = DoseStatus.PENDING.name,
    val date: String = "",
    val moodAfter: String = ""
) {
    fun doseStatus(): DoseStatus = try { DoseStatus.valueOf(status) } catch (_: Exception) { DoseStatus.PENDING }
    fun isTaken() = doseStatus() == DoseStatus.TAKEN
    fun isMissed() = doseStatus() == DoseStatus.MISSED
    fun isPending() = doseStatus() == DoseStatus.PENDING
    fun isOverdue() = isPending() && System.currentTimeMillis() - scheduledTime > 30 * 60 * 1000L
}

enum class DoseStatus { PENDING, TAKEN, MISSED, SKIPPED }
