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

    val greeting = run {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (h) {
            in 5..11 -> "காலை வணக்கம் 🌅"
            in 12..16 -> "மதிய வணக்கம் ☀️"
            in 17..20 -> "மாலை வணக்கம் 🌆"
            else -> "இரவு வணக்கம் 🌙"
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
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = (16 * ts).sp
                        )
                        Text(
                            state.user?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = (12 * ts).sp
                        )
                    }
                },
                actions = {
                    if (state.isMonitored) CameraIndicatorDot()
                    // Text size toggle
                    TextButton(onClick = { viewModel.cycleTextSize() }) {
                        Text(
                            state.textSizePref.label,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (15 * ts).sp
                        )
                    }
                    IconButton(onClick = onVoiceAssistant) {
                        Icon(Icons.Default.Mic, "Voice Assistant", tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedGreen, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            if (state.medications.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.seedDemoMedications() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("மருந்துகள் ஏற்று", fontSize = (13 * ts).sp) },
                    containerColor = MedGreen,
                    contentColor = Color.White
                )
            } else {
                FloatingActionButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "நான் [மருந்து] போட்டுட்டேன் என்று சொல்லுங்கள்")
                        }
                        speechLauncher.launch(intent)
                    },
                    containerColor = MedGreen,
                    modifier = Modifier.size((64 * ts.coerceIn(1f, 1.4f)).dp)
                ) {
                    Icon(Icons.Default.Mic, "Quick voice confirm", tint = Color.White,
                        modifier = Modifier.size((28 * ts.coerceIn(1f, 1.4f)).dp))
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MedGreen)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F8E9))
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SummaryCard(taken = taken, total = total, ts = ts) }

            item {
                SmartMedicineBoxCard(
                    state = state,
                    onStripPicked = { medId -> viewModel.onMedicineStripPicked(medId) },
                    currentSlotMeds = viewModel.getCurrentSlotMeds()
                )
            }

            if (state.hasVoiceNote) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💌", fontSize = (28 * ts).sp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("குடும்பத்தினரிடம் இருந்து குரல் செய்தி!",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = (14 * ts).sp)
                                Text("(Voice message from your family!)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray, fontSize = (11 * ts).sp)
                            }
                            IconButton(onClick = { viewModel.playFamilyVoiceNote(context) }) {
                                Icon(Icons.Default.PlayArrow, "Play", tint = CareBlue)
                            }
                        }
                    }
                }
            }

            state.dailyChallenge?.let { challenge ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "🧠 தினசரி மூளை பயிற்சி (Daily Brain Exercise)",
                                style = MaterialTheme.typography.labelLarge,
                                color = CareBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13 * ts).sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(challenge, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold, fontSize = (16 * ts).sp)
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = state.successMessage != null, enter = fadeIn() + slideInVertically()) {
                    state.successMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it, modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MedGreen, fontWeight = FontWeight.SemiBold,
                                fontSize = (16 * ts).sp
                            )
                        }
                    }
                }
            }

            // Full Mon-Sun calendar strip
            item {
                WeeklyCalendarRow(
                    selectedOffset = state.selectedDayOffset,
                    onDaySelect = viewModel::selectDay,
                    todayDoses = state.todayDoses,
                    medications = state.medications,
                    ts = ts
                )
            }

            // Day label + This Week Grid toggle
            item {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, state.selectedDayOffset)
                val dayLabel = when (state.selectedDayOffset) {
                    0 -> "இன்று (Today) — $taken/$total எடுத்தீர்கள்"
                    1 -> "நாளை (Tomorrow)"
                    -1 -> "நேற்று (Yesterday)"
                    else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(cal.time)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MedGreen,
                        fontSize = (17 * ts).sp,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { viewModel.toggleWeekGrid() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            if (state.showWeekGrid) "📋 Day" else "📅 Week",
                            fontSize = (12 * ts).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // This Week Grid (toggleable)
            if (state.showWeekGrid) {
                item {
                    ThisWeekGrid(
                        medications = state.medications,
                        todayDoses = state.todayDoses,
                        ts = ts
                    )
                }
            }

            if (state.medications.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("💊", fontSize = (48 * ts).sp)
                            Spacer(Modifier.height(8.dp))
                            Text("மருந்துகள் இல்லை", style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray, fontSize = (18 * ts).sp)
                            Text("(No medicines yet)", style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray, fontSize = (14 * ts).sp)
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
                            onMedicationClick(
                                dose,
                                state.medications.find { it.id == dose.medicationId } ?: return@DayMedicationView
                            )
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

// ─── Weekly Mon-Sun Calendar Strip ───────────────────────────────────────────

@Composable
private fun WeeklyCalendarRow(
    selectedOffset: Int,
    onDaySelect: (Int) -> Unit,
    todayDoses: List<DoseRecord>,
    medications: List<Medication>,
    ts: Float
) {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val today = Calendar.getInstance()
    val todayDow = today.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
    val daysFromMon = ((todayDow - Calendar.MONDAY) + 7) % 7

    val weekDays = (0..6).map { i ->
        val offset = i - daysFromMon
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        Triple(offset, dayNames[i], cal.get(Calendar.DAY_OF_MONTH))
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
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
                offset < 0 -> {
                    val weekIdx = (offset + daysFromMon + 7) % 7
                    val hasMeds = medications.any { m ->
                        m.scheduleDays.isEmpty() || m.scheduleDays.contains(weekIdx)
                    }
                    if (hasMeds) Color(0xFF90A4AE) else null
                }
                else -> {
                    val weekIdx = (offset + daysFromMon) % 7
                    val hasMeds = medications.any { m ->
                        m.scheduleDays.isEmpty() || m.scheduleDays.contains(weekIdx)
                    }
                    if (hasMeds) Color(0xFFB0BEC5) else null
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isSelected -> MedGreen
                    isToday -> MedGreen.copy(alpha = 0.12f)
                    else -> Color.White
                },
                shadowElevation = if (isSelected) 4.dp else 1.dp,
                modifier = Modifier
                    .width((50 * ts.coerceIn(1f, 1.35f)).dp)
                    .clickable { onDaySelect(offset) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        name,
                        fontSize = (10 * ts).sp,
                        color = if (isSelected) Color.White else if (isToday) MedGreen else Color.Gray,
                        fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Normal
                    )
                    Text(
                        "$num",
                        fontSize = (17 * ts).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) Color.White else Color(0xFF1A2E1A)
                    )
                    Box(
                        Modifier.size(6.dp).clip(CircleShape).background(
                            when {
                                dotColor == null -> Color.Transparent
                                isSelected -> Color.White
                                else -> dotColor
                            }
                        )
                    )
                }
            }
        }
    }
}

