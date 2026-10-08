package com.jsf.app.medication_solution.ui.senior

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (text.isNotBlank()) viewModel.confirmDoseByVoice(text)
        }
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
            ExtendedFloatingActionButton(
                onClick = {
                    if (state.medications.isEmpty()) {
                        viewModel.resetAndReseed()
                    } else {
                        speechLauncher.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "மருந்து பெயர் சொல்லுங்கள்")
                            }
                        )
                    }
                },
                icon = { Icon(Icons.Default.Mic, null, tint = Color.White) },
                text = {
                    Text(
                        if (state.medications.isEmpty()) "மருந்துகள் ஏற்று" else "🎙️ குரலில் உறுதி செய்",
                        color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = (14 * ts.coerceIn(1f, 1.3f)).sp
                    )
                },
                containerColor = MedGreen,
                modifier = Modifier.height((56 * ts.coerceIn(1f, 1.3f)).dp)
            )
        }
    ) { padding ->
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
            // Hero card with greeting + circular progress
            item { SeniorHeroCard(taken = taken, total = total, user = state.user?.name ?: "", ts = ts) }

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
                    currentSlotMeds = viewModel.getCurrentSlotMeds()
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
private fun SeniorHeroCard(taken: Int, total: Int, user: String, ts: Float) {
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
    val medColor = MedPalette.colorForMedication(medication?.pillColorHex ?: "#4CAF50", medication?.colorIndex ?: -1)
    val textColor = MedPalette.contrastTextColor(medColor)
    val isTaken = dose.isTaken()
    val isMissed = dose.isMissed()
    val isOverdue = dose.isOverdue()
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = when {
            isTaken -> Color(0xFFF5F5F5)
            isMissed -> Color(0xFFFFEBEE)
            else -> medColor
        }),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(if (isTaken || isMissed) 0.dp else 5.dp)
    ) {
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
                        color = if (isTaken || isMissed) medColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.3f),
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
                color = if (isTaken || isMissed) medColor else textColor,
                maxLines = 2)

            if (medication?.purpose?.isNotBlank() == true) {
                Text(medication.purpose.substringBefore("(").trim(),
                    fontSize = (12 * ts).sp,
                    color = if (isTaken || isMissed) Color.Gray else textColor.copy(alpha = 0.82f),
                    maxLines = 2)
            }

            Text(timeFmt.format(Date(dose.scheduledTime)),
                fontSize = (12 * ts).sp,
                fontWeight = FontWeight.Medium,
                color = if (isTaken || isMissed) Color.Gray else textColor.copy(alpha = 0.75f))

            Spacer(Modifier.height(2.dp))

            if (!isTaken && !isMissed) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height((52 * ts.coerceIn(1f, 1.3f)).dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.28f)),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(4.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("✅ எடுத்தேன்!", fontSize = (15 * ts).sp,
                        fontWeight = FontWeight.ExtraBold, color = textColor)
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
                        color = if (isTaken) medColor else Color.Red)
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
private fun SmartMedicineBoxCard(state: SeniorUiState, currentSlotMeds: List<Medication>, onStripPicked: (String) -> Unit) {
    val context = LocalContext.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val slotName = when (hour) { in 6..11 -> "🌅 Morning"; in 12..16 -> "☀️ Afternoon"; in 17..20 -> "🌆 Evening"; else -> "🌙 Night" }
    val ledColor = when (state.boxLedStatus) { LedStatus.GREEN -> Color(0xFF4CAF50); LedStatus.RED -> Color(0xFFF44336); LedStatus.NONE -> Color(0xFF9E9E9E) }
    val ledAlpha by rememberInfiniteTransition(label = "led").animateFloat(0.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")

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
            Text("Current: $slotName", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
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
            if (currentSlotMeds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Tap your strip:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currentSlotMeds.forEach { med ->
                        val c = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                        val taken = state.todayDoses.find { it.medicationId == med.id }?.isTaken() == true
                        Surface(shape = RoundedCornerShape(14.dp),
                            color = if (taken) Color.Gray.copy(alpha = 0.4f) else c.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f).clickable(enabled = !taken) { onStripPicked(med.id) }) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(med.pillEmoji, fontSize = 26.sp)
                                Text(med.name.split(" ").first(), fontSize = 11.sp, color = Color.White,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                                if (taken) Text("✅", fontSize = 12.sp)
                            }
                        }
                    }
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
