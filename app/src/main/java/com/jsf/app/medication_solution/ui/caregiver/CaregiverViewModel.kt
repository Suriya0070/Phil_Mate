package com.jsf.app.medication_solution.ui.caregiver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jsf.app.medication_solution.data.model.Alert
import com.jsf.app.medication_solution.data.model.ConversationReport
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodRecord
import com.jsf.app.medication_solution.data.model.User
import com.jsf.app.medication_solution.data.repository.AuthRepository
import com.jsf.app.medication_solution.data.repository.CaregiverRepository
import com.jsf.app.medication_solution.service.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SeniorStatusLevel(val label: String, val emoji: String) {
    GREEN("All Good", "✅"),
    AMBER("Needs Attention", "⚠️"),
    RED("Urgent", "🚨")
}

data class SeniorSnapshot(
    val user: User = User(),
    val todayDoses: List<DoseRecord> = emptyList(),
    val latestMood: MoodRecord? = null,
    val weeklyAdherence: Float = 0f,
    val statusLevel: SeniorStatusLevel = SeniorStatusLevel.GREEN,
    val inactiveMinutes: Long = 0L
)

data class CaregiverUiState(
    val caregiver: User? = null,
    val seniorSnapshot: SeniorSnapshot? = null,
    val alerts: List<Alert> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val conversationReports: List<ConversationReport> = emptyList(),
    val checkInIntervalMinutes: Int = 15,
    val isMonitoringEnabled: Boolean = false,
    val showAddMedDialog: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val noSeniorLinked: Boolean = false,
    val linkDialogVisible: Boolean = false,
    val linkEmail: String = "",
    val linkSuccess: String? = null
)

class CaregiverViewModel : ViewModel() {
    private val authRepo = AuthRepository()
    private val caregiverRepo = CaregiverRepository()

    private val _state = MutableStateFlow(CaregiverUiState())
    val state: StateFlow<CaregiverUiState> = _state.asStateFlow()

    init { loadData() }

    private fun loadData() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            authRepo.getUserById(uid).onSuccess { caregiver ->
                _state.value = _state.value.copy(caregiver = caregiver)
                val seniorId = caregiver.linkedSeniorId
                if (seniorId.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false, noSeniorLinked = true)
                } else {
                    loadSeniorData(seniorId)
                }
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    private fun loadSeniorData(seniorId: String) {
        viewModelScope.launch {
            caregiverRepo.observeSeniorUser(seniorId).collect { senior ->
                val inactiveMs = System.currentTimeMillis() - senior.lastActiveAt
                val inactiveMin = inactiveMs / 60_000L
                updateSnapshot { current ->
                    val statusLevel = calculateStatus(
                        current?.todayDoses ?: emptyList(),
                        inactiveMin,
                        current?.latestMood
                    )
                    (current ?: SeniorSnapshot()).copy(
                        user = senior,
                        statusLevel = statusLevel,
                        inactiveMinutes = inactiveMin
                    )
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    isMonitoringEnabled = senior.isMonitored,
                    checkInIntervalMinutes = if (senior.checkInIntervalMinutes > 0) senior.checkInIntervalMinutes else 15
                )
            }
        }

        viewModelScope.launch {
            caregiverRepo.observeTodayDoses(seniorId).collect { doses ->
                updateSnapshot { current ->
                    val statusLevel = calculateStatus(
                        doses,
                        current?.inactiveMinutes ?: 0L,
                        current?.latestMood
                    )
                    (current ?: SeniorSnapshot()).copy(todayDoses = doses, statusLevel = statusLevel)
                }
            }
        }

        viewModelScope.launch {
            caregiverRepo.observeMoodHistory(seniorId).collect { moods ->
                updateSnapshot { current ->
                    (current ?: SeniorSnapshot()).copy(latestMood = moods.firstOrNull())
                }
            }
        }

        viewModelScope.launch {
            caregiverRepo.observeAlerts(seniorId).collect { alerts ->
                _state.value = _state.value.copy(alerts = alerts)
            }
        }

        viewModelScope.launch {
            val adherence = caregiverRepo.calculateWeeklyAdherence(seniorId)
            updateSnapshot { current ->
                (current ?: SeniorSnapshot()).copy(weeklyAdherence = adherence)
            }
        }

        viewModelScope.launch {
            caregiverRepo.getMedicationsForSenior(seniorId).collect { meds ->
                _state.value = _state.value.copy(medications = meds)
            }
        }

        viewModelScope.launch {
            caregiverRepo.observeConversationReports(seniorId).collect { reports ->
                _state.value = _state.value.copy(conversationReports = reports)
            }
        }
    }

