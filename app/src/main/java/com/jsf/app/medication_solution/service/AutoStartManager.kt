package com.jsf.app.medication_solution.service

object AutoStartManager {
    var pendingTrigger: AlarmTrigger? = null

    data class AlarmTrigger(
        val medName: String,
        val triggerType: String,
        val seniorId: String
    )
}
