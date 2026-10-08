package com.jsf.app.medication_solution.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jsf.app.medication_solution.data.model.Alert
import com.jsf.app.medication_solution.data.model.ConversationReport
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodRecord
import com.jsf.app.medication_solution.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CaregiverRepository {
    private val db = FirebaseFirestore.getInstance()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun observeSeniorUser(seniorId: String): Flow<User> = callbackFlow {
        val listener = db.collection("users").document(seniorId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                snap?.toObject(User::class.java)?.copy(id = snap.id)?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun observeTodayDoses(seniorId: String): Flow<List<DoseRecord>> = callbackFlow {
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

    fun observeAlerts(seniorId: String): Flow<List<Alert>> = callbackFlow {
        val listener = db.collection("alerts")
            .whereEqualTo("seniorId", seniorId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(Alert::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun observeMoodHistory(seniorId: String): Flow<List<MoodRecord>> = callbackFlow {
        val listener = db.collection("moodRecords")
            .whereEqualTo("seniorId", seniorId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(7)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(MoodRecord::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun calculateWeeklyAdherence(seniorId: String): Float {
        val cal = Calendar.getInstance()
        var taken = 0
        var total = 0
        repeat(7) {
            val date = dateFmt.format(cal.time)
            runCatching {
                val snap = db.collection("doseRecords")
                    .whereEqualTo("seniorId", seniorId)
                    .whereEqualTo("date", date)
                    .get().await()
                val records = snap.documents.mapNotNull { it.toObject(DoseRecord::class.java) }
                total += records.size
                taken += records.count { it.doseStatus() == DoseStatus.TAKEN }
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return if (total == 0) 0f else taken.toFloat() / total.toFloat()
    }

    suspend fun resolveAlert(alertId: String) {
        runCatching {
            db.collection("alerts").document(alertId).update("isResolved", true).await()
        }
    }

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

    fun observeConversationReports(seniorId: String): Flow<List<ConversationReport>> = callbackFlow {
        val listener = db.collection("conversationReports")
            .whereEqualTo("seniorId", seniorId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(ConversationReport::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun addMedicationForSenior(medication: Medication): Result<String> = runCatching {
        val ref = db.collection("medications").document()
        ref.set(medication.copy(id = ref.id, createdAt = System.currentTimeMillis())).await()
        ref.id
    }

    suspend fun saveEmergencyContact(seniorId: String, name: String, phone: String) {
        runCatching {
            db.collection("users").document(seniorId).update(
                mapOf("emergencyContactName" to name, "emergencyContactPhone" to phone)
            ).await()
        }
    }

    fun getEmergencyContact(seniorId: String): Flow<Pair<String, String>> = callbackFlow {
        val listener = db.collection("users").document(seniorId)
            .addSnapshotListener { snap, _ ->
                val name = snap?.getString("emergencyContactName") ?: ""
                val phone = snap?.getString("emergencyContactPhone") ?: ""
                trySend(Pair(name, phone))
            }
        awaitClose { listener.remove() }
    }

    suspend fun calculateAdherenceStreak(seniorId: String): Int {
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            var streak = 0
            val cal = Calendar.getInstance()
            for (i in 0..6) {
                val dateStr = fmt.format(cal.time)
                val snap = db.collection("doseRecords")
                    .whereEqualTo("seniorId", seniorId)
                    .whereEqualTo("date", dateStr)
                    .get().await()
                val doses = snap.documents.mapNotNull { it.toObject(DoseRecord::class.java) }
                if (doses.isEmpty()) break
                if (!doses.all { it.doseStatus() == DoseStatus.TAKEN }) break
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            streak
        }.getOrDefault(0)
    }

    suspend fun setInteractionSchedule(seniorId: String, caregiverId: String, intervalMinutes: Int) {
        runCatching {
            db.collection("interactionSchedules").document(seniorId).set(mapOf(
                "seniorId" to seniorId,
                "caregiverId" to caregiverId,
                "intervalMinutes" to intervalMinutes,
                "enabled" to true,
                "updatedAt" to System.currentTimeMillis()
            )).await()
        }
    }
}
