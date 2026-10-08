package com.jsf.app.medication_solution.data.model

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.SENIOR.name,
    val phone: String = "",
    val linkedSeniorId: String = "",
    val linkedCaregiverIds: List<String> = emptyList(),
    val lastActiveAt: Long = 0L,
    val engagementEnabled: Boolean = true,
    val isMonitored: Boolean = false,
    val checkInIntervalMinutes: Int = 0
) {
    fun userRole(): UserRole = try { UserRole.valueOf(role) } catch (_: Exception) { UserRole.SENIOR }
}

enum class UserRole { SENIOR, CAREGIVER }
