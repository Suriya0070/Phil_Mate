package com.jsf.app.medication_solution.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.jsf.app.medication_solution.MedApp

class MedVerifyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("medName") ?: return
        val phone = context.getSharedPreferences("medicare_prefs", Context.MODE_PRIVATE)
            .getString("family_phone", "") ?: ""
        if (phone.isBlank()) return

        showVerifyNotification(context, medName)

        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    private fun showVerifyNotification(context: Context, medName: String) {
        val notification = NotificationCompat.Builder(context, MedApp.CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Verification Call 📞")
            .setContentText("Calling to verify: was $medName taken?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify("verify_$medName".hashCode(), notification)
    }
}
