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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen
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
                        Text(greeting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(state.user?.name ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                },
                actions = {
                    if (state.isMonitored) CameraIndicatorDot()
                    IconButton(onClick = onVoiceAssistant) { Icon(Icons.Default.Mic, "Voice Assistant", tint = Color.White) }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedGreen, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            if (state.medications.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.seedDemoMedications() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("மருந்துகள் ஏற்று") },
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
                    containerColor = MedGreen
                ) { Icon(Icons.Default.Mic, "Quick voice confirm", tint = Color.White) }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MedGreen) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF1F8E9)).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item { SummaryCard(taken = taken, total = total) }

            // Family voice note
            if (state.hasVoiceNote) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)), shape = RoundedCornerShape(16.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💌", fontSize = 28.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("குடும்பத்தினரிடம் இருந்து குரல் செய்தி!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("(Voice message from your family!)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.playFamilyVoiceNote(context) }) {
                                Icon(Icons.Default.PlayArrow, "Play", tint = CareBlue)
                            }
                        }
                    }
                }
            }

            // Daily brain challenge
            state.dailyChallenge?.let { challenge ->
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🧠 தினசரி மூளை பயிற்சி (Daily Brain Exercise)", style = MaterialTheme.typography.labelLarge, color = CareBlue, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(challenge, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Success message
            item {
                AnimatedVisibility(visible = state.successMessage != null, enter = fadeIn() + slideInVertically()) {
                    state.successMessage?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)), modifier = Modifier.fillMaxWidth()) {
                            Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = MedGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Weekly calendar row
            item {
                WeeklyCalendarRow(selectedOffset = state.selectedDayOffset, onDaySelect = viewModel::selectDay)
            }

            // Day label
            item {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, state.selectedDayOffset)
                val dayLabel = when (state.selectedDayOffset) {
                    0 -> "இன்று (Today) — $taken/$total எடுத்தீர்கள்"
                    1 -> "நாளை (Tomorrow)"
                    -1 -> "நேற்று (Yesterday)"
                    else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(cal.time)
                }
                Text(dayLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MedGreen)
            }

            // Medicines by time slot or empty state
            if (state.medications.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💊", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("மருந்துகள் இல்லை", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                            Text("(No medicines yet)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            } else {
                item {
                    DayMedicationView(
                        dayOffset = state.selectedDayOffset,
                        todayDoses = state.todayDoses,
                        medications = state.medications,
                        onConfirm = { dose ->
                            viewModel.onUserInteraction()
                            onMedicationClick(dose, state.medications.find { it.id == dose.medicationId } ?: return@DayMedicationView)
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun WeeklyCalendarRow(selectedOffset: Int, onDaySelect: (Int) -> Unit) {
    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val days = (-3..3).map { offset ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offset)
        Triple(offset, dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1], cal.get(Calendar.DAY_OF_MONTH))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(days) { (offset, name, num) ->
            val isSelected = offset == selectedOffset
            val isToday = offset == 0
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when { isSelected -> MedGreen; isToday -> MedGreen.copy(alpha = 0.15f); else -> Color.White },
                modifier = Modifier.width(52.dp).clickable { onDaySelect(offset) }
            ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White else if (isToday) MedGreen else Color.Gray, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
                    Text("$num", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = if (isSelected) Color.White else Color(0xFF1A2E1A))
                    if (isToday) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else MedGreen))
                }
            }
        }
    }
}

