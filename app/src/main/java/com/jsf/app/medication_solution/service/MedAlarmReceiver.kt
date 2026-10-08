package com.jsf.app.medication_solution.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jsf.app.medication_solution.MainActivity
import com.jsf.app.medication_solution.MedApp

class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("medName") ?: return
        val seniorId = intent.getStringExtra("seniorId") ?: return
        val triggerType = intent.getStringExtra("triggerType") ?: "MEDICATION"

        AutoStartManager.pendingTrigger = AutoStartManager.AlarmTrigger(
            medName = medName, triggerType = triggerType, seniorId = seniorId
        )

        showNotification(context, medName, triggerType)

        if (triggerType == "MEDICATION") {
            callFamilyNumber(context)
            scheduleVerificationCall(context, medName, seniorId)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("LAUNCH_VOICE_ASSISTANT", true)
            putExtra("medName", medName)
            putExtra("triggerType", triggerType)
        }
        context.startActivity(launchIntent)
    }

    private fun callFamilyNumber(context: Context) {
        val phone = context.getSharedPreferences("medicare_prefs", Context.MODE_PRIVATE)
            .getString("family_phone", "") ?: ""
        if (phone.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    private fun scheduleVerificationCall(context: Context, medName: String, seniorId: String) {
        val verifyIntent = Intent(context, MedVerifyReceiver::class.java).apply {
            putExtra("medName", medName)
            putExtra("seniorId", seniorId)
        }
        val requestCode = "$medName-verify".hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, verifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val verifyAt = System.currentTimeMillis() + 5 * 60 * 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, verifyAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, verifyAt, pendingIntent)
        }
    }

    private fun showNotification(context: Context, medName: String, triggerType: String) {
        val (title, text) = if (triggerType == "CHECK_IN") {
            "Check-In Time!" to "Your caregiver wants to check in with you."
        } else {
            "Medicine Time! 💊" to "Time to take $medName — calling your family now..."
        }
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("LAUNCH_VOICE_ASSISTANT", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, medName.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MedApp.CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(medName.hashCode(), notification)
    }
}
