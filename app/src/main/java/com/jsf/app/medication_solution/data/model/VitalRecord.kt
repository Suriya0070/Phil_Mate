package com.jsf.app.medication_solution.data.model

data class VitalRecord(
    val id: String = "",
    val seniorId: String = "",
    val systolic: Int = 120,
    val diastolic: Int = 80,
    val heartRate: Int = 72,
    val spO2: Int = 98,
    val timestamp: Long = System.currentTimeMillis()
)