// ─── This Week Grid ───────────────────────────────────────────────────────────

@Composable
private fun ThisWeekGrid(
    medications: List<Medication>,
    todayDoses: List<DoseRecord>,
    ts: Float
) {
    data class SlotInfo(val label: String, val emoji: String, val range: IntRange)
    val slots = listOf(
        SlotInfo("Morning", "🌅", 6..11),
        SlotInfo("Afternoon", "☀️", 12..16),
        SlotInfo("Evening", "🌆", 17..20),
        SlotInfo("Night", "🌙", 21..29)
    )
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val today = Calendar.getInstance()
    val daysFromMon = ((today.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY) + 7) % 7

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "📅 இந்த வாரம் (This Week)",
                fontWeight = FontWeight.Bold,
                color = MedGreen,
                fontSize = (15 * ts).sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(56.dp))
                dayNames.forEachIndexed { i, name ->
                    val offset = i - daysFromMon
                    val isToday = offset == 0
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = (9 * ts).sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isToday) MedGreen else Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            slots.forEach { slot ->
                val slotMeds = medications.filter { med ->
                    med.scheduleTimes.any { t ->
                        val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                        val mapped = if (h < 6) h + 24 else h
                        mapped in slot.range
                    }
                }
                if (slotMeds.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${slot.emoji} ${slot.label}",
                            modifier = Modifier.width(56.dp),
                            fontSize = (8 * ts).sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
                        dayNames.forEachIndexed { dayIdx, _ ->
                            val offset = dayIdx - daysFromMon
                            val weekDayIdx = dayIdx
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                val dayMeds = slotMeds.filter { med ->
                                    med.scheduleDays.isEmpty() || med.scheduleDays.contains(weekDayIdx)
                                }
                                if (dayMeds.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        dayMeds.take(2).forEach { med ->
                                            val medColor = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                                            val isTakenToday = offset == 0 &&
                                                todayDoses.any { it.medicationId == med.id && it.isTaken() }
                                            Box(
                                                Modifier
                                                    .size((9 * ts).dp)
                                                    .clip(CircleShape)
                                                    .background(if (isTakenToday) medColor else medColor.copy(alpha = 0.45f))
                                            )
                                        }
                                        if (dayMeds.size > 2) {
                                            Text("+", fontSize = (7 * ts).sp, color = Color.Gray)
                                        }
                                    }
                                } else {
                                    Text("·", fontSize = (10 * ts).sp, color = Color(0xFFDDDDDD))
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFEEEEEE)))
                }
            }

            // Colour legend
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                medications.take(4).forEach { med ->
                    val color = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size((9 * ts).dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            med.name.split(" ").first(),
                            fontSize = (9 * ts).sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
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
            "oval" -> Box(
                Modifier
                    .height(size * 0.65f)
                    .width(size * 1.3f)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            "capsule" -> Box(
                Modifier
                    .height(size * 0.5f)
                    .width(size * 1.55f)
                    .clip(RoundedCornerShape(50))
            ) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                    Box(Modifier.weight(1f).fillMaxHeight().background(color.copy(alpha = 0.45f)))
                }
            }
            "square" -> Box(
                Modifier
                    .size(size * 0.78f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            )
            else -> Box(  // circle
                Modifier.size(size).clip(CircleShape).background(color)
            )
        }
    }
}

