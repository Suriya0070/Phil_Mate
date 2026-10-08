package com.jsf.app.medication_solution.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jsf.app.medication_solution.data.model.Alert
import com.jsf.app.medication_solution.data.model.AlertSeverity
import com.jsf.app.medication_solution.data.model.AlertType
import com.jsf.app.medication_solution.data.model.ConversationReport
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodLevel
import com.jsf.app.medication_solution.data.model.MoodRecord
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MedicationRepository {
    private val db = FirebaseFirestore.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun getMedicationsForSenior(seniorId: String): Flow<List<Medication>> = callbackFlow {
        val listener = db.collection("medications")
            .whereEqualTo("seniorId", seniorId)
            .whereEqualTo("active", true)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(Medication::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getTodayDoseRecords(seniorId: String): Flow<List<DoseRecord>> = callbackFlow {
        val today = dateFmt.format(Date())
        val listener = db.collection("doseRecords")
            .whereEqualTo("seniorId", seniorId)
            .whereEqualTo("date", today)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(DoseRecord::class.java)?.copy(id = it.id)
                }?.sortedBy { it.scheduledTime } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getAlertsForSenior(seniorId: String): Flow<List<Alert>> = callbackFlow {
        val listener = db.collection("alerts")
            .whereEqualTo("seniorId", seniorId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(Alert::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getMoodHistory(seniorId: String): Flow<List<MoodRecord>> = callbackFlow {
        val listener = db.collection("moodRecords")
            .whereEqualTo("seniorId", seniorId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(MoodRecord::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun createDoseRecordsForToday(seniorId: String, medications: List<Medication>) {
        val today = dateFmt.format(Date())
        val batch = db.batch()
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
                val recordId = "${seniorId}_${med.id}_${today}_${timeStr.replace(":", "").replace(" ", "")}"
                val record = DoseRecord(
                    id = recordId,
                    medicationId = med.id,
                    medicationName = med.name,
                    medicationPurpose = med.purpose,
                    seniorId = seniorId,
                    scheduledTime = cal.timeInMillis,
                    status = DoseStatus.PENDING.name,
                    date = today
                )
                batch.set(db.collection("doseRecords").document(recordId), record)
            }
        }
        runCatching { batch.commit().await() }
    }

    suspend fun confirmDose(record: DoseRecord, mood: MoodLevel?): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        db.collection("doseRecords").document(record.id).update(
            mapOf(
                "status" to DoseStatus.TAKEN.name,
                "takenAt" to now,
                "moodAfter" to (mood?.name ?: "")
            )
        ).await()

        if (mood != null) {
            val moodId = UUID.randomUUID().toString()
            db.collection("moodRecords").document(moodId).set(
                MoodRecord(
                    id = moodId,
                    seniorId = record.seniorId,
                    mood = mood.name,
                    timestamp = now,
                    afterDoseId = record.id
                )
            ).await()

            if (mood.score <= 2) {
                createAlert(
                    seniorId = record.seniorId,
                    type = AlertType.MOOD_LOW,
                    message = "${record.medicationName} taken but ${record.seniorId.take(6)} is feeling ${mood.label}",
                    severity = AlertSeverity.HIGH,
                    medicationName = record.medicationName
                )
            }
        }

        createAlert(
            seniorId = record.seniorId,
            type = AlertType.DOSE_CONFIRMED,
            message = "${record.medicationName} taken at ${timeFmt.format(Date(now))}",
            severity = AlertSeverity.LOW,
            medicationName = record.medicationName
        )
    }

    suspend fun markMissedDoses(seniorId: String) {
        val today = dateFmt.format(Date())
        val now = System.currentTimeMillis()
        runCatching {
            val snap = db.collection("doseRecords")
                .whereEqualTo("seniorId", seniorId)
                .whereEqualTo("date", today)
                .whereEqualTo("status", DoseStatus.PENDING.name)
                .get().await()
            val batch = db.batch()
            for (doc in snap.documents) {
                val record = doc.toObject(DoseRecord::class.java) ?: continue
                if (now - record.scheduledTime > 45 * 60 * 1000L) {
                    batch.update(doc.reference, "status", DoseStatus.MISSED.name)
                    createAlert(
                        seniorId = seniorId,
                        type = AlertType.MISSED_DOSE,
                        message = "${record.medicationName} was due at ${timeFmt.format(Date(record.scheduledTime))} and was not taken",
                        severity = AlertSeverity.HIGH,
                        medicationName = record.medicationName
                    )
                }
            }
            batch.commit().await()
        }
    }

    suspend fun addMedication(medication: Medication): Result<String> = runCatching {
        val ref = db.collection("medications").document()
        ref.set(medication.copy(id = ref.id)).await()
        ref.id
    }

    suspend fun seedDemoData(seniorId: String) {
        val meds = listOf(
            Medication(
                seniorId = seniorId,
                name = "Amlodipine 5mg",
                purpose = "Blood Pressure",
                dosage = "1 tablet",
                pillColorHex = "#F44336",
                pillEmoji = "🔴",
                scheduleTimes = listOf("08:00", "20:00"),
                instructions = "Take with food",
                remainingPills = 28
            ),
            Medication(
                seniorId = seniorId,
                name = "Metformin 500mg",
                purpose = "Diabetes",
                dosage = "1 tablet",
                pillColorHex = "#2196F3",
                pillEmoji = "🔵",
                scheduleTimes = listOf("07:30", "13:00", "19:30"),
                instructions = "Take after meals",
                remainingPills = 45
            ),
            Medication(
                seniorId = seniorId,
                name = "Atorvastatin 10mg",
                purpose = "Cholesterol",
                dosage = "1 tablet",
                pillColorHex = "#FF9800",
                pillEmoji = "🟠",
                scheduleTimes = listOf("21:00"),
                instructions = "Take at bedtime",
                remainingPills = 14
            ),
            Medication(
                seniorId = seniorId,
                name = "Vitamin D3",
                purpose = "Bone Health",
                dosage = "1 capsule",
                pillColorHex = "#FFEB3B",
                pillEmoji = "🟡",
                scheduleTimes = listOf("09:00"),
                instructions = "Take with breakfast",
                remainingPills = 60
            )
        )
        for (med in meds) {
            val ref = db.collection("medications").document()
            ref.set(med.copy(id = ref.id, createdAt = System.currentTimeMillis())).await()
        }
    }

    suspend fun saveMoodRecord(seniorId: String, mood: MoodLevel, note: String = "") {
        runCatching {
            val id = UUID.randomUUID().toString()
            db.collection("moodRecords").document(id).set(
                MoodRecord(id = id, seniorId = seniorId, mood = mood.name, timestamp = System.currentTimeMillis())
            ).await()
            if (mood.score <= 2) {
                createAlert(seniorId, AlertType.MOOD_LOW, "Feeling ${mood.label} — detected via voice", AlertSeverity.HIGH)
            }
        }
    }

    suspend fun saveConversationReport(report: ConversationReport): Result<Unit> = runCatching {
        val id = UUID.randomUUID().toString()
        db.collection("conversationReports").document(id)
            .set(report.copy(id = id)).await()
    }

    fun getConversationReports(seniorId: String): Flow<List<ConversationReport>> = callbackFlow {
        val listener = db.collection("conversationReports")
            .whereEqualTo("seniorId", seniorId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(ConversationReport::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    private suspend fun createAlert(
        seniorId: String,
        type: AlertType,
        message: String,
        severity: AlertSeverity,
        medicationName: String = ""
    ) {
        val id = UUID.randomUUID().toString()
        db.collection("alerts").document(id).set(
            Alert(
                id = id,
                seniorId = seniorId,
                type = type.name,
                message = message,
                severity = severity.name,
                timestamp = System.currentTimeMillis(),
                medicationName = medicationName
            )
        ).await()
    }
}
