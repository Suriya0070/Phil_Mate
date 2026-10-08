package com.jsf.app.medication_solution.ui.senior

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodLevel
import com.jsf.app.medication_solution.data.model.User
import com.jsf.app.medication_solution.data.repository.AuthRepository
import com.jsf.app.medication_solution.data.repository.MedicationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LedStatus { NONE, GREEN, RED }

enum class TextSizePref(val scale: Float, val label: String) {
    NORMAL(1f, "A"),
    LARGE(1.25f, "A+"),
    XLARGE(1.5f, "A++")
}

data class SeniorUiState(
    val user: User? = null,
    val medications: List<Medication> = emptyList(),
    val todayDoses: List<DoseRecord> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
    val showEngagement: Boolean = false,
    val engagementQuestion: String = "",
    val showDoubleDoseWarning: Boolean = false,
    val doubleDoseTakenAt: String = "",
    val doubleDoseMedName: String = "",
    val isMonitored: Boolean = false,
    val hasVoiceNote: Boolean = false,
    val dailyChallenge: String? = null,
    val selectedDayOffset: Int = 0,
    val showWeekGrid: Boolean = false,
    val textSizePref: TextSizePref = TextSizePref.NORMAL,
    // Smart Box simulation
    val bandConnected: Boolean = true,
    val boxLedStatus: LedStatus = LedStatus.NONE,
    val boxLedMessage: String = "",
    val cameraMonitoringActive: Boolean = false,
    val intakeDetected: Boolean? = null
)

