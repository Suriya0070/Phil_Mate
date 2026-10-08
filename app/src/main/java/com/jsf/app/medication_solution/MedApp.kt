package com.jsf.app.medication_solution

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class MedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseFirestore.getInstance().firestoreSettings =
            FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDER,
                    "Medication Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Reminders to take your medicine" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERT,
                    "Caregiver Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts for caregivers about missed doses" }
            )
        }
    }

    companion object {
        const val CHANNEL_REMINDER = "med_reminder"
        const val CHANNEL_ALERT = "med_alert"
    }
}
