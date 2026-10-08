package com.jsf.app.medication_solution.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jsf.app.medication_solution.data.model.Medication
import java.util.Calendar
import java.util.Date

object AlarmScheduler {
    private const val ACTION_MED_ALARM = "com.jsf.app.medication_solution.MED_ALARM"

    fun scheduleMedicationAlarms(context: Context, medications: List<Medication>, seniorId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val cal = Calendar.getInstance()

        for (med in medications) {
            for (timeStr in med.scheduleTimes) {
                val parts = timeStr.split(":")
                if (parts.size < 2) continue
                val hour = parts[0].trim().toIntOrNull() ?: continue
                val minute = parts[1].trim().toIntOrNull() ?: 0

                cal.apply {
                    time = Date()
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }

                val intent = Intent(context, MedAlarmReceiver::class.java).apply {
                    action = ACTION_MED_ALARM
                    putExtra("medName", med.name)
                    putExtra("seniorId", seniorId)
                    putExtra("triggerType", "MEDICATION")
                }

                val requestCode = (seniorId + med.id + timeStr).hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
        }
    }

    fun scheduleCheckIn(context: Context, seniorId: String, intervalMinutes: Int) {
        val triggerAt = System.currentTimeMillis() + intervalMinutes * 60_000L
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val intent = Intent(context, MedAlarmReceiver::class.java).apply {
            action = ACTION_MED_ALARM
            putExtra("medName", "Check-In")
            putExtra("seniorId", seniorId)
            putExtra("triggerType", "CHECK_IN")
        }

        val requestCode = ("checkin_$seniorId").hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }
}
