package com.jsf.app.medication_solution.ui.caregiver

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jsf.app.medication_solution.data.model.Alert
import com.jsf.app.medication_solution.data.model.AlertSeverity
import com.jsf.app.medication_solution.data.model.ConversationReport
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.service.VoiceAlarmManager
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen
import com.jsf.app.medication_solution.ui.theme.StatusAmber
import com.jsf.app.medication_solution.ui.theme.StatusGreen
import com.jsf.app.medication_solution.ui.theme.StatusRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    viewModel: CaregiverViewModel,
    onViewSeniorDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRecordingAlarm by remember { mutableStateOf(false) }
    var isRecordingNote by remember { mutableStateOf(false) }
    var alarmSaved by remember { mutableStateOf(false) }
    var noteSaved by remember { mutableStateOf(false) }

    LaunchedEffect(state.seniorSnapshot?.user?.id) {
        val sid = state.seniorSnapshot?.user?.id ?: return@LaunchedEffect
        alarmSaved = VoiceAlarmManager.isAlarmVoiceSaved(context, sid)
        noteSaved = VoiceAlarmManager.isFamilyNoteSaved(context, sid)
    }

    state.linkSuccess?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000); viewModel.clearLinkSuccess()
        }
    }

    if (state.linkDialogVisible) {
        LinkSeniorDialog(
            email = state.linkEmail,
            onEmailChange = viewModel::onLinkEmailChanged,
            onLink = viewModel::linkSenior,
            onDismiss = viewModel::hideLinkDialog,
            isLoading = state.isLoading
        )
    }

    if (state.showVoiceAlarmRecorder) {
        VoiceRecordDialog(
            title = "🎙️ Record Alarm Voice",
            hint = "Example: \"Thatha, medicine edunga!\"",
            isRecording = isRecordingAlarm,
            saved = alarmSaved,
            onStart = { isRecordingAlarm = true; VoiceAlarmManager.startRecording(context) },
            onStop = {
                isRecordingAlarm = false
                val file = VoiceAlarmManager.stopRecording()
                val sid = state.seniorSnapshot?.user?.id ?: ""
                if (file != null && sid.isNotBlank()) {
                    coroutineScope.launch {
                        if (VoiceAlarmManager.uploadAlarmVoice(sid, file)) {
                            VoiceAlarmManager.markAlarmVoiceSaved(context, sid)
                            alarmSaved = true
                        }
                    }
                }
            },
            onDismiss = viewModel::hideVoiceAlarmRecorder
        )
    }

    if (state.showFamilyNoteRecorder) {
        VoiceRecordDialog(
            title = "💌 Leave a Voice Note",
            hint = "Example: \"Thatha, how are you today?\"",
            isRecording = isRecordingNote,
            saved = noteSaved,
            onStart = { isRecordingNote = true; VoiceAlarmManager.startRecording(context) },
            onStop = {
                isRecordingNote = false
                val file = VoiceAlarmManager.stopRecording()
                val sid = state.seniorSnapshot?.user?.id ?: ""
                if (file != null && sid.isNotBlank()) {
                    coroutineScope.launch {
                        if (VoiceAlarmManager.uploadFamilyNote(sid, file)) {
                            VoiceAlarmManager.markFamilyNoteSaved(context, sid)
                            noteSaved = true
                        }
                    }
                }
            },
            onDismiss = viewModel::hideFamilyNoteRecorder
        )
    }

    if (state.showEmergencyDialog) {
        EmergencyContactDialog(
            contactName = state.emergencyContact,
            contactPhone = state.emergencyContactPhone,
            onSave = viewModel::saveEmergencyContact,
            onDismiss = viewModel::hideEmergencyDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MediCare", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Family Dashboard", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CareBlue)
            )
        }
    ) { padding ->
        if (state.isLoading && state.seniorSnapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CareBlue) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF0F4FF)).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Hello, ${state.caregiver?.name ?: "Children"} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CareBlue
                )
            }

            state.linkSuccess?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                        Text("✅ $msg", modifier = Modifier.padding(16.dp), color = MedGreen, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item { NoSeniorLinkedCard(onLinkClick = viewModel::showLinkDialog) }
            } else {
                val snap = state.seniorSnapshot!!

                // Emergency alert
                if (snap.inactiveMinutes > 30 && state.emergencyContactPhone.isNotBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), shape = RoundedCornerShape(12.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🚨", fontSize = 28.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("No activity for ${snap.inactiveMinutes} minutes!", fontWeight = FontWeight.Bold, color = Color.Red)
                                    Text("Check if ${snap.user.name} is okay", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = {
                                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${state.emergencyContactPhone}"))
                                    context.startActivity(intent)
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                    Text("Call", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Status header
                item { PatientStatusCard(snapshot = snap, streak = state.streak, onViewDetail = { onViewSeniorDetail(snap.user.id) }) }

                // Camera + emotion
                item {
                    CameraMonitorCard(
                        enabled = state.isMonitoringEnabled,
                        snapshotUrl = state.cameraSnapshotUrl,
                        onToggle = viewModel::toggleMonitoring,
                        onRefresh = viewModel::refreshCameraSnapshot
                    )
                }

                // Medication progress
                item { MedicationProgressCard(medications = state.medications, doses = snap.todayDoses) }

                // Health vitals
                item { HealthVitalsCard(bp = state.vitalBP, hr = state.vitalHR, spo2 = state.vitalSpO2, onRefresh = viewModel::simulateVitals) }

                // Quick actions
                item {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionButton(emoji = "🎙️", label = "Alarm Voice", modifier = Modifier.weight(1f), onClick = viewModel::showVoiceAlarmRecorder)
                        QuickActionButton(emoji = "💌", label = "Voice Note", modifier = Modifier.weight(1f), onClick = viewModel::showFamilyNoteRecorder)
                        QuickActionButton(emoji = "🚨", label = "Emergency", modifier = Modifier.weight(1f), onClick = viewModel::showEmergencyDialog)
                        QuickActionButton(emoji = "💬", label = "Check-in", modifier = Modifier.weight(1f), onClick = { viewModel.scheduleImmediateCheckIn(context) })
                    }
                }

                // Check-in interval
                item { CheckInScheduleCard(intervalMinutes = state.checkInIntervalMinutes, onSetInterval = { viewModel.setCheckInInterval(context, it) }) }

                // Conversation reports
                if (state.conversationReports.isNotEmpty()) {
                    item { Text("Voice Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E)) }
                    items(state.conversationReports.take(3)) { report -> ConversationReportCard(report = report) }
                }

                // Alerts
                val unresolvedAlerts = state.alerts.filter { !it.isResolved }
                if (unresolvedAlerts.isNotEmpty()) {
                    item { Text("Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E)) }
                    items(unresolvedAlerts.take(5)) { alert -> AlertCard(alert = alert, onResolve = { viewModel.resolveAlert(alert.id) }) }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PatientStatusCard(snapshot: SeniorSnapshot, streak: Int, onViewDetail: () -> Unit) {
    val statusColor = levelToColor(snapshot.statusLevel)
    val (taken, total) = snapshot.todayDoses.let { it.count { d -> d.isTaken() } to it.size }
    val lastSeen = when {
        snapshot.inactiveMinutes < 1 -> "Just now"
        snapshot.inactiveMinutes < 60 -> "${snapshot.inactiveMinutes} min ago"
        else -> "${snapshot.inactiveMinutes / 60}h ago"
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("👴", fontSize = 32.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(snapshot.user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Last seen: $lastSeen", style = MaterialTheme.typography.bodyMedium, color = if (snapshot.inactiveMinutes > 30) Color(0xFFF57F17) else Color.Gray)
                    if (streak > 0) {
                        Text("🔥 $streak-day streak!", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                    }
                }
                StatusBadge(level = snapshot.statusLevel)
            }
            Spacer(Modifier.height(12.dp))
            if (total > 0) {
                Text("Today: $taken / $total medicines taken", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else taken.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = statusColor, trackColor = Color(0xFFE0E0E0)
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(modifier = Modifier.weight(1f), emoji = "📊", label = "${(snapshot.weeklyAdherence * 100).toInt()}%", desc = "7-day", color = when { snapshot.weeklyAdherence >= 0.8f -> StatusGreen; snapshot.weeklyAdherence >= 0.5f -> StatusAmber; else -> StatusRed })
                StatChip(modifier = Modifier.weight(1f), emoji = "⏰", label = if (snapshot.inactiveMinutes < 60) "${snapshot.inactiveMinutes}m" else "${snapshot.inactiveMinutes / 60}h", desc = "inactive", color = when { snapshot.inactiveMinutes < 30 -> StatusGreen; snapshot.inactiveMinutes < 60 -> StatusAmber; else -> StatusRed })
                StatChip(modifier = Modifier.weight(1f), emoji = "🚨", label = "${snapshot.todayDoses.count { it.isMissed() || it.isOverdue() }}", desc = "missed", color = if (snapshot.todayDoses.any { it.isMissed() }) StatusRed else StatusGreen)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onViewDetail, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                Text("View Full Details", color = Color.White)
            }
        }
    }
}

@Composable
private fun StatChip(modifier: Modifier = Modifier, emoji: String, label: String, desc: String, color: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.1f)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun CameraMonitorCard(enabled: Boolean, snapshotUrl: String?, onToggle: () -> Unit, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFFE3F2FD) else Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📷 Camera Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), modifier = Modifier.weight(1f))
                if (enabled) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, "Refresh", tint = CareBlue) }
                }
                Switch(checked = enabled, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedThumbColor = CareBlue))
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                if (snapshotUrl != null) {
                    AsyncImage(model = snapshotUrl, contentDescription = "Patient camera", modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(4.dp))
                    Text("📸 Latest snapshot — tap refresh to update", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                } else {
                    Text("Waiting for patient camera snapshot...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Text("Patient must open the app to send a snapshot", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text("Enable to see live snapshots from patient's camera", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun MedicationProgressCard(medications: List<Medication>, doses: List<com.jsf.app.medication_solution.data.model.DoseRecord>) {
    val taken = doses.count { it.isTaken() }
    val total = doses.size
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("💊 Medication Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
            Spacer(Modifier.height(8.dp))
            if (total > 0) {
                Text("$taken / $total doses taken today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = if (taken == total) MedGreen else CareBlue)
                LinearProgressIndicator(
                    progress = { taken.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = if (taken == total) MedGreen else CareBlue, trackColor = Color(0xFFE0E0E0)
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Text("No doses scheduled today", color = Color.Gray)
            }
            medications.forEach { med ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(med.pillEmoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(med.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (med.remainingPills in 1..5) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFEBEE)) {
                            Text("⚠️ ${med.remainingPills} left", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthVitalsCard(bp: String, hr: Int, spo2: Int, onRefresh: () -> Unit) {
    val parts = bp.split("/").map { it.trim().toIntOrNull() ?: 0 }
    val sys = parts.getOrElse(0) { 120 }
    val isHighBP = sys > 140
    val isHighHR = hr > 100
    val isLowSpo2 = spo2 < 94

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🩺 Health Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C), modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF4A148C)) }
            }
            Text("Simulated Wearable Band", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalChip(modifier = Modifier.weight(1f), label = "BP", value = "$bp", unit = "mmHg", icon = "❤️", alert = isHighBP)
                VitalChip(modifier = Modifier.weight(1f), label = "HR", value = "$hr", unit = "bpm", icon = "💓", alert = isHighHR)
                VitalChip(modifier = Modifier.weight(1f), label = "SpO2", value = "$spo2", unit = "%", icon = "🫁", alert = isLowSpo2)
            }
            if (isHighBP || isHighHR || isLowSpo2) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFEBEE)) {
                    Text("⚠️ Abnormal vitals. Consider consulting a doctor.", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("💡 Connect a Bluetooth health band for real readings", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun VitalChip(modifier: Modifier = Modifier, label: String, value: String, unit: String, icon: String, alert: Boolean) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = if (alert) Color(0xFFFFEBEE) else Color.White) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 18.sp)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (alert) Color.Red else Color(0xFF37474F))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun CheckInScheduleCard(intervalMinutes: Int, onSetInterval: (Int) -> Unit) {
    val options = listOf(5, 10, 15, 30, 60)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("⏰ Check-in Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
            Text("Auto voice interaction every:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { mins ->
                    FilterChip(
                        selected = intervalMinutes == mins,
                        onClick = { onSetInterval(mins) },
                        label = { Text("${mins}m") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CareBlue, selectedLabelColor = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = CareBlue, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConversationReportCard(report: ConversationReport) {
    val dtFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val emotionEmoji = when (report.dominantEmotion) { "HAPPY" -> "😊"; "SAD" -> "😢"; "WORRIED" -> "😰"; "PAIN" -> "😣"; "TIRED" -> "😴"; else -> "😐" }
    val triggerLabel = when (report.triggerType) { "MEDICATION" -> "💊 Med Alarm"; "CHECK_IN" -> "⏰ Check-in"; else -> "💬 Manual" }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emotionEmoji, fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dtFmt.format(Date(report.startTime)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("$triggerLabel • ${report.turns} turns • ${report.dominantEmotion.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (report.summary.isNotBlank()) Text(report.summary, style = MaterialTheme.typography.labelSmall, color = Color(0xFF37474F))
            }
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, onResolve: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val bgColor = when (alert.alertSeverity()) {
        AlertSeverity.CRITICAL -> Color(0xFFFFEBEE)
        AlertSeverity.HIGH -> Color(0xFFFFF3E0)
        AlertSeverity.MEDIUM -> Color(0xFFFFFDE7)
        AlertSeverity.LOW -> Color(0xFFE8F5E9)
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(alert.alertType().emoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(timeFmt.format(Date(alert.timestamp)), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
            IconButton(onClick = onResolve) { Icon(Icons.Default.CheckCircle, "Resolve", tint = MedGreen) }
        }
    }
}

@Composable
private fun VoiceRecordDialog(
    title: String,
    hint: String,
    isRecording: Boolean,
    saved: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Record your voice. It will play when the patient needs to hear from you.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1565C0), fontStyle = FontStyle.Italic)
                if (saved) Text("✅ Voice saved!", color = MedGreen, fontWeight = FontWeight.Bold)
                Button(
                    onClick = if (isRecording) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else CareBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (isRecording) Icons.Default.Close else Icons.Default.Mic, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRecording) "Stop & Save" else "Start Recording", color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (isRecording) Text("🔴 Recording...", color = Color.Red, style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun EmergencyContactDialog(contactName: String, contactPhone: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(contactName) }
    var phone by remember { mutableStateOf(contactPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚨 Emergency Contact", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("If no activity for 30+ minutes, this contact will receive an alert.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Contact Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Save", color = Color.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StatusBadge(level: SeniorStatusLevel) {
    val color = levelToColor(level)
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(level.label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoSeniorLinkedCard(onLinkClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👴", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("No Senior Linked Yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Link your family member's account to start monitoring their medications.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onLinkClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Link Family Member", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSeniorDialog(email: String, onEmailChange: (String) -> Unit, onLink: () -> Unit, onDismiss: () -> Unit, isLoading: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Family Member", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter the email address of the senior's account.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = email, onValueChange = onEmailChange, label = { Text("Senior's Email Address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onLink, enabled = email.isNotBlank() && !isLoading, colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Link Account", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun levelToColor(level: SeniorStatusLevel): Color = when (level) {
    SeniorStatusLevel.GREEN -> Color(0xFF4CAF50)
    SeniorStatusLevel.AMBER -> Color(0xFFFFA726)
    SeniorStatusLevel.RED -> Color(0xFFF44336)
}