class SeniorViewModel(app: Application) : AndroidViewModel(app) {
    private val authRepo = AuthRepository()
    private val medRepo = MedicationRepository()
    private val prefs = app.getSharedPreferences("senior_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SeniorUiState())
    val state: StateFlow<SeniorUiState> = _state.asStateFlow()

    private var inactivityJob: Job? = null
    private var lastInteraction = System.currentTimeMillis()
    private var autoSeeded = false
    private var dosesCreatedForDate = ""   // prevents re-creating dose records on every listener update
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

    private val engagementMessages = listOf(
        "👋 Hey! What's your favourite Tamil song?",
        "💧 Have you had 8 glasses of water today?",
        "🚶 How about a short walk? Exercise is medicine too!",
        "😊 Tell me something that made you smile today!",
        "☕ What did you have for breakfast today?",
        "🎵 Want me to remind you to do your breathing exercises?",
        "🌸 You're doing amazing! Your family is proud of you.",
        "🌤️ How's the weather outside? Maybe open a window!"
    )

    init {
        val savedScale = prefs.getFloat("text_scale", 1f)
        val savedPref = TextSizePref.values().find { it.scale == savedScale } ?: TextSizePref.NORMAL
        _state.value = _state.value.copy(textSizePref = savedPref, dailyChallenge = getDailyChallenge())
        loadData()
        startInactivityMonitor()
    }

    private fun loadData() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            authRepo.getUserById(uid).onSuccess { user ->
                _state.value = _state.value.copy(user = user)
                loadMedsAndDoses(uid)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
        viewModelScope.launch {
            delay(5000)
            if (_state.value.medications.isEmpty() && !autoSeeded) {
                autoSeeded = true
                medRepo.seedDemoData(uid)
            }
        }
        viewModelScope.launch {
            authRepo.observeUser(uid).collect { user ->
                _state.value = _state.value.copy(isMonitored = user.isMonitored)
            }
        }
    }

    private fun loadMedsAndDoses(seniorId: String) {
        viewModelScope.launch {
            combine(
                medRepo.getMedicationsForSenior(seniorId),
                medRepo.getTodayDoseRecords(seniorId)
            ) { meds, doses -> Pair(meds, doses) }
                .collect { (meds, doses) ->
                    val today = dateFmt.format(java.util.Date())

                    // Only create dose records once per day, never on subsequent listener updates
                    if (meds.isNotEmpty() && dosesCreatedForDate != today) {
                        dosesCreatedForDate = today
                        medRepo.createDoseRecordsForToday(seniorId, meds)
                    }

                    // Never show an empty list while Firestore is just catching up —
                    // keep the last known list until we have real data
                    val safeMeds  = if (meds.isNotEmpty()) meds  else _state.value.medications
                    val safeDoses = if (doses.isNotEmpty()) doses else _state.value.todayDoses

                    _state.value = _state.value.copy(
                        medications = safeMeds,
                        todayDoses  = safeDoses,
                        isLoading   = false
                    )

                    // Mark missed doses only once per load, not on every Firestore event
                    if (meds.isNotEmpty()) medRepo.markMissedDoses(seniorId)

                    if (meds.isEmpty() && !autoSeeded) {
                        autoSeeded = true
                        delay(2000)
                        medRepo.seedDemoData(seniorId)
                    }
                }
        }
    }

    fun confirmDose(record: DoseRecord, mood: MoodLevel?) {
        if (record.isTaken()) {
            val takenTime = timeFmt.format(Date(record.takenAt))
            _state.value = _state.value.copy(
                showDoubleDoseWarning = true,
                doubleDoseTakenAt = takenTime,
                doubleDoseMedName = record.medicationName
            )
            return
        }
        viewModelScope.launch {
            medRepo.confirmDose(record, mood)
                .onSuccess {
                    _state.value = _state.value.copy(
                        successMessage = "✅ ${record.medicationName} எடுத்துவிட்டீர்கள்! (Taken)"
                    )
                    onUserInteraction()
                    cancelVerificationAlarm(record.medicationName)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }

    private fun cancelVerificationAlarm(medName: String) {
        val ctx = getApplication<android.app.Application>()
        val verifyIntent = android.content.Intent(ctx, com.jsf.app.medication_solution.service.MedVerifyReceiver::class.java)
        val requestCode = "$medName-verify".hashCode()
        val pi = android.app.PendingIntent.getBroadcast(
            ctx, requestCode, verifyIntent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(pi)
        }
    }

    fun onUserInteraction() {
        lastInteraction = System.currentTimeMillis()
        authRepo.currentUserId?.let { uid ->
            viewModelScope.launch { authRepo.updateLastActive(uid) }
        }
    }

    private fun startInactivityMonitor() {
        inactivityJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val inactiveMs = System.currentTimeMillis() - lastInteraction
                val state = _state.value
                if (inactiveMs >= 15 * 60_000L &&
                    state.user?.engagementEnabled == true &&
                    !state.showEngagement
                ) {
                    _state.value = _state.value.copy(
                        showEngagement = true,
                        engagementQuestion = engagementMessages.random()
                    )
                    lastInteraction = System.currentTimeMillis()
                }
            }
        }
    }

    fun dismissEngagement() {
        _state.value = _state.value.copy(showEngagement = false)
        onUserInteraction()
    }

    fun dismissDoubleDoseWarning() {
        _state.value = _state.value.copy(showDoubleDoseWarning = false)
    }

    fun clearSuccess() { _state.value = _state.value.copy(successMessage = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun getDoseForMedication(medicationId: String): DoseRecord? =
        _state.value.todayDoses.find { it.medicationId == medicationId }

    fun getAdherenceToday(): Pair<Int, Int> {
        val doses = _state.value.todayDoses
        val taken = doses.count { it.isTaken() }
        return Pair(taken, doses.size)
    }

    fun selectDay(offset: Int) {
        _state.value = _state.value.copy(selectedDayOffset = offset)
    }

    fun toggleWeekGrid() {
        _state.value = _state.value.copy(showWeekGrid = !_state.value.showWeekGrid)
    }

    fun cycleTextSize() {
        val next = when (_state.value.textSizePref) {
            TextSizePref.NORMAL -> TextSizePref.LARGE
            TextSizePref.LARGE -> TextSizePref.XLARGE
            TextSizePref.XLARGE -> TextSizePref.NORMAL
        }
        prefs.edit().putFloat("text_scale", next.scale).apply()
        _state.value = _state.value.copy(textSizePref = next)
    }

    fun setVoiceError(msg: String) {
        _state.value = _state.value.copy(successMessage = msg)
    }

    fun confirmDoseByVoice(spokenText: String) {
        val lower = spokenText.lowercase().trim()
        val doses = _state.value.todayDoses
        val pending = doses.filter { !it.isTaken() && !it.isMissed() }

        // Build nickname/alias map for common medicine names
        val aliases = mapOf(
            "dolo" to listOf("dolo", "dolor", "dolo650", "dollo", "paracetamol", "paracetomol"),
            "pan 40" to listOf("pan", "pan40", "pantoprazole", "pantop", "acidity"),
            "telma 40" to listOf("telma", "telmisartan", "bp", "blood pressure", "thelma"),
            "metformin 500" to listOf("metformin", "metformine", "sugar", "diabetes", "metro"),
            "shelcal 500" to listOf("shelcal", "calcium", "cal", "bone", "shellcal"),
            "ulgel" to listOf("ulgel", "antacid", "gel", "stomach", "vayiru"),
            "ecosprin 75" to listOf("ecosprin", "aspirin", "eco", "heart", "blood thinner"),
            "atorva 10" to listOf("atorva", "atorvastatin", "cholesterol", "statin", "atora")
        )

        // Step 1: Try alias match against pending doses
        for (dose in pending) {
            val doseKey = dose.medicationName.lowercase()
            val aliasSet = aliases.entries.find { (k, _) -> doseKey.contains(k) }?.value ?: emptyList()
            val directWords = dose.medicationName.lowercase().split(" ", "-").filter { it.length >= 3 }
            val allMatchers = (directWords + aliasSet).distinct()
            if (allMatchers.any { lower.contains(it) }) {
                viewModelScope.launch {
                    medRepo.confirmDose(dose, null)
                    _state.value = _state.value.copy(
                        successMessage = "✅ ${dose.medicationName} எடுத்துவிட்டீர்கள்!"
                    )
                    cancelVerificationAlarm(dose.medicationName)
                }
                return
            }
        }

        // Step 2: If only one pending dose left, "took / eduthen / potuten" keywords confirm it
        val tookKeywords = listOf(
            "took", "taken", "done", "yes", "ok", "okay",
            "potuten", "potutten", "eduthen", "eduten", "saptuten",
            "போட்டேன்", "எடுத்தேன்", "போட்டுட்டேன்", "எடுத்துட்டேன்"
        )
        if (tookKeywords.any { lower.contains(it) } && pending.size == 1) {
            viewModelScope.launch {
                medRepo.confirmDose(pending.first(), null)
                _state.value = _state.value.copy(
                    successMessage = "✅ ${pending.first().medicationName} எடுத்துவிட்டீர்கள்!"
                )
                cancelVerificationAlarm(pending.first().medicationName)
            }
            return
        }

        // Step 3: Nothing matched — show what was heard
        _state.value = _state.value.copy(
            successMessage = "\"$spokenText\" — மருந்து பெயர் சொல்லுங்கள்"
        )
    }

    fun seedDemoMedications() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch { medRepo.seedDemoData(uid) }
    }

    fun resetAndReseed() {
        val uid = authRepo.currentUserId ?: return
        _state.value = _state.value.copy(isLoading = true)
        autoSeeded = false
        viewModelScope.launch {
            medRepo.seedDemoData(uid)
            _state.value = _state.value.copy(successMessage = "✅ Medicines reset!")
        }
    }

    fun onMedicineStripPicked(pickedMedicationId: String) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val currentSlotDoses = _state.value.todayDoses.filter { dose ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = dose.scheduledTime
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..11 -> h in 6..11
                in 12..16 -> h in 12..16
                in 17..20 -> h in 17..20
                else -> h >= 21 || h < 6
            }
        }.filter { !it.isTaken() }

        val isCorrect = currentSlotDoses.any { it.medicationId == pickedMedicationId }
        if (isCorrect) {
            _state.value = _state.value.copy(
                boxLedStatus = LedStatus.GREEN,
                boxLedMessage = "✅ சரியான மருந்து! (Correct medicine!)",
                cameraMonitoringActive = true,
                intakeDetected = null
            )
            viewModelScope.launch {
                delay(3000)
                _state.value = _state.value.copy(intakeDetected = true, cameraMonitoringActive = false)
                delay(4000)
                _state.value = _state.value.copy(
                    boxLedStatus = LedStatus.NONE, boxLedMessage = "", intakeDetected = null
                )
            }
        } else {
            _state.value = _state.value.copy(
                boxLedStatus = LedStatus.RED,
                boxLedMessage = "❌ தவறான மருந்து! (Wrong medicine!) — Please check",
                cameraMonitoringActive = false,
                intakeDetected = null
            )
            viewModelScope.launch {
                delay(4000)
                _state.value = _state.value.copy(boxLedStatus = LedStatus.NONE, boxLedMessage = "")
            }
        }
    }

