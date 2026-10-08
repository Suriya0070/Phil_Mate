package com.jsf.app.medication_solution.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jsf.app.medication_solution.MainActivity
import com.jsf.app.medication_solution.MedApp

class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("medName") ?: return
        val seniorId = intent.getStringExtra("seniorId") ?: return
        val triggerType = intent.getStringExtra("triggerType") ?: "MEDICATION"

        AutoStartManager.pendingTrigger = AutoStartManager.AlarmTrigger(
            medName = medName,
            triggerType = triggerType,
            seniorId = seniorId
        )

        showNotification(context, medName, triggerType)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("LAUNCH_VOICE_ASSISTANT", true)
            putExtra("medName", medName)
            putExtra("triggerType", triggerType)
        }
        context.startActivity(launchIntent)
    }

    private fun showNotification(context: Context, medName: String, triggerType: String) {
        val (title, text) = if (triggerType == "CHECK_IN") {
            "Check-In Time!" to "Your caregiver wants to check in with you."
        } else {
            "Medicine Time! 💊" to "Time to take $medName"
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(medName.hashCode(), notification)
    }
}
