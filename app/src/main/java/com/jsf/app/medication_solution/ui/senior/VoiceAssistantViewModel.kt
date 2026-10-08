package com.jsf.app.medication_solution.ui.senior

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jsf.app.medication_solution.data.model.ConversationReport
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodLevel
import com.jsf.app.medication_solution.data.repository.AuthRepository
import com.jsf.app.medication_solution.data.repository.MedicationRepository
import com.jsf.app.medication_solution.service.AutoStartManager
import com.jsf.app.medication_solution.service.ChatMessage
import com.jsf.app.medication_solution.service.OllamaService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }
enum class DetectedEmotion(val emoji: String, val label: String) {
    HAPPY("😊", "Happy"), SAD("😢", "Sad"), WORRIED("😰", "Worried"),
    PAIN("😣", "In Pain"), TIRED("😴", "Tired"), NEUTRAL("😐", "Neutral")
}

data class ConversationTurn(val isUser: Boolean, val text: String, val emotion: DetectedEmotion? = null)

data class VoiceAssistantState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val conversation: List<ConversationTurn> = emptyList(),
    val currentTranscript: String = "",
    val detectedEmotion: DetectedEmotion = DetectedEmotion.NEUTRAL,
    val errorMessage: String? = null,
    val isOllamaAvailable: Boolean = true,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "llama3.2",
    val seniorName: String = "",
    val medications: List<Medication> = emptyList(),
    val todayDoses: List<DoseRecord> = emptyList(),
    val alarmTriggerName: String = "",
    val customOllamaUrl: String = "http://10.0.2.2:11434"
)

class VoiceAssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo = AuthRepository()
    private val medRepo = MedicationRepository()
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    private val _state = MutableStateFlow(VoiceAssistantState())
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsReady = false

    private val chatHistory = mutableListOf<ChatMessage>()
    private val emotionCounts = mutableMapOf<String, Int>()
    private val sessionStartTime = System.currentTimeMillis()
    private var reportSaved = false

    init {
        OllamaService.loadUrl(getApplication())
        _state.value = _state.value.copy(customOllamaUrl = OllamaService.currentUrl())
        initTts()
        loadContext()
        checkOllama()
        checkAlarmTrigger()
    }

    private fun checkAlarmTrigger() {
        val trigger = AutoStartManager.pendingTrigger ?: return
        _state.value = _state.value.copy(alarmTriggerName = trigger.medName)
        viewModelScope.launch {
            delay(2500)
            val greeting = when (trigger.triggerType) {
                "CHECK_IN" -> "Hello! Your caregiver wanted to check in with you. How are you feeling right now?"
                else -> "Time to take your ${trigger.medName}! How are you feeling today?"
            }
            sendMessage(greeting)
        }
    }

    private fun initTts() {
        tts = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
            }
        }
    }

    private fun loadContext() {
        val uid = authRepo.currentUserId ?: return
        viewModelScope.launch {
            authRepo.getUserById(uid).onSuccess { user ->
                _state.value = _state.value.copy(seniorName = user.name)
            }
        }
        viewModelScope.launch {
            medRepo.getMedicationsForSenior(uid).collect { meds ->
                _state.value = _state.value.copy(medications = meds)
            }
        }
        viewModelScope.launch {
            medRepo.getTodayDoseRecords(uid).collect { doses ->
                _state.value = _state.value.copy(todayDoses = doses)
            }
        }
    }

    private fun buildSystemPrompt(name: String, meds: List<Medication>, doses: List<DoseRecord>) {
        val now = timeFmt.format(Date())
        val medSummary = if (meds.isEmpty()) "No medications scheduled." else {
            meds.joinToString("\n") { med ->
                val dose = doses.find { it.medicationId == med.id }
                val status = when {
                    dose?.isTaken() == true -> "✅ TAKEN at ${timeFmt.format(Date(dose.takenAt))}"
                    dose?.isMissed() == true -> "❌ MISSED"
                    dose?.isOverdue() == true -> "⏰ OVERDUE since ${timeFmt.format(Date(dose.scheduledTime))}"
                    dose != null -> "🕐 Due at ${timeFmt.format(Date(dose.scheduledTime))}"
                    else -> "Not scheduled today"
                }
                "- ${med.name} (${med.purpose}, ${med.dosage}): $status. Times: ${med.scheduleTimes.joinToString(", ")}"
            }
        }

        val systemPrompt = """You are Paati's caring medication companion. You speak to $name, an elderly person.

CURRENT TIME: $now
TODAY'S MEDICATIONS:
$medSummary

RULES:
1. Keep responses SHORT (2-3 sentences max). Speak warmly like a caring family member.
2. Answer medication questions using the data above — be specific about times and status.
3. If they seem sad, worried, or in pain, acknowledge their feeling with empathy first, then help.
4. If they ask if they took a medicine, check the status above and tell them clearly.
5. Remind them to take any overdue/missed medicines gently.
6. Never give medical advice beyond their scheduled medications.
7. End responses with a warm, encouraging note when appropriate.
8. Detect their emotion from their words: classify as HAPPY, SAD, WORRIED, PAIN, TIRED, or NEUTRAL.
   Always end your response with exactly: [EMOTION:DETECTED_EMOTION]
   Example: [EMOTION:HAPPY] or [EMOTION:WORRIED]"""

        chatHistory.clear()
        chatHistory.add(ChatMessage("system", systemPrompt))
    }

    private fun checkOllama() {
        viewModelScope.launch {
            val models = OllamaService.listModels()
            if (models.isNotEmpty()) {
                val preferred = models.firstOrNull { it.startsWith("llama3") }
                    ?: models.firstOrNull { it.startsWith("mistral") }
                    ?: models.first()
                _state.value = _state.value.copy(
                    availableModels = models,
                    selectedModel = preferred,
                    isOllamaAvailable = true
                )
                OllamaService.configure(OllamaService.currentUrl(), preferred)
            } else {
                _state.value = _state.value.copy(isOllamaAvailable = false)
            }
        }
    }

    fun startListening() {
        if (_state.value.voiceState == VoiceState.LISTENING) return
        _state.value = _state.value.copy(voiceState = VoiceState.LISTENING, currentTranscript = "", errorMessage = null)
        tts?.stop()

        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _state.value = _state.value.copy(
                voiceState = VoiceState.ERROR,
                errorMessage = "Speech recognition not available. Type your question below."
            )
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(voiceState = VoiceState.THINKING)
                }
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't hear you. Please try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
                        SpeechRecognizer.ERROR_NETWORK -> "No internet for speech. Please type your question."
                        else -> "Microphone error. Please type your question."
                    }
                    _state.value = _state.value.copy(voiceState = VoiceState.ERROR, errorMessage = msg)
                }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = words?.firstOrNull() ?: ""
                    if (text.isNotBlank()) sendMessage(text)
                    else _state.value = _state.value.copy(voiceState = VoiceState.IDLE)
                }
                override fun onPartialResults(partial: Bundle?) {
                    val words = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    _state.value = _state.value.copy(currentTranscript = words?.firstOrNull() ?: "")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = _state.value.copy(voiceState = VoiceState.IDLE)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        buildSystemPrompt(
            _state.value.seniorName,
            _state.value.medications,
            _state.value.todayDoses
        )

        val userTurn = ConversationTurn(isUser = true, text = text)
        _state.value = _state.value.copy(
            conversation = _state.value.conversation + userTurn,
            voiceState = VoiceState.THINKING,
            currentTranscript = ""
        )

        chatHistory.add(ChatMessage("user", text))

        viewModelScope.launch {
            val result = OllamaService.chat(chatHistory)
            result.onSuccess { response ->
                val emotion = parseEmotion(response)
                val cleanResponse = response.replace(Regex("\\[EMOTION:[A-Z]+\\]"), "").trim()

                emotionCounts[emotion.name] = (emotionCounts[emotion.name] ?: 0) + 1
                chatHistory.add(ChatMessage("assistant", response))

                val assistantTurn = ConversationTurn(isUser = false, text = cleanResponse, emotion = emotion)
                _state.value = _state.value.copy(
                    conversation = _state.value.conversation + assistantTurn,
                    detectedEmotion = emotion,
                    voiceState = VoiceState.SPEAKING,
                    errorMessage = null
                )

                if (emotion != DetectedEmotion.NEUTRAL) {
                    logMoodFromEmotion(emotion)
                }

                speak(cleanResponse)
            }.onFailure { err ->
                val errorMsg = when {
                    err.message?.contains("refused") == true || err.message?.contains("connect") == true ->
                        "Cannot reach Ollama. Make sure Ollama is running on your PC."
                    else -> "AI error: ${err.message?.take(100)}"
                }
                _state.value = _state.value.copy(
                    voiceState = VoiceState.ERROR,
                    errorMessage = errorMsg,
                    conversation = _state.value.conversation + ConversationTurn(
                        isUser = false,
                        text = getFallbackResponse(_state.value.medications, _state.value.todayDoses)
                    )
                )
            }
        }
    }

    private fun saveConversationReport() {
        if (reportSaved) return
        val uid = authRepo.currentUserId ?: return
        val turns = _state.value.conversation.size
        if (turns == 0) return

        reportSaved = true
        val dominant = emotionCounts.maxByOrNull { it.value }?.key ?: "NEUTRAL"
        val trigger = AutoStartManager.pendingTrigger
        val report = ConversationReport(
            seniorId = uid,
            startTime = sessionStartTime,
            endTime = System.currentTimeMillis(),
            turns = turns,
            dominantEmotion = dominant,
            emotionCounts = emotionCounts.toMap(),
            triggerType = trigger?.triggerType ?: "MANUAL",
            summary = buildSummary()
        )
        viewModelScope.launch {
            medRepo.saveConversationReport(report)
        }
    }

    private fun buildSummary(): String {
        val turns = _state.value.conversation
        val userLines = turns.filter { it.isUser }.take(2).map { it.text.take(60) }
        return if (userLines.isEmpty()) "" else userLines.joinToString("; ")
    }

    private fun parseEmotion(response: String): DetectedEmotion {
        val match = Regex("\\[EMOTION:([A-Z]+)\\]").find(response)
        return when (match?.groupValues?.get(1)) {
            "HAPPY" -> DetectedEmotion.HAPPY
            "SAD" -> DetectedEmotion.SAD
            "WORRIED" -> DetectedEmotion.WORRIED
            "PAIN" -> DetectedEmotion.PAIN
            "TIRED" -> DetectedEmotion.TIRED
            else -> DetectedEmotion.NEUTRAL
        }
    }

    private fun getFallbackResponse(meds: List<Medication>, doses: List<DoseRecord>): String {
        if (meds.isEmpty()) return "You have no medicines scheduled today."
        val pending = doses.filter { it.doseStatus() == DoseStatus.PENDING || it.isOverdue() }
        return if (pending.isEmpty()) {
            "You've taken all your medicines today! Great job! 🎉"
        } else {
            val next = pending.minByOrNull { it.scheduledTime }
            val med = meds.find { it.id == next?.medicationId }
            "Your next medicine is ${med?.name ?: "your medicine"} due at ${next?.let { timeFmt.format(Date(it.scheduledTime)) } ?: "soon"}."
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            _state.value = _state.value.copy(voiceState = VoiceState.IDLE)
            return
        }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                _state.value = _state.value.copy(voiceState = VoiceState.IDLE)
            }
            override fun onError(utteranceId: String?) {
                _state.value = _state.value.copy(voiceState = VoiceState.IDLE)
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "med_response")
    }

    private fun logMoodFromEmotion(emotion: DetectedEmotion) {
        val uid = authRepo.currentUserId ?: return
        val moodLevel = when (emotion) {
            DetectedEmotion.HAPPY -> MoodLevel.GREAT
            DetectedEmotion.NEUTRAL -> MoodLevel.OKAY
            DetectedEmotion.TIRED -> MoodLevel.OKAY
            DetectedEmotion.WORRIED -> MoodLevel.NOT_GOOD
            DetectedEmotion.SAD -> MoodLevel.BAD
            DetectedEmotion.PAIN -> MoodLevel.BAD
        }
        viewModelScope.launch {
            medRepo.saveMoodRecord(uid, moodLevel, "Detected via voice assistant")
        }
    }

    fun selectModel(model: String) {
        _state.value = _state.value.copy(selectedModel = model)
        OllamaService.configure(OllamaService.currentUrl(), model)
    }

    fun updateOllamaUrl(context: android.content.Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        OllamaService.saveUrl(context, trimmed)
        _state.value = _state.value.copy(customOllamaUrl = trimmed, isOllamaAvailable = false)
        checkOllama()
    }

    fun clearConversation() {
        saveConversationReport()
        AutoStartManager.pendingTrigger = null
        reportSaved = false
        chatHistory.removeAll { it.role != "system" }
        emotionCounts.clear()
        _state.value = _state.value.copy(
            conversation = emptyList(),
            detectedEmotion = DetectedEmotion.NEUTRAL,
            voiceState = VoiceState.IDLE,
            errorMessage = null,
            alarmTriggerName = ""
        )
    }

    override fun onCleared() {
        super.onCleared()
        saveConversationReport()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
