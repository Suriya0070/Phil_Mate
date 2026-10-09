package com.jsf.app.medication_solution.ui.senior

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.jsf.app.medication_solution.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.service.TtsHelper
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen
import com.jsf.app.medication_solution.ui.theme.MedPalette
import com.jsf.app.medication_solution.ui.theme.StatusAmber
import com.jsf.app.medication_solution.ui.theme.StatusGreen
import com.jsf.app.medication_solution.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorHomeScreen(
    viewModel: SeniorViewModel,
    onMedicationClick: (DoseRecord, Medication) -> Unit,
    onVoiceAssistant: () -> Unit = {},
    onLogout: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val (taken, total) = viewModel.getAdherenceToday()
    val context = LocalContext.current
    val ts = state.textSizePref.scale
    var selectedTab by remember { mutableStateOf(0) }

    // Direct SpeechRecognizer — no dialog, no manual input needed
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    DisposableEffect(Unit) {
        onDispose { speechRecognizer.destroy() }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; partialText = "" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {
                val heard = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (heard.isNotBlank()) partialText = heard
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                partialText = ""
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                val best = texts.firstOrNull() ?: return
                if (best.isNotBlank()) viewModel.confirmDoseByVoice(best)
            }
            override fun onError(error: Int) {
                isListening = false
                partialText = ""
                // On error, retry once with English fallback
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    viewModel.setVoiceError("கேட்கவில்லை — மீண்டும் முயற்சி செய்யவும்")
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening() {
        speechRecognizer.setRecognitionListener(recognitionListener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        speechRecognizer.startListening(intent)
    }

    LaunchedEffect(Unit) {
        viewModel.onUserInteraction()
        viewModel.checkFamilyVoiceNote()
    }

    if (state.showEngagement) {
        EngagementDialog(question = state.engagementQuestion, onDismiss = viewModel::dismissEngagement)
    }
    if (state.showDoubleDoseWarning) {
        DoubleDoseWarningDialog(
            medicationName = state.doubleDoseMedName,
            takenAt = state.doubleDoseTakenAt,
            onDismiss = viewModel::dismissDoubleDoseWarning
        )
    }
    state.successMessage?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); viewModel.clearSuccess() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = if (selectedTab == 0) MedGreen else Color.Gray) },
                    label = { Text("Home", fontSize = (11 * ts).sp, color = if (selectedTab == 0) MedGreen else Color.Gray) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MedGreen.copy(alpha = 0.15f))
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("💊", fontSize = 20.sp) },
                    label = { Text("Pill Vault", fontSize = (11 * ts).sp, color = if (selectedTab == 1) MedGreen else Color.Gray) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MedGreen.copy(alpha = 0.15f))
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("PillMate 💊",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (20 * ts).sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp)
                },
                actions = {
                    if (state.isMonitored) CameraIndicatorDot()
                    TextButton(onClick = { viewModel.cycleTextSize() }) {
                        Text(state.textSizePref.label, color = Color.White,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    TextButton(onClick = { viewModel.resetAndReseed() }) {
                        Text("↺", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onVoiceAssistant) {
                        Icon(Icons.Default.Mic, "Voice", tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedGreen)
            )
        },
        floatingActionButton = {
            val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 0.55f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "p"
            )
            ExtendedFloatingActionButton(
                onClick = {
                    if (state.medications.isEmpty()) {
                        viewModel.resetAndReseed()
                    } else if (isListening) {
                        speechRecognizer.stopListening()
                        isListening = false
                        partialText = ""
                    } else {
                        startListening()
                    }
                },
                icon = {
                    Icon(Icons.Default.Mic, null, tint = Color.White,
                        modifier = if (isListening) Modifier.alpha(pulseAlpha) else Modifier)
                },
                text = {
                    Text(
                        when {
                            state.medications.isEmpty() -> "மருந்துகள் ஏற்று"
                            isListening && partialText.isNotBlank() -> "\"$partialText\""
                            isListening -> "கேட்கிறேன்..."
                            else -> "🎙️ பேசுங்கள்"
                        },
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = (15 * ts.coerceIn(1f, 1.3f)).sp
                    )
                },
                containerColor = if (isListening) Color(0xFFD32F2F) else MedGreen,
                modifier = Modifier.height((56 * ts.coerceIn(1f, 1.3f)).dp)
            )
        }
    ) { padding ->
        if (selectedTab == 1) {
            PillVaultScreen(state = state, ts = ts, modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedGreen, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF0F4F0)).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card with greeting + circular progress + streak
            item { SeniorHeroCard(taken = taken, total = total, user = state.user?.name ?: "", ts = ts, streak = state.adherenceStreak) }

            // SOS Emergency button
            item { SosButton(context = context, onNoPhone = { viewModel.setVoiceError("SOS: Emergency number இல்லை. Caregiver-ஐ கேளுங்கள்.") }) }

            // Success banner
            item {
                AnimatedVisibility(visible = state.successMessage != null, enter = fadeIn() + slideInVertically()) {
                    state.successMessage?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Text(it, modifier = Modifier.padding(16.dp),
                                color = MedGreen, fontWeight = FontWeight.Bold, fontSize = (16 * ts).sp)
                        }
                    }
                }
            }

            // Family voice note
            if (state.hasVoiceNote) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)),
                        shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💌", fontSize = (28 * ts).sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("குடும்பத்தினர் குரல் செய்தி!",
                                    fontWeight = FontWeight.ExtraBold, fontSize = (16 * ts).sp)
                                Text("Voice message from family", fontSize = (12 * ts).sp, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.playFamilyVoiceNote(context) }) {
                                Icon(Icons.Default.PlayArrow, "Play", tint = Color(0xFFE91E63))
                            }
                        }
                    }
                }
            }

            // Smart box
            item {
                SmartMedicineBoxCard(
                    state = state,
                    onStripPicked = viewModel::onMedicineStripPicked,
                    allMedications = state.medications,
                    activeSlotLabel = viewModel.activeSlotLabel()
                )
            }

            // Daily challenge
            state.dailyChallenge?.let { challenge ->
                item {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🧠 தினசரி மூளை பயிற்சி",
                                fontWeight = FontWeight.Bold, color = CareBlue, fontSize = (14 * ts).sp)
                            Spacer(Modifier.height(4.dp))
                            Text(challenge, fontWeight = FontWeight.SemiBold, fontSize = (17 * ts).sp)
                        }
                    }
                }
            }

            // Calendar
            item {
                WeeklyCalendarRow(
                    selectedOffset = state.selectedDayOffset,
                    onDaySelect = viewModel::selectDay,
                    todayDoses = state.todayDoses,
                    medications = state.medications,
                    ts = ts
                )
            }

            // Day label + Week toggle
            item {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, state.selectedDayOffset)
                val dayLabel = when (state.selectedDayOffset) {
                    0 -> "இன்று • $taken/$total எடுத்தீர்கள்"
                    1 -> "நாளை (Tomorrow)"
                    -1 -> "நேற்று (Yesterday)"
                    else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(cal.time)
                }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1B5E20), fontSize = (18 * ts).sp, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = viewModel::toggleWeekGrid,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(if (state.showWeekGrid) "📋 Day" else "📅 Week", fontSize = (12 * ts).sp)
                    }
                }
            }

            if (state.showWeekGrid) {
                item { ThisWeekGrid(medications = state.medications, todayDoses = state.todayDoses, ts = ts) }
            }

            // Medicine tiles
            if (state.medications.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💊", fontSize = (56 * ts).sp)
                            Spacer(Modifier.height(8.dp))
                            Text("மருந்துகள் இல்லை", fontSize = (18 * ts).sp, color = Color.Gray)
                        }
                    }
                }
            } else if (!state.showWeekGrid) {
                item {
                    DayMedicationView(
                        dayOffset = state.selectedDayOffset,
                        todayDoses = state.todayDoses,
                        medications = state.medications,
                        ts = ts,
                        onConfirm = { dose ->
                            viewModel.onUserInteraction()
                            viewModel.confirmDose(dose, null)
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

// ─── Hero Card ────────────────────────────────────────────────────────────────

@Composable
private fun SeniorHeroCard(taken: Int, total: Int, user: String, ts: Float, streak: Int = 0) {
    val progress = if (total == 0) 0f else taken.toFloat() / total.toFloat()
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (h) {
        in 5..11 -> "காலை வணக்கம்! 🌅"
        in 12..16 -> "மதிய வணக்கம்! ☀️"
        in 17..20 -> "மாலை வணக்கம்! 🌆"
        else -> "இரவு வணக்கம்! 🌙"
    }
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), MedGreen)))
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(greeting, fontWeight = FontWeight.ExtraBold,
                        fontSize = (22 * ts).sp, color = Color.White, letterSpacing = 0.3.sp)
                    if (user.isNotBlank())
                        Text(user, fontSize = (15 * ts).sp, color = Color.White.copy(alpha = 0.8f))
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size((76 * ts.coerceIn(1f, 1.3f)).dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 7.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$taken/$total", fontWeight = FontWeight.ExtraBold,
                            fontSize = (17 * ts).sp, color = Color.White)
                        Text("எடுத்தது", fontSize = (9 * ts).sp, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
            if (streak > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.18f)) {
                    Text("🔥 $streak நாள் தொடர்ச்சி! (day streak)",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = (13 * ts).sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                total == 0 -> Text("இன்று மருந்துகள் இல்லை 😊",
                    fontSize = (15 * ts).sp, color = Color.White.copy(alpha = 0.85f))
                taken == total -> Text("🎉 எல்லா மருந்துகளும் எடுத்துவிட்டீர்கள்! Great job!",
                    fontSize = (16 * ts).sp, fontWeight = FontWeight.Bold, color = Color.White)
                else -> {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${total - taken} மருந்து இன்னும் மீதம் (more to take)",
                        fontSize = (13 * ts).sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

// ─── SOS Button ──────────────────────────────────────────────────────────────

@Composable
private fun SosButton(context: android.content.Context, onNoPhone: () -> Unit) {
    val phone = remember {
        context.getSharedPreferences("medicare_prefs", android.content.Context.MODE_PRIVATE)
            .getString("family_phone", "") ?: ""
    }
    Button(
        onClick = {
            if (phone.isNotBlank()) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } else {
                onNoPhone()
            }
        },
        modifier = Modifier.fillMaxWidth().height(58.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
        shape = RoundedCornerShape(18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    ) {
        Text("🚨", fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Text("SOS — உடனடி அழைப்பு",
            fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
    }
}

// ─── Weekly Calendar Strip ────────────────────────────────────────────────────

@Composable
private fun WeeklyCalendarRow(
    selectedOffset: Int,
    onDaySelect: (Int) -> Unit,
    todayDoses: List<DoseRecord>,
    medications: List<Medication>,
    ts: Float
) {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val daysFromMon = ((todayDow - Calendar.MONDAY) + 7) % 7
    val weekDays = (0..6).map { i ->
        val offset = i - daysFromMon
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        Triple(offset, dayNames[i], cal.get(Calendar.DAY_OF_MONTH))
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(weekDays) { (offset, name, num) ->
            val isSelected = offset == selectedOffset
            val isToday = offset == 0
            val dotColor: Color? = when {
                isToday -> when {
                    todayDoses.isEmpty() -> null
                    todayDoses.all { it.isTaken() } -> Color(0xFF4CAF50)
                    todayDoses.any { it.isMissed() } -> Color(0xFFF44336)
                    else -> Color(0xFFFF9800)
                }
                else -> {
                    val weekIdx = ((offset + daysFromMon) % 7 + 7) % 7
                    val hasMeds = medications.any { m -> m.scheduleDays.isEmpty() || m.scheduleDays.contains(weekIdx) }
                    if (hasMeds) Color(0xFFB0BEC5) else null
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when { isSelected -> MedGreen; isToday -> MedGreen.copy(alpha = 0.12f); else -> Color.White },
                shadowElevation = if (isSelected) 4.dp else 1.dp,
                modifier = Modifier.width((52 * ts.coerceIn(1f, 1.35f)).dp).clickable { onDaySelect(offset) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(name, fontSize = (10 * ts).sp,
                        color = if (isSelected) Color.White else if (isToday) MedGreen else Color.Gray,
                        fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Normal)
                    Text("$num", fontSize = (18 * ts).sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) Color.White else Color(0xFF1A2E1A))
                    Box(Modifier.size(6.dp).clip(CircleShape)
                        .background(when { dotColor == null -> Color.Transparent; isSelected -> Color.White; else -> dotColor }))
                }
            }
        }
    }
}

// ─── This Week Grid ───────────────────────────────────────────────────────────

@Composable
private fun ThisWeekGrid(medications: List<Medication>, todayDoses: List<DoseRecord>, ts: Float) {
    data class SlotInfo(val label: String, val emoji: String, val range: IntRange)
    val slots = listOf(
        SlotInfo("Morning", "🌅", 6..11), SlotInfo("Afternoon", "☀️", 12..16),
        SlotInfo("Evening", "🌆", 17..20), SlotInfo("Night", "🌙", 21..29)
    )
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val daysFromMon = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY) + 7) % 7

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("📅 இந்த வாரம் (This Week)", fontWeight = FontWeight.Bold,
                color = MedGreen, fontSize = (15 * ts).sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(56.dp))
                dayNames.forEachIndexed { i, name ->
                    val isToday = (i - daysFromMon) == 0
                    Text(name, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = (9 * ts).sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isToday) MedGreen else Color.Gray)
                }
            }
            Spacer(Modifier.height(4.dp))
            slots.forEach { slot ->
                val slotMeds = medications.filter { med ->
                    med.scheduleTimes.any { t ->
                        val h2 = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                        (if (h2 < 6) h2 + 24 else h2) in slot.range
                    }
                }
                if (slotMeds.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${slot.emoji} ${slot.label}", modifier = Modifier.width(56.dp),
                            fontSize = (8 * ts).sp, color = Color.Gray, maxLines = 1)
                        dayNames.forEachIndexed { dayIdx, _ ->
                            val offset = dayIdx - daysFromMon
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                val dayMeds = slotMeds.filter { med ->
                                    med.scheduleDays.isEmpty() || med.scheduleDays.contains(dayIdx)
                                }
                                if (dayMeds.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                                        dayMeds.take(2).forEach { med ->
                                            val c = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                                            val taken = offset == 0 && todayDoses.any { it.medicationId == med.id && it.isTaken() }
                                            Box(Modifier.size((9 * ts).dp).clip(CircleShape).background(if (taken) c else c.copy(alpha = 0.4f)))
                                        }
                                    }
                                } else Text("·", fontSize = (10 * ts).sp, color = Color(0xFFDDDDDD))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                medications.take(4).forEach { med ->
                    val color = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size((9 * ts).dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(3.dp))
                        Text(med.name.split(" ").first(), fontSize = (9 * ts).sp, color = Color.Gray, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ─── Tablet Shape Icon ────────────────────────────────────────────────────────

@Composable
fun TabletShapeIcon(color: Color, shape: String, size: Dp = 52.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 4.dp)) {
        when (shape) {
            "oval" -> Box(Modifier.height(size * 0.65f).width(size * 1.3f).clip(RoundedCornerShape(50)).background(color))
            "capsule" -> Box(Modifier.height(size * 0.5f).width(size * 1.55f).clip(RoundedCornerShape(50))) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                    Box(Modifier.weight(1f).fillMaxHeight().background(color.copy(alpha = 0.45f)))
                }
            }
            "square" -> Box(Modifier.size(size * 0.78f).clip(RoundedCornerShape(8.dp)).background(color))
            else -> Box(Modifier.size(size).clip(CircleShape).background(color))
        }
    }
}

// ─── Day Medication View → 2-Column Tile Grid ────────────────────────────────

@Composable
private fun DayMedicationView(
    dayOffset: Int,
    todayDoses: List<DoseRecord>,
    medications: List<Medication>,
    ts: Float,
    onConfirm: (DoseRecord) -> Unit
) {
    val isToday = dayOffset == 0
    data class TimeSlot(val label: String, val emoji: String, val range: IntRange)
    val slots = listOf(
        TimeSlot("காலை (Morning)", "🌅", 6..11),
        TimeSlot("மதியம் (Afternoon)", "☀️", 12..16),
        TimeSlot("மாலை (Evening)", "🌆", 17..20),
        TimeSlot("இரவு (Night)", "🌙", 21..29)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (slot in slots) {
            if (isToday) {
                val slotDoses = todayDoses.filter { dose ->
                    val cal = Calendar.getInstance(); cal.timeInMillis = dose.scheduledTime
                    val h = cal.get(Calendar.HOUR_OF_DAY)
                    (if (h < 6) h + 24 else h) in slot.range
                }
                if (slotDoses.isNotEmpty()) {
                    TimeSlotHeader(label = slot.label, emoji = slot.emoji, count = slotDoses.size, ts = ts)
                    MedicineTileGrid(doses = slotDoses, medications = medications, ts = ts, onConfirm = onConfirm)
                }
            } else {
                val slotMeds = medications.filter { med ->
                    med.scheduleTimes.any { t ->
                        val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                        (if (h < 6) h + 24 else h) in slot.range
                    }
                }
                if (slotMeds.isNotEmpty()) {
                    TimeSlotHeader(label = slot.label, emoji = slot.emoji, count = slotMeds.size, ts = ts)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slotMeds.forEach { med ->
                            val times = med.scheduleTimes.filter { t ->
                                val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                                (if (h < 6) h + 24 else h) in slot.range
                            }
                            ScheduledMedicineRow(medication = med, times = times, ts = ts)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotHeader(label: String, emoji: String, count: Int, ts: Float) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B5E20).copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = (22 * ts).sp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.ExtraBold,
            fontSize = (16 * ts).sp, color = Color(0xFF1B5E20), modifier = Modifier.weight(1f))
        Text("$count", fontWeight = FontWeight.ExtraBold,
            fontSize = (18 * ts).sp, color = MedGreen)
    }
}

// ─── 2-Column Medicine Tile Grid ─────────────────────────────────────────────

@Composable
private fun MedicineTileGrid(
    doses: List<DoseRecord>,
    medications: List<Medication>,
    ts: Float,
    onConfirm: (DoseRecord) -> Unit
) {
    val rows = doses.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { dose ->
                    MedicineTile(
                        dose = dose,
                        medication = medications.find { it.id == dose.medicationId },
                        ts = ts,
                        modifier = Modifier.weight(1f),
                        onConfirm = { onConfirm(dose) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MedicineTile(
    dose: DoseRecord,
    medication: Medication?,
    ts: Float,
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit
) {
    val context  = LocalContext.current
    val medColor = MedPalette.colorForMedication(medication?.pillColorHex ?: "#4CAF50", medication?.colorIndex ?: -1)
    val isTaken = dose.isTaken()
    val isMissed = dose.isMissed()
    val isOverdue = dose.isOverdue()
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var expandedPhotoIdx by remember { mutableStateOf(-1) }

    val cardBg = when {
        isMissed -> Color(0xFFFFF0F0)
        isTaken  -> Color(0xFFF8F8F8)
        else     -> Color.White
    }
    val borderColor = when {
        isMissed -> Color(0xFFEF9A9A)
        isTaken  -> medColor.copy(alpha = 0.35f)
        else     -> medColor
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(if (isTaken || isMissed) 1.dp else 4.dp),
        border = BorderStroke(2.dp, borderColor)
    ) {
        // Colored top accent strip
        Box(Modifier.fillMaxWidth().height(5.dp).background(borderColor))
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                if (medication?.imagePath?.isNotBlank() == true) {
                    AsyncImage(model = medication.imagePath, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size((36 * ts.coerceIn(1f, 1.3f)).dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    TabletShapeIcon(
                        color = medColor.copy(alpha = if (isTaken || isMissed) 0.3f else 1f),
                        shape = medication?.shape ?: "circle",
                        size = (32 * ts.coerceIn(1f, 1.3f)).dp
                    )
                }
                Text(when { isTaken -> "✅"; isMissed -> "❌"; isOverdue -> "⏰"; else -> "" },
                    fontSize = (20 * ts).sp)
            }

            Text(dose.medicationName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (21 * ts).sp,
                color = if (isTaken || isMissed) Color(0xFF757575) else medColor,
                maxLines = 2)

            if (medication?.purpose?.isNotBlank() == true) {
                Text(medication.purpose.substringBefore("(").trim(),
                    fontSize = (12 * ts).sp,
                    color = Color(0xFF546E7A),
                    maxLines = 2)
            }

            Text(timeFmt.format(Date(dose.scheduledTime)),
                fontSize = (12 * ts).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF90A4AE))

            Spacer(Modifier.height(4.dp))

            // Tablet photo thumbnails — real photos for known medicines
            val medName = dose.medicationName.lowercase()
            val photoRes: List<Pair<String, Int?>> = when {
                medName.contains("dolo") -> listOf("Front" to R.drawable.med_dolo_front, "Back" to R.drawable.med_dolo_back)
                medName.contains("pan")  -> listOf("Front" to R.drawable.med_pan_front,  "Back" to R.drawable.med_pan_back)
                medName.contains("telma") -> listOf("Front" to R.drawable.med_telma_front, "Back" to R.drawable.med_telma_back)
                else -> listOf("Front" to null, "Back" to null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                photoRes.forEachIndexed { idx, (label, resId) ->
                    val isSelected = expandedPhotoIdx == idx
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected)
                            Color.White.copy(alpha = if (isTaken || isMissed) 0.3f else 0.45f)
                        else
                            Color.White.copy(alpha = if (isTaken || isMissed) 0.1f else 0.2f),
                        modifier = Modifier.weight(1f).clickable { expandedPhotoIdx = if (isSelected) -1 else idx }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                            if (resId != null) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(resId),
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size((30 * ts.coerceIn(1f, 1.3f)).dp).clip(RoundedCornerShape(4.dp))
                                        .alpha(if (isTaken || isMissed) 0.45f else 1f)
                                )
                            } else {
                                TabletShapeIcon(
                                    color = if (isTaken || isMissed) medColor.copy(alpha = 0.3f) else medColor.copy(alpha = 0.85f),
                                    shape = medication?.shape ?: "circle",
                                    size = (18 * ts.coerceIn(1f, 1.3f)).dp
                                )
                            }
                            Text(label, fontSize = (7 * ts).sp,
                                color = Color(0xFF90A4AE),
                                textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
            AnimatedVisibility(visible = expandedPhotoIdx >= 0) {
                val safeIdx = expandedPhotoIdx.coerceAtLeast(0)
                val (exLabel, exResId) = photoRes[safeIdx]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTaken || isMissed) medColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
                        if (exResId != null) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(exResId),
                                contentDescription = exLabel,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((120 * ts.coerceIn(1f, 1.3f)).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .alpha(if (isTaken || isMissed) 0.55f else 1f)
                            )
                        } else {
                            TabletShapeIcon(
                                color = if (isTaken || isMissed) medColor.copy(alpha = 0.3f) else medColor,
                                shape = medication?.shape ?: "circle",
                                size = (52 * ts.coerceIn(1f, 1.3f)).dp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$exLabel", fontSize = (11 * ts).sp,
                            color = medColor,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!isTaken && !isMissed) {
                Button(
                    onClick = {
                        TtsHelper.speak(context, "${dose.medicationName} எடுத்துவிட்டீர்கள்!")
                        onConfirm()
                    },
                    modifier = Modifier.fillMaxWidth().height((52 * ts.coerceIn(1f, 1.3f)).dp),
                    colors = ButtonDefaults.buttonColors(containerColor = medColor),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text("✅ எடுத்தேன்!", fontSize = (15 * ts).sp,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (isTaken) medColor.copy(alpha = 0.1f) else Color(0xFFFFEBEE))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isTaken) "எடுத்துவிட்டீர்கள்" else "தவறிவிட்டீர்கள்",
                        fontSize = (12 * ts).sp, fontWeight = FontWeight.Bold,
                        color = if (isTaken) medColor else Color(0xFFD32F2F))
                }
            }
        }
    }
}

@Composable
private fun ScheduledMedicineRow(medication: Medication, times: List<String>, ts: Float) {
    val medColor = MedPalette.colorForMedication(medication.pillColorHex, medication.colorIndex)
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TabletShapeIcon(color = medColor, shape = medication.shape, size = (44 * ts.coerceIn(1f, 1.4f)).dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, fontWeight = FontWeight.ExtraBold,
                    fontSize = (17 * ts).sp, color = Color(0xFF1B5E20))
                if (medication.purpose.isNotBlank())
                    Text(medication.purpose.substringBefore("(").trim(),
                        fontSize = (13 * ts).sp, color = Color(0xFF546E7A))
                Text("${medication.dosage} • ${times.joinToString(", ")}",
                    fontSize = (12 * ts).sp, color = Color.Gray)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = medColor.copy(alpha = 0.12f)) {
                Text("📅", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 18.sp)
            }
        }
    }
}

// ─── Smart Box Card ───────────────────────────────────────────────────────────

@Composable
private fun SmartMedicineBoxCard(state: SeniorUiState, allMedications: List<Medication>, onStripPicked: (String) -> Unit, activeSlotLabel: String = "") {
    val context = LocalContext.current
    val ledColor = when (state.boxLedStatus) { LedStatus.GREEN -> Color(0xFF4CAF50); LedStatus.RED -> Color(0xFFF44336); LedStatus.NONE -> Color(0xFF9E9E9E) }
    val ledAlpha by rememberInfiniteTransition(label = "led").animateFloat(0.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    var checkingMedId by remember { mutableStateOf<String?>(null) }
    val stripPulse by rememberInfiniteTransition(label = "strip").animateFloat(0.55f, 1f, infiniteRepeatable(tween(280), RepeatMode.Reverse), label = "sp")
    val stripScale by rememberInfiniteTransition(label = "sc").animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(280), RepeatMode.Reverse), label = "scv")

    LaunchedEffect(state.boxLedStatus) {
        if (state.boxLedStatus == LedStatus.RED) {
            val v = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                (context.getSystemService(android.os.VibratorManager::class.java))?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 300), -1))
        }
    }

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
        shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💊 Smart Medicine Box", fontWeight = FontWeight.Bold,
                    color = Color.White, modifier = Modifier.weight(1f), fontSize = 15.sp)
                Box(Modifier.size(12.dp).alpha(if (state.boxLedStatus != LedStatus.NONE) ledAlpha else 1f).clip(CircleShape).background(ledColor))
                Spacer(Modifier.width(6.dp))
                Text(if (state.bandConnected) "Band ●" else "Band ○", fontSize = 11.sp,
                    color = if (state.bandConnected) Color(0xFF69F0AE) else Color.Gray)
            }
            Text("Next up: ${activeSlotLabel.ifBlank { "🎉 All Done Today!" }}  •  Tap any strip to confirm", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            if (state.boxLedMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = ledColor.copy(alpha = 0.25f)) {
                    Text(state.boxLedMessage, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            if (state.cameraMonitoringActive || state.intakeDetected != null) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.4f)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📷", fontSize = 18.sp); Spacer(Modifier.width(8.dp))
                        when {
                            state.cameraMonitoringActive -> {
                                val d by rememberInfiniteTransition(label = "d").animateFloat(0f, 3f, infiniteRepeatable(tween(900)), label = "d2")
                                Text("Monitoring" + ".".repeat(d.toInt() + 1), fontSize = 13.sp, color = Color.White)
                            }
                            state.intakeDetected == true -> Text("✅ Detected!", fontSize = 13.sp, color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                            else -> Text("⚠️ Not detected", fontSize = 13.sp, color = Color(0xFFFFD740))
                        }
                    }
                }
            }
            if (allMedications.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("ALL MEDICINES — always available:", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), letterSpacing = 0.5.sp)
                Spacer(Modifier.height(6.dp))
                // Show in rows of 4
                allMedications.chunked(4).forEach { rowMeds ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    rowMeds.forEach { med ->
                        val c = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                        val doseRecord = state.todayDoses.find { it.medicationId == med.id }
                        val taken = doseRecord?.isTaken() == true
                        val missed = doseRecord?.isMissed() == true
                        val isChecking = checkingMedId == med.id && !taken
                        Surface(shape = RoundedCornerShape(10.dp),
                            color = when {
                                taken   -> Color.Gray.copy(alpha = 0.35f)
                                missed  -> Color(0xFFD32F2F).copy(alpha = 0.7f)
                                else    -> c.copy(alpha = if (isChecking) stripPulse else 0.9f)
                            },
                            modifier = Modifier.weight(1f)
                                .scale(if (isChecking) stripScale else 1f)
                                .clickable {
                                    if (!taken) {
                                        checkingMedId = med.id
                                        onStripPicked(med.id)
                                    }
                                }) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(med.pillEmoji, fontSize = 20.sp)
                                Text(med.name.split(" ").first(), fontSize = 9.sp, color = Color.White,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                                Text(
                                    when { isChecking -> "⏳"; taken -> "✅"; missed -> "❌"; else -> "" },
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    if (rowMeds.size < 4) repeat(4 - rowMeds.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─── Public composables ───────────────────────────────────────────────────────

@Composable
fun MedicationCard(medication: Medication, doseRecord: DoseRecord?, onClick: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val status = doseRecord?.doseStatus() ?: DoseStatus.PENDING
    val isOverdue = doseRecord?.isOverdue() == true
    val (statusColor, statusText, statusBg) = when {
        status == DoseStatus.TAKEN -> Triple(StatusGreen, "✓ Taken ${timeFmt.format(Date(doseRecord!!.takenAt))}", Color(0xFFE8F5E9))
        status == DoseStatus.MISSED -> Triple(StatusRed, "✗ Missed", Color(0xFFFFEBEE))
        isOverdue -> Triple(StatusAmber, "⏰ Overdue!", Color(0xFFFFF8E1))
        doseRecord != null -> Triple(MedGreen, "Due ${timeFmt.format(Date(doseRecord.scheduledTime))}", Color.White)
        else -> Triple(Color.Gray, "Not scheduled today", Color.White)
    }
    val medColor = MedPalette.colorForMedication(medication.pillColorHex, medication.colorIndex)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        elevation = CardDefaults.cardElevation(3.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TabletShapeIcon(color = medColor, shape = medication.shape, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, color = Color(0xFF1A2E1A))
                Text(medication.purpose, fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun EngagementDialog(question: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👋 வணக்கம்!", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(question, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("நீங்கள் எப்படி இருக்கிறீர்கள்?", fontSize = 14.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("நான் நலமாக இருக்கிறேன்! 😊", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("பிறகு", color = Color.Gray) } }
    )
}

@Composable
fun DoubleDoseWarningDialog(medicationName: String, takenAt: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛡️ ஏற்கனவே எடுத்தீர்கள்!", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFB71C1C)) },
        text = { Text("$medicationName ஐ $takenAt ல் எடுத்துவிட்டீர்கள்.\n\nமீண்டும் எடுக்காதீர்கள். (Do not take again — it could be harmful.)", fontSize = 16.sp) },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("சரி, புரிந்தது (OK)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    )
}

@Composable
private fun CameraIndicatorDot() {
    val a by rememberInfiniteTransition(label = "cam").animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier = Modifier.size(10.dp).alpha(a).clip(CircleShape).background(Color(0xFF4CAF50)))
}

// ─── Pill Vault Screen ────────────────────────────────────────────────────────

@Composable
private fun PillVaultScreen(state: SeniorUiState, ts: Float, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.background(Color(0xFFF0F4F0)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("💊 Pill Vault", fontWeight = FontWeight.ExtraBold,
                fontSize = (22 * ts).sp, color = Color(0xFF1B5E20))
            Text("Your medicine & mood dashboard", fontSize = (13 * ts).sp, color = Color.Gray)
        }

        // Medicine Stock Tracker
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(3.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📦 Medicine Stock", fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20), fontSize = (16 * ts).sp)
                    Spacer(Modifier.height(12.dp))
                    if (state.medications.isEmpty()) {
                        Text("No medicines yet", color = Color.Gray, fontSize = (14 * ts).sp)
                    } else {
                        state.medications.forEachIndexed { i, med ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            MedicineStockRow(med = med, ts = ts)
                        }
                    }
                }
            }
        }

        // Emotional Journey
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)),
                shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(3.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💭 Emotional Journey", fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828), fontSize = (16 * ts).sp)
                    Spacer(Modifier.height(8.dp))
                    if (state.moodHistory.isEmpty()) {
                        Text("Confirm a dose to start tracking mood",
                            color = Color.Gray, fontSize = (13 * ts).sp)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.moodHistory.take(7).forEach { record ->
                                val ml = record.moodLevel()
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(ml.emoji, fontSize = (24 * ts).sp)
                                    Text(ml.label.take(4), fontSize = (8 * ts).sp, color = Color.Gray)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val avgScore = state.moodHistory.take(7).map { it.moodLevel().score }.average()
                        val summary = when {
                            avgScore >= 4.0 -> "😊 Great week! Feeling positive overall."
                            avgScore >= 3.0 -> "🙂 A decent week. Keep it up!"
                            else -> "💛 Tough week. Caregiver has been notified."
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFCDD2)) {
                            Text(summary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = (13 * ts).sp, color = Color(0xFF880E4F), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Vault Concept Card (Hardware Vision)
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
                shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("🔬 Smart Pill Vault", fontWeight = FontWeight.ExtraBold,
                        color = Color.White, fontSize = (18 * ts).sp)
                    Text("Hardware Vision — Coming Soon", color = Color.White.copy(alpha = 0.6f),
                        fontSize = (12 * ts).sp)
                    Spacer(Modifier.height(14.dp))
                    listOf(
                        "🗃️" to "Smart dispenser tray — medicine auto-drops at the right time",
                        "📷" to "Camera checks if you actually take the pill",
                        "😊" to "AI detects your emotion before & after each dose",
                        "📊" to "Full report sent to caregiver in real-time"
                    ).forEach { (emoji, text) ->
                        Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Text(emoji, fontSize = (20 * ts).sp)
                            Spacer(Modifier.width(10.dp))
                            Text(text, fontSize = (13 * ts).sp, color = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.15f)) {
                        Text("🛠️ HACKNEXT'26 Innovation — Patent Pending",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            color = Color.White, fontSize = (11 * ts).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun MedicineStockRow(med: Medication, ts: Float) {
    val color = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
    val maxPills = 30
    val progress = (med.remainingPills.toFloat() / maxPills.toFloat()).coerceIn(0f, 1f)
    val dosesPerDay = med.scheduleTimes.size.coerceAtLeast(1)
    val daysLeft = med.remainingPills / dosesPerDay
    val isLow = daysLeft <= 7
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabletShapeIcon(color = color, shape = med.shape, size = (28 * ts.coerceIn(1f, 1.3f)).dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(med.name, fontWeight = FontWeight.Bold,
                        fontSize = (14 * ts).sp, color = Color(0xFF1B5E20))
                    if (isLow) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFEBEE)) {
                            Text("⚠️ Refill Soon",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color(0xFFD32F2F), fontSize = (9 * ts).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("${med.remainingPills} pills • ~$daysLeft days left",
                    fontSize = (11 * ts).sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (isLow) Color(0xFFD32F2F) else color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}
