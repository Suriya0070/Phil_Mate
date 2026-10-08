package com.jsf.app.medication_solution.ui.caregiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.data.model.ConversationReport
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodRecord
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen
import com.jsf.app.medication_solution.ui.theme.StatusAmber
import com.jsf.app.medication_solution.ui.theme.StatusGreen
import com.jsf.app.medication_solution.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorDetailScreen(
    viewModel: CaregiverViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snap = state.seniorSnapshot
    val context = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    if (state.showAddMedDialog) {
        AddMedicationDialog(
            onDismiss = viewModel::hideAddMedDialog,
            onConfirm = { name, dosage, purpose, times, instructions, emoji, color ->
                viewModel.addMedication(name, dosage, purpose, times, instructions, emoji, color)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        snap?.user?.name ?: "Senior Details",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.scheduleMedicationAlarms(context) }) {
                        Icon(Icons.Default.Notifications, "Schedule Alarms", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CareBlue)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showAddMedDialog,
                containerColor = CareBlue
            ) {
                Icon(Icons.Default.Add, "Add Medication", tint = Color.White)
            }
        }
    ) { padding ->
        if (snap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CareBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F4FF))
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StatusHeaderCard(snap = snap) }

            item { CheckInScheduleCard(
                currentInterval = state.checkInIntervalMinutes,
                onIntervalSelected = { viewModel.setCheckInInterval(context, it) }
            ) }

            item { CameraMonitorCard(
                isEnabled = state.isMonitoringEnabled,
                onToggle = viewModel::toggleMonitoring
            ) }

            item {
                Text(
                    "Medications",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
            }
            if (state.medications.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text("No medicines yet. Tap + to add.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    }
                }
            } else {
                items(state.medications) { med -> MedicationItemCard(med) }
            }

            item {
                Text(
                    "Today's Medication Timeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
            }
            if (snap.todayDoses.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text("No doses scheduled today", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    }
                }
            } else {
                items(snap.todayDoses) { dose -> DoseTimelineItem(dose = dose, timeFmt = timeFmt) }
            }

            if (state.conversationReports.isNotEmpty()) {
                item {
                    Text(
                        "Conversation Reports",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A237E)
                    )
                }
                items(state.conversationReports) { report -> ConversationReportCard(report) }
            }

            item {
                Text("Recent Mood", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
            }
            item {
                snap.latestMood?.let { MoodHistoryCard(mood = it) }
                    ?: Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text("No mood data yet", modifier = Modifier.padding(16.dp), color = Color.Gray)
                    }
            }

            if (state.alerts.isNotEmpty()) {
                item {
                    Text("All Alerts Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                }
                items(state.alerts) { alert ->
                    if (!alert.isResolved) AlertCard(alert = alert, onResolve = { viewModel.resolveAlert(alert.id) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CheckInScheduleCard(currentInterval: Int, onIntervalSelected: (Int) -> Unit) {
    val intervals = listOf(5, 10, 15, 30, 60)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Check-In Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Auto voice check-in interval", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                intervals.forEach { mins ->
                    FilterChip(
                        selected = currentInterval == mins,
                        onClick = { onIntervalSelected(mins) },
                        label = { Text("${mins}m") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CareBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraMonitorCard(isEnabled: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Color(0xFFE3F2FD) else Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📷", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Camera Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (isEnabled) "Active — patient sees indicator dot" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) CareBlue else Color.Gray
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = CareBlue)
            )
        }
    }
}

@Composable
private fun MedicationItemCard(med: Medication) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FF)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(med.pillEmoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(med.purpose, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text("${med.dosage} • ${med.scheduleTimes.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = CareBlue)
            }
        }
    }
}

@Composable
private fun ConversationReportCard(report: ConversationReport) {
    val timeFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val emotionEmoji = when (report.dominantEmotion) {
        "HAPPY" -> "😊"
        "SAD" -> "😢"
        "WORRIED" -> "😰"
        "PAIN" -> "😣"
        "TIRED" -> "😴"
        else -> "😐"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emotionEmoji, fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (report.triggerType == "CHECK_IN") "Check-In" else "Medication Reminder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Text(
                            report.dominantEmotion,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = CareBlue
                        )
                    }
                }
                Text(
                    "${report.turns} turns • ${timeFmt.format(Date(report.startTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (report.summary.isNotBlank()) {
                    Text(report.summary, style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dosage: String, purpose: String, times: List<String>, instructions: String, emoji: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("1 tablet") }
    var purpose by remember { mutableStateOf("") }
    var timesText by remember { mutableStateOf("08:00") }
    var instructions by remember { mutableStateOf("Take with food") }
    var selectedEmoji by remember { mutableStateOf("💊") }
    val emojiOptions = listOf("💊", "🔴", "🔵", "🟠", "🟡", "🟢", "🟣", "⚪", "🫀")
    val colorMap = mapOf("💊" to "#9C27B0","🔴" to "#F44336","🔵" to "#2196F3","🟠" to "#FF9800","🟡" to "#FFEB3B","🟢" to "#4CAF50","🟣" to "#673AB7","⚪" to "#9E9E9E","🫀" to "#E91E63")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medication", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Medicine Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = purpose, onValueChange = { purpose = it },
                    label = { Text("Purpose (e.g. Blood Pressure)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dosage, onValueChange = { dosage = it },
                    label = { Text("Dosage (e.g. 1 tablet)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = timesText, onValueChange = { timesText = it },
                    label = { Text("Schedule times (e.g. 08:00, 20:00)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = instructions, onValueChange = { instructions = it },
                    label = { Text("Instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Pill Icon:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojiOptions.forEach { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = emoji },
                            label = { Text(emoji, fontSize = 18.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CareBlue.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val times = timesText.split(",").map { it.trim() }.filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                    if (name.isNotBlank() && times.isNotEmpty()) {
                        onConfirm(name, dosage, purpose, times, instructions, selectedEmoji, colorMap[selectedEmoji] ?: "#9C27B0")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue),
                enabled = name.isNotBlank()
            ) { Text("Add", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun levelToColor(level: SeniorStatusLevel): Color = when (level) {
    SeniorStatusLevel.GREEN -> Color(0xFF4CAF50)
    SeniorStatusLevel.AMBER -> Color(0xFFFFA726)
    SeniorStatusLevel.RED -> Color(0xFFF44336)
}

@Composable
private fun StatusHeaderCard(snap: SeniorSnapshot) {
    val statusColor = levelToColor(snap.statusLevel)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(snap.statusLevel.emoji, fontSize = 48.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(snap.statusLevel.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = statusColor)
                Text("Weekly adherence: ${(snap.weeklyAdherence * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                val lastSeen = when {
                    snap.inactiveMinutes < 1 -> "Just now"
                    snap.inactiveMinutes < 60 -> "${snap.inactiveMinutes} min ago"
                    else -> "${snap.inactiveMinutes / 60}h ago"
                }
                Text("Last active: $lastSeen", style = MaterialTheme.typography.bodyMedium, color = if (snap.inactiveMinutes > 30) Color(0xFFF57F17) else Color.Gray)
            }
        }
    }
}

@Composable
private fun DoseTimelineItem(dose: DoseRecord, timeFmt: SimpleDateFormat) {
    val (dotColor, icon, label) = when {
        dose.isTaken() -> Triple(StatusGreen, "✅", "Taken at ${timeFmt.format(Date(dose.takenAt))}")
        dose.isMissed() -> Triple(StatusRed, "❌", "MISSED — was due at ${timeFmt.format(Date(dose.scheduledTime))}")
        dose.isOverdue() -> Triple(StatusAmber, "⏰", "OVERDUE since ${timeFmt.format(Date(dose.scheduledTime))}")
        else -> Triple(CareBlue, "🕐", "Due at ${timeFmt.format(Date(dose.scheduledTime))}")
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(dotColor))
            Box(modifier = Modifier.width(2.dp).height(40.dp).background(Color.LightGray))
        }
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    dose.isMissed() -> Color(0xFFFFEBEE)
                    dose.isTaken() -> Color(0xFFE8F5E9)
                    dose.isOverdue() -> Color(0xFFFFF8E1)
                    else -> Color.White
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(dose.medicationName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = dotColor)
                    if (dose.isTaken() && dose.moodAfter.isNotBlank()) {
                        val mood = runCatching {
                            com.jsf.app.medication_solution.data.model.MoodLevel.valueOf(dose.moodAfter)
                        }.getOrNull()
                        mood?.let { Text("Mood after: ${it.emoji} ${it.label}", style = MaterialTheme.typography.labelMedium, color = Color.Gray) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodHistoryCard(mood: MoodRecord) {
    val timeFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mood.moodLevel().emoji, fontSize = 48.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Feeling ${mood.moodLevel().label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (mood.moodLevel().score <= 2) Color(0xFFB71C1C) else MedGreen)
                Text(timeFmt.format(Date(mood.timestamp)), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun AlertCard(alert: com.jsf.app.medication_solution.data.model.Alert, onResolve: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val bgColor = when (alert.alertSeverity()) {
        com.jsf.app.medication_solution.data.model.AlertSeverity.CRITICAL,
        com.jsf.app.medication_solution.data.model.AlertSeverity.HIGH -> Color(0xFFFFEBEE)
        com.jsf.app.medication_solution.data.model.AlertSeverity.MEDIUM -> Color(0xFFFFF8E1)
        com.jsf.app.medication_solution.data.model.AlertSeverity.LOW -> Color(0xFFE8F5E9)
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(alert.alertType().emoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(timeFmt.format(Date(alert.timestamp)), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            IconButton(onClick = onResolve) {
                Icon(Icons.Default.CheckCircle, "Resolve", tint = MedGreen, modifier = Modifier.size(24.dp))
            }
        }
    }
}