@Composable
private fun DayMedicationView(
    dayOffset: Int,
    todayDoses: List<DoseRecord>,
    medications: List<Medication>,
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
                    TimeSlotSection(label = slot.label, emoji = slot.emoji, count = slotDoses.size) {
                        slotDoses.forEach { dose ->
                            val med = medications.find { it.id == dose.medicationId }
                            DoseMedicineCard(dose = dose, medication = med, onConfirm = { onConfirm(dose) })
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
                    TimeSlotSection(label = slot.label, emoji = slot.emoji, count = slotMeds.size) {
                        slotMeds.forEach { med ->
                            val times = med.scheduleTimes.filter { t ->
                                val h = t.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                                val mapped = if (h < 6) h + 24 else h
                                mapped in slot.range
                            }
                            ScheduledMedicineCard(medication = med, times = times)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotSection(label: String, emoji: String, count: Int, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(containerColor = if (expanded) MedGreen.copy(alpha = 0.08f) else Color.White),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(if (expanded) 0.dp else 2.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF263238))
                    Text("$count மருந்து ($count medicine${if (count > 1) "s" else ""})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MedGreen)
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}

@Composable
private fun DoseMedicineCard(dose: DoseRecord, medication: Medication?, onConfirm: () -> Unit) {
    val pillColor = remember(medication?.pillColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(medication?.pillColorHex ?: "#4CAF50")) }
            .getOrElse { Color(0xFF4CAF50) }
    }
    val statusColor = when {
        dose.isTaken() -> Color(0xFF4CAF50)
        dose.isMissed() -> Color(0xFFF44336)
        dose.isOverdue() -> Color(0xFFFF9800)
        else -> Color(0xFF2196F3)
    }
    val statusLabel = when {
        dose.isTaken() -> "✅ எடுத்துவிட்டீர்கள்"
        dose.isMissed() -> "❌ தவறிவிட்டீர்கள்"
        dose.isOverdue() -> "⏰ நேரம் கடந்தது"
        else -> "🕐 காத்திருக்கிறது"
    }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(pillColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(medication?.pillEmoji ?: "💊", fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dose.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${medication?.dosage ?: ""} • ${timeFmt.format(Date(dose.scheduledTime))}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (medication?.purpose?.isNotBlank() == true) Text(medication.purpose, style = MaterialTheme.typography.labelSmall, color = Color(0xFF546E7A))
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!dose.isTaken() && !dose.isMissed()) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) { Text("எடு\nTake", style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun ScheduledMedicineCard(medication: Medication, times: List<String>) {
    val pillColor = remember(medication.pillColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(medication.pillColorHex)) }
            .getOrElse { Color(0xFF4CAF50) }
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(pillColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(medication.pillEmoji, fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${medication.dosage} • ${times.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(medication.purpose, style = MaterialTheme.typography.labelSmall, color = Color(0xFF546E7A))
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE3F2FD)) {
                Text("📅 திட்டம்", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
            }
        }
    }
}

@Composable
private fun SummaryCard(taken: Int, total: Int) {
    val dateStr = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) }
    val progress = if (total == 0) 0f else taken.toFloat() / total.toFloat()
    val allDone = taken == total && total > 0

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (allDone) MedGreen else Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(dateStr, style = MaterialTheme.typography.bodyMedium, color = if (allDone) Color.White.copy(alpha = 0.8f) else Color.Gray)
            Text("இன்றைய மருந்துகள் (Today's Medicines)", style = MaterialTheme.typography.labelSmall, color = if (allDone) Color.White.copy(alpha = 0.7f) else Color.Gray)
            Spacer(Modifier.height(8.dp))
            when {
                total == 0 -> Text("மருந்துகள் இல்லை (No medicines scheduled)", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                allDone -> {
                    Text("🎉 எல்லா மருந்துகளும் எடுத்தீர்கள்!", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("(All medicines taken! Great job!)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
                else -> {
                    Text("$taken / $total மருந்துகள் எடுத்தீர்கள்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A2E1A))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), color = MedGreen, trackColor = Color(0xFFE0E0E0))
                }
            }
        }
    }
}

// Keep existing public composables for navigation compatibility
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
    val pillColor = runCatching { Color(android.graphics.Color.parseColor(medication.pillColorHex)) }.getOrElse { MedGreen }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = statusBg), elevation = CardDefaults.cardElevation(3.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(pillColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(medication.pillEmoji, fontSize = 32.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A2E1A))
                Text(medication.purpose, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun EngagementDialog(question: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👋 வணக்கம்!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(question, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("நீங்கள் எப்படி இருக்கிறீர்கள்? (Just checking in!)", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MedGreen), modifier = Modifier.fillMaxWidth()) {
                Text("நான் நலமாக இருக்கிறேன்! 😊 (I'm here!)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("பிறகு (Not now)", color = Color.Gray) } }
    )
}

@Composable
fun DoubleDoseWarningDialog(medicationName: String, takenAt: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛡️ ஏற்கனவே எடுத்தீர்கள்! (Already Taken!)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C)) },
        text = { Text("$medicationName ஐ $takenAt ல் எடுத்துவிட்டீர்கள்.\n\nமீண்டும் எடுப்பது தீங்கு செய்யலாம். (Taking it again could be harmful. Please wait.)", style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MedGreen), modifier = Modifier.fillMaxWidth()) {
                Text("சரி, புரிந்தது (OK, I understand)", color = Color.White, fontWeight = FontWeight.Bold)
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
