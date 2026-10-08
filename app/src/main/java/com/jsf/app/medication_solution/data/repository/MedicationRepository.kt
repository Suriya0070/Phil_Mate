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

        // Fetch which dose record IDs already exist — never overwrite them (would erase TAKEN/MISSED)
        val existing = runCatching {
            db.collection("doseRecords")
                .whereEqualTo("seniorId", seniorId)
                .whereEqualTo("date", today)
                .get().await()
        }.getOrNull()
        val existingIds = existing?.documents?.map { it.id }?.toSet() ?: emptySet()

        val batch = db.batch()
        var hasNew = false
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
                if (recordId in existingIds) continue   // already exists — never overwrite
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
                hasNew = true
            }
        }
        if (hasNew) runCatching { batch.commit().await() }
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

    suspend fun calculateAdherenceStreak(seniorId: String): Int = runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var streak = 0
        val cal = Calendar.getInstance()
        for (i in 0..29) {
            val dateStr = fmt.format(cal.time)
            val snap = db.collection("doseRecords")
                .whereEqualTo("seniorId", seniorId)
                .whereEqualTo("date", dateStr)
                .get().await()
            val doses = snap.documents.mapNotNull { it.toObject(DoseRecord::class.java) }
            if (doses.isEmpty()) { cal.add(Calendar.DAY_OF_YEAR, -1); continue }
            if (!doses.all { it.doseStatus() == DoseStatus.TAKEN }) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        streak
    }.getOrDefault(0)

    suspend fun addMedication(medication: Medication): Result<String> = runCatching {
        val ref = db.collection("medications").document()
        ref.set(medication.copy(id = ref.id)).await()
        ref.id
    }

    suspend fun seedDemoData(seniorId: String) {
        // Delete all existing medications and dose records for this senior first
        val existingMeds = db.collection("medications")
            .whereEqualTo("seniorId", seniorId).get().await()
        for (doc in existingMeds.documents) doc.reference.delete().await()
        val existingDoses = db.collection("doseRecords")
            .whereEqualTo("seniorId", seniorId).get().await()
        for (doc in existingDoses.documents) doc.reference.delete().await()

        val meds = listOf(
            // ── Morning slot (07:00–09:00) ── 4 meds
            Medication(
                seniorId = seniorId,
                name = "Pan 40",
                purpose = "Acidity / Gastric (Saapida munpu)",
                dosage = "1 tab",
                pillColorHex = "#00ACC1",
                pillEmoji = "💊",
                scheduleTimes = listOf("07:00"),
                instructions = "Saapida 30 nimisham munpu edunga",
                remainingPills = 30,
                shape = "capsule",
                colorIndex = 5
            ),
            Medication(
                seniorId = seniorId,
                name = "Dolo 650",
                purpose = "Kaichal & Vali (Fever & Pain)",
                dosage = "1 tab",
                pillColorHex = "#E53935",
                pillEmoji = "💊",
                scheduleTimes = listOf("08:00", "14:00", "20:00"),
                instructions = "Saapida potta edunga",
                remainingPills = 28,
                shape = "oval",
                colorIndex = 0
            ),
            Medication(
                seniorId = seniorId,
                name = "Telma 40",
                purpose = "Ratham azhuththam (BP / Blood Pressure)",
                dosage = "1 tab",
                pillColorHex = "#43A047",
                pillEmoji = "💊",
                scheduleTimes = listOf("08:30"),
                instructions = "Kaalaiyil thinra potta edunga",
                remainingPills = 20,
                shape = "circle",
                colorIndex = 2
            ),
            Medication(
                seniorId = seniorId,
                name = "Metformin 500",
                purpose = "Neeriziv noi (Diabetes / Sugar)",
                dosage = "1 tab",
                pillColorHex = "#FF8F00",
                pillEmoji = "💊",
                scheduleTimes = listOf("09:00", "21:00"),
                instructions = "Saapida potta edunga",
                remainingPills = 60,
                shape = "square",
                colorIndex = 3
            ),
            // ── Afternoon slot (13:00–14:00) ── 3 meds
            Medication(
                seniorId = seniorId,
                name = "Shelcal 500",
                purpose = "Elumbu vali (Calcium / Bone)",
                dosage = "1 tab",
                pillColorHex = "#8E24AA",
                pillEmoji = "💊",
                scheduleTimes = listOf("13:30"),
                instructions = "Saapida potta edunga",
                remainingPills = 30,
                shape = "circle",
                colorIndex = 4
            ),
            Medication(
                seniorId = seniorId,
                name = "Ulgel",
                purpose = "Vayiru vali (Antacid / Acidity)",
                dosage = "2 tsp",
                pillColorHex = "#6D4C41",
                pillEmoji = "💊",
                scheduleTimes = listOf("13:00"),
                instructions = "Saapida potta pinju kudiukkavum",
                remainingPills = 25,
                shape = "oval",
                colorIndex = 7
            ),
            // ── Evening slot (18:00) ── 1 med
            Medication(
                seniorId = seniorId,
                name = "Ecosprin 75",
                purpose = "Neer maitha kaappu (Blood Thinner)",
                dosage = "1 tab",
                pillColorHex = "#1E88E5",
                pillEmoji = "💊",
                scheduleTimes = listOf("18:00"),
                instructions = "Saapida potta edunga",
                remainingPills = 30,
                shape = "circle",
                colorIndex = 1
            ),
            // ── Night slot (21:00) ── 2 meds
            Medication(
                seniorId = seniorId,
                name = "Atorva 10",
                purpose = "Koluppugal (Cholesterol)",
                dosage = "1 tab",
                pillColorHex = "#E91E63",
                pillEmoji = "💊",
                scheduleTimes = listOf("21:30"),
                instructions = "Iravu thoongum munpu edunga",
                remainingPills = 28,
                shape = "capsule",
                colorIndex = 6
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