    fun getCurrentSlotMeds(): List<Medication> {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return _state.value.medications.filter { med ->
            med.scheduleTimes.any { t ->
                val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                when (hour) {
                    in 6..11 -> h in 6..11
                    in 12..16 -> h in 12..16
                    in 17..20 -> h in 17..20
                    else -> h >= 21 || h < 6
                }
            }
        }
    }

    fun checkFamilyVoiceNote() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            runCatching {
                val ref = com.google.firebase.storage.FirebaseStorage.getInstance()
                    .reference.child("voiceNotes/$uid/latest.mp4")
                ref.metadata.await()
                _state.value = _state.value.copy(hasVoiceNote = true)
            }
        }
    }

    fun playFamilyVoiceNote(context: android.content.Context) {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            com.jsf.app.medication_solution.service.VoiceAlarmManager.downloadAndPlayFamilyNote(context, uid)
        }
    }

    private fun getDailyChallenge(): String {
        val challenges = listOf(
            "🧠 What is 15 + 27?",
            "🔤 Name 3 vegetables starting with 'C'",
            "🎯 What day of the week was yesterday?",
            "🔢 Count backwards from 20 to 1 out loud!",
            "🌍 Name the capital of India",
            "🎵 Hum your favorite song for 10 seconds!",
            "📅 What year were you born?",
            "🌺 Name 3 flowers you love!"
        )
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        return challenges[dayOfYear % challenges.size]
    }

    override fun onCleared() {
        super.onCleared()
        inactivityJob?.cancel()
    }
}
