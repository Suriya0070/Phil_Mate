package com.jsf.app.medication_solution.ui.senior

import androidx.lifecycle.ViewModel
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
    val selectedDayOffset: Int = 0
)

class SeniorViewModel : ViewModel() {
    private val authRepo = AuthRepository()
    private val medRepo = MedicationRepository()

    private val _state = MutableStateFlow(SeniorUiState())
    val state: StateFlow<SeniorUiState> = _state.asStateFlow()

    private var inactivityJob: Job? = null
    private var lastInteraction = System.currentTimeMillis()
    private var autoSeeded = false
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

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
        loadData()
        startInactivityMonitor()
        _state.value = _state.value.copy(dailyChallenge = getDailyChallenge())
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
                    if (doses.isEmpty() && meds.isNotEmpty()) {
                        medRepo.createDoseRecordsForToday(seniorId, meds)
                    }
                    _state.value = _state.value.copy(
                        medications = meds,
                        todayDoses = doses,
                        isLoading = false
                    )
                    medRepo.markMissedDoses(seniorId)
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
                        successMessage = "✅ ${record.medicationName} marked as taken!"
                    )
                    onUserInteraction()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
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

    fun confirmDoseByVoice(spokenText: String) {
        val lower = spokenText.lowercase()
        val tookPatterns = listOf("potuten", "potutten", "took", "taken", "eduthen", "eduten", "saptuten", "போட்டுட்டேன்", "எடுத்துட்டேன்", "சாப்பிட்டுட்டேன்")
        if (tookPatterns.none { lower.contains(it) }) return
        val doses = _state.value.todayDoses
        for (dose in doses) {
            if (dose.isTaken() || dose.isMissed()) continue
            val words = dose.medicationName.lowercase().split(" ", "-")
            if (words.any { it.length > 3 && lower.contains(it) } || lower.contains(dose.medicationName.lowercase())) {
                viewModelScope.launch {
                    medRepo.confirmDose(dose, null)
                    _state.value = _state.value.copy(successMessage = "✅ ${dose.medicationName} எடுத்துவிட்டீர்கள்! (Marked as taken!)")
                }
                return
            }
        }
        val pending = doses.filter { !it.isTaken() && !it.isMissed() }
        if (pending.size == 1) {
            viewModelScope.launch {
                medRepo.confirmDose(pending.first(), null)
                _state.value = _state.value.copy(successMessage = "✅ ${pending.first().medicationName} எடுத்துவிட்டீர்கள்! (Marked as taken!)")
            }
        }
    }

    fun seedDemoMedications() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch { medRepo.seedDemoData(uid) }
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