// ─── Day Medication View ──────────────────────────────────────────────────────

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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (slot in slots) {
            if (isToday) {
                val slotDoses = todayDoses.filter { dose ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = dose.scheduledTime
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val mapped = if (hour < 6) hour + 24 else hour
                    mapped in slot.range
                }
                if (slotDoses.isNotEmpty()) {
                    TimeSlotSection(label = slot.label, emoji = slot.emoji, count = slotDoses.size, ts = ts) {
                        slotDoses.forEach { dose ->
                            val med = medications.find { it.id == dose.medicationId }
                            DoseMedicineCard(dose = dose, medication = med, ts = ts, onConfirm = { onConfirm(dose) })
                        }
                    }
                }
            } else {
                val slotMeds = medications.filter { med ->
                    med.scheduleTimes.any { t ->
                        val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                        val mapped = if (h < 6) h + 24 else h
                        mapped in slot.range
                    }
                }
                if (slotMeds.isNotEmpty()) {
                    TimeSlotSection(label = slot.label, emoji = slot.emoji, count = slotMeds.size, ts = ts) {
                        slotMeds.forEach { med ->
                            val times = med.scheduleTimes.filter { t ->
                                val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                                val mapped = if (h < 6) h + 24 else h
                                mapped in slot.range
                            }
                            ScheduledMedicineCard(medication = med, times = times, ts = ts)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotSection(
    label: String,
    emoji: String,
    count: Int,
    ts: Float,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(
                containerColor = if (expanded) MedGreen.copy(alpha = 0.08f) else Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(if (expanded) 0.dp else 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = (14 * ts.coerceIn(1f, 1.3f)).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = (22 * ts).sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238),
                        fontSize = (15 * ts).sp
                    )
                    Text(
                        "$count மருந்து ($count medicine${if (count > 1) "s" else ""})",
                        color = Color.Gray,
                        fontSize = (11 * ts).sp
                    )
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = MedGreen
                )
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}

// ─── Dose Medicine Card (Senior view with "I Took It" button) ─────────────────

@Composable
private fun DoseMedicineCard(
    dose: DoseRecord,
    medication: Medication?,
    ts: Float,
    onConfirm: () -> Unit
) {
    val medColor = MedPalette.colorForMedication(
        medication?.pillColorHex ?: "#4CAF50",
        medication?.colorIndex ?: -1
    )
    val statusColor = when {
        dose.isTaken() -> Color(0xFF4CAF50)
        dose.isMissed() -> Color(0xFFF44336)
        dose.isOverdue() -> Color(0xFFFF9800)
        else -> Color(0xFF2196F3)
    }
    val statusLabel = when {
        dose.isTaken() -> "✅ எடுத்துவிட்டீர்கள் (Taken)"
        dose.isMissed() -> "❌ தவறிவிட்டீர்கள் (Missed)"
        dose.isOverdue() -> "⏰ நேரம் கடந்தது (Overdue)"
        else -> "🕐 எடுக்க வேண்டியது (Pending)"
    }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tablet image or shape icon
                if (medication?.imagePath?.isNotBlank() == true) {
                    AsyncImage(
                        model = medication.imagePath,
                        contentDescription = medication.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size((60 * ts.coerceIn(1f, 1.4f)).dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, medColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    )
                } else {
                    TabletShapeIcon(
                        color = medColor,
                        shape = medication?.shape ?: "circle",
                        size = (52 * ts.coerceIn(1f, 1.4f)).dp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dose.medicationName,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A2E1A),
                        fontSize = (19 * ts).sp
                    )
                    if (medication?.purpose?.isNotBlank() == true) {
                        Text(
                            medication.purpose,
                            color = Color(0xFF37474F),
                            fontWeight = FontWeight.Medium,
                            fontSize = (14 * ts).sp
                        )
                    }
                    Text(
                        "${medication?.dosage ?: "1 tablet"} • ${timeFmt.format(Date(dose.scheduledTime))}",
                        color = Color.Gray,
                        fontSize = (12 * ts).sp
                    )
                    if (medication?.instructions?.isNotBlank() == true) {
                        Text(
                            "📋 ${medication.instructions}",
                            color = Color(0xFF546E7A),
                            fontSize = (12 * ts).sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Status chip + "I Took It" button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (13 * ts).sp
                    )
                }
                if (!dose.isTaken() && !dose.isMissed()) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                        modifier = Modifier
                            .height((64 * ts.coerceIn(1f, 1.4f)).dp)
                            .widthIn(min = (88 * ts.coerceIn(1f, 1.4f)).dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅", fontSize = (20 * ts).sp)
                            Text(
                                "எடுத்தேன்",
                                fontSize = (12 * ts).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "I Took It",
                                fontSize = (10 * ts).sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Disclaimer note at bottom
            Text(
                "⚠️ Check the label before taking. Picture is for recognition only.",
                color = Color(0xFF9E9E9E),
                fontSize = (9 * ts).sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ScheduledMedicineCard(medication: Medication, times: List<String>, ts: Float) {
    val medColor = MedPalette.colorForMedication(medication.pillColorHex, medication.colorIndex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (medication.imagePath.isNotBlank()) {
                AsyncImage(
                    model = medication.imagePath,
                    contentDescription = medication.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size((52 * ts.coerceIn(1f, 1.4f)).dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                TabletShapeIcon(color = medColor, shape = medication.shape, size = (48 * ts.coerceIn(1f, 1.4f)).dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A2E1A), fontSize = (17 * ts).sp)
                if (medication.purpose.isNotBlank()) {
                    Text(medication.purpose, color = Color(0xFF546E7A), fontSize = (13 * ts).sp)
                }
                Text("${medication.dosage} • ${times.joinToString(", ")}",
                    color = Color.Gray, fontSize = (12 * ts).sp)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE3F2FD)) {
                Text("📅 Scheduled",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFF1565C0),
                    fontSize = (11 * ts).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Smart Medicine Box Card (unchanged) ─────────────────────────────────────

@Composable
private fun SmartMedicineBoxCard(
    state: SeniorUiState,
    currentSlotMeds: List<Medication>,
    onStripPicked: (String) -> Unit
) {
    val context = LocalContext.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val slotName = when (hour) {
        in 6..11 -> "🌅 காலை (Morning)"
        in 12..16 -> "☀️ மதியம் (Afternoon)"
        in 17..20 -> "🌆 மாலை (Evening)"
        else -> "🌙 இரவு (Night)"
    }
    val ledColor = when (state.boxLedStatus) {
        LedStatus.GREEN -> Color(0xFF4CAF50)
        LedStatus.RED -> Color(0xFFF44336)
        LedStatus.NONE -> Color(0xFF9E9E9E)
    }
    val ledPulse = rememberInfiniteTransition(label = "led")
    val ledAlpha by ledPulse.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha"
    )

    LaunchedEffect(state.boxLedStatus) {
        if (state.boxLedStatus == LedStatus.RED) {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 300), -1))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💊 Smart Medicine Box",
                    fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.weight(1f), fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .alpha(if (state.boxLedStatus != LedStatus.NONE) ledAlpha else 1f)
                            .clip(CircleShape)
                            .background(ledColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.bandConnected) "Band ●" else "Band ○",
                        fontSize = 11.sp,
                        color = if (state.bandConnected) Color(0xFF69F0AE) else Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Current slot: $slotName", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))

            if (state.boxLedMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = ledColor.copy(alpha = 0.25f)) {
                    Text(state.boxLedMessage,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (state.cameraMonitoringActive || state.intakeDetected != null) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.4f)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📷", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        when {
                            state.cameraMonitoringActive -> {
                                val dots by rememberInfiniteTransition(label = "d")
                                    .animateFloat(0f, 3f, infiniteRepeatable(tween(900)), label = "d2")
                                Text("Monitoring intake" + ".".repeat(dots.toInt() + 1),
                                    fontSize = 13.sp, color = Color.White)
                            }
                            state.intakeDetected == true ->
                                Text("✅ Medicine intake detected!", fontSize = 13.sp,
                                    color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                            else ->
                                Text("⚠️ Intake not detected", fontSize = 13.sp, color = Color(0xFFFFD740))
                        }
                    }
                }
            }

            if (currentSlotMeds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Tray dispensed — pick your strip:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currentSlotMeds.forEach { med ->
                        val pillColor = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                        val alreadyTaken = state.todayDoses.find { it.medicationId == med.id }?.isTaken() == true
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (alreadyTaken) Color.Gray.copy(alpha = 0.4f) else pillColor.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f).clickable(enabled = !alreadyTaken) { onStripPicked(med.id) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(med.pillEmoji, fontSize = 26.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(med.name.split(" ").first(), fontSize = 11.sp,
                                    color = Color.White, fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center, maxLines = 1)
                                if (alreadyTaken) Text("✅", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("No medicines for current time slot", fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

// ─── Summary Card ─────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(taken: Int, total: Int, ts: Float) {
    val dateStr = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) }
    val progress = if (total == 0) 0f else taken.toFloat() / total.toFloat()
    val allDone = taken == total && total > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (allDone) MedGreen else Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(dateStr, fontSize = (13 * ts).sp,
                color = if (allDone) Color.White.copy(alpha = 0.8f) else Color.Gray)
            Text("இன்றைய மருந்துகள் (Today's Medicines)", fontSize = (11 * ts).sp,
                color = if (allDone) Color.White.copy(alpha = 0.7f) else Color.Gray)
            Spacer(Modifier.height(8.dp))
            when {
                total == 0 -> Text("மருந்துகள் இல்லை (No medicines scheduled)",
                    fontSize = (16 * ts).sp, color = Color.Gray)
                allDone -> {
                    Text("🎉 எல்லா மருந்துகளும் எடுத்தீர்கள்!",
                        fontSize = (20 * ts).sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("(All medicines taken! Great job!)",
                        fontSize = (14 * ts).sp, color = Color.White.copy(alpha = 0.85f))
                }
                else -> {
                    Text("$taken / $total மருந்துகள் எடுத்தீர்கள்",
                        fontSize = (18 * ts).sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A2E1A))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height((14 * ts.coerceIn(1f, 1.3f)).dp)
                            .clip(RoundedCornerShape(7.dp)),
                        color = MedGreen,
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }
        }
    }
}

// ─── Public composables kept for nav compatibility ────────────────────────────

@Composable
fun MedicationCard(medication: Medication, doseRecord: DoseRecord?, onClick: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val status = doseRecord?.doseStatus() ?: DoseStatus.PENDING
    val isOverdue = doseRecord?.isOverdue() == true
    val (statusColor, statusText, statusBg) = when {
        status == DoseStatus.TAKEN -> Triple(StatusGreen, "✓ எடுத்தார்கள் ${timeFmt.format(Date(doseRecord!!.takenAt))}", Color(0xFFE8F5E9))
        status == DoseStatus.MISSED -> Triple(StatusRed, "✗ தவறினார்கள்", Color(0xFFFFEBEE))
        isOverdue -> Triple(StatusAmber, "⏰ தாமதம்!", Color(0xFFFFF8E1))
        doseRecord != null -> Triple(MedGreen, "எடுக்க வேண்டியது ${timeFmt.format(Date(doseRecord.scheduledTime))}", Color.White)
        else -> Triple(Color.Gray, "இன்று திட்டமிடவில்லை", Color.White)
    }
    val medColor = MedPalette.colorForMedication(medication.pillColorHex, medication.colorIndex)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(medColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(medication.pillEmoji, fontSize = 32.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, fontWeight = FontWeight.Bold, color = Color(0xFF1A2E1A),
                    style = MaterialTheme.typography.titleMedium)
                Text(medication.purpose, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusColor, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun EngagementDialog(question: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("👋 வணக்கம்!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(question, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("நீங்கள் எப்படி இருக்கிறீர்கள்? (Just checking in!)",
                    style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("நான் நலமாக இருக்கிறேன்! 😊 (I'm here!)",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("பிறகு (Not now)", color = Color.Gray) }
        }
    )
}

@Composable
fun DoubleDoseWarningDialog(medicationName: String, takenAt: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🛡️ ஏற்கனவே எடுத்தீர்கள்! (Already Taken!)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
        },
        text = {
            Text("$medicationName ஐ $takenAt ல் எடுத்துவிட்டீர்கள்.\n\nமீண்டும் எடுப்பது தீங்கு செய்யலாம். (Taking it again could be harmful. Please wait.)",
                style = MaterialTheme.typography.bodyLarge)
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("சரி, புரிந்தது (OK, I understand)",
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CameraIndicatorDot() {
    val pulse = rememberInfiniteTransition(label = "cam")
    val a by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier = Modifier.size(10.dp).alpha(a).clip(CircleShape).background(Color(0xFF4CAF50)))
}