    private fun updateSnapshot(transform: (SeniorSnapshot?) -> SeniorSnapshot) {
        _state.value = _state.value.copy(seniorSnapshot = transform(_state.value.seniorSnapshot))
    }

    private fun calculateStatus(
        doses: List<DoseRecord>,
        inactiveMinutes: Long,
        latestMood: MoodRecord?
    ): SeniorStatusLevel {
        val hasMissed = doses.any { it.doseStatus() == DoseStatus.MISSED }
        val hasOverdue = doses.any { it.isOverdue() }
        val isLowMood = latestMood != null && latestMood.moodLevel().score <= 2
        val isVeryInactive = inactiveMinutes > 60

        return when {
            hasMissed || isLowMood || isVeryInactive -> SeniorStatusLevel.RED
            hasOverdue || inactiveMinutes > 30 -> SeniorStatusLevel.AMBER
            else -> SeniorStatusLevel.GREEN
        }
    }

    fun showLinkDialog() { _state.value = _state.value.copy(linkDialogVisible = true) }
    fun hideLinkDialog() { _state.value = _state.value.copy(linkDialogVisible = false, linkEmail = "") }
    fun onLinkEmailChanged(email: String) { _state.value = _state.value.copy(linkEmail = email) }

    fun linkSenior() {
        val caregiverId = authRepo.currentUserId ?: return
        val email = _state.value.linkEmail.trim()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            authRepo.linkCaregiverToSenior(caregiverId, email)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        linkDialogVisible = false,
                        noSeniorLinked = false,
                        linkSuccess = "Successfully linked! Loading their data now..."
                    )
                    loadData()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Could not find that senior account"
                    )
                }
        }
    }

    fun resolveAlert(alertId: String) {
        viewModelScope.launch { caregiverRepo.resolveAlert(alertId) }
    }

    fun showAddMedDialog() { _state.value = _state.value.copy(showAddMedDialog = true) }
    fun hideAddMedDialog() { _state.value = _state.value.copy(showAddMedDialog = false) }

    fun addMedication(
        name: String,
        dosage: String,
        purpose: String,
        scheduleTimes: List<String>,
        instructions: String,
        pillEmoji: String,
        pillColorHex: String
    ) {
        val seniorId = _state.value.seniorSnapshot?.user?.id ?: return
        val med = Medication(
            seniorId = seniorId,
            name = name,
            dosage = dosage,
            purpose = purpose,
            scheduleTimes = scheduleTimes,
            instructions = instructions,
            pillEmoji = pillEmoji,
            pillColorHex = pillColorHex
        )
        viewModelScope.launch {
            caregiverRepo.addMedicationForSenior(med)
                .onSuccess { _state.value = _state.value.copy(showAddMedDialog = false) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun setCheckInInterval(context: Context, intervalMinutes: Int) {
        val seniorId = _state.value.seniorSnapshot?.user?.id ?: return
        _state.value = _state.value.copy(checkInIntervalMinutes = intervalMinutes)
        viewModelScope.launch {
            authRepo.updateCheckInInterval(seniorId, intervalMinutes)
        }
        AlarmScheduler.scheduleCheckIn(context, seniorId, intervalMinutes)
    }

    fun toggleMonitoring() {
        val seniorId = _state.value.seniorSnapshot?.user?.id ?: return
        val newValue = !_state.value.isMonitoringEnabled
        _state.value = _state.value.copy(isMonitoringEnabled = newValue)
        viewModelScope.launch {
            authRepo.updateMonitoring(seniorId, newValue)
        }
    }

    fun scheduleMedicationAlarms(context: Context) {
        val seniorId = _state.value.seniorSnapshot?.user?.id ?: return
        val meds = _state.value.medications
        AlarmScheduler.scheduleMedicationAlarms(context, meds, seniorId)
        _state.value = _state.value.copy(linkSuccess = "Alarms scheduled for ${meds.size} medication(s)!")
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearLinkSuccess() { _state.value = _state.value.copy(linkSuccess = null) }
}
