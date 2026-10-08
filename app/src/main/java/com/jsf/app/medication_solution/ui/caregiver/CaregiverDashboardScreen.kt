package com.jsf.app.medication_solution.ui.caregiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.MoodRecord
import com.jsf.app.medication_solution.service.VoiceAlarmManager
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen
import com.jsf.app.medication_solution.ui.theme.MedPalette
import com.jsf.app.medication_solution.ui.theme.StatusAmber
import com.jsf.app.medication_solution.ui.theme.StatusGreen
import com.jsf.app.medication_solution.ui.theme.StatusRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Palette ─────────────────────────────────────────────────────────────────

private val DarkSurface   = Color(0xFF0D1117)
private val DarkBar       = Color(0xFF161B22)
private val DividerColor  = Color(0xFFE8ECEF)
private val LabelGray     = Color(0xFF6E7681)
private val HeaderBlue    = Color(0xFF1565C0)

// ─── Main Screen ─────────────────────────────────────────────────────────────

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
    var isRecordingNote  by remember { mutableStateOf(false) }
    var alarmSaved       by remember { mutableStateOf(false) }
    var noteSaved        by remember { mutableStateOf(false) }

    LaunchedEffect(state.seniorSnapshot?.user?.id) {
        val sid = state.seniorSnapshot?.user?.id ?: return@LaunchedEffect
        alarmSaved = VoiceAlarmManager.isAlarmVoiceSaved(context, sid)
        noteSaved  = VoiceAlarmManager.isFamilyNoteSaved(context, sid)
    }
    state.linkSuccess?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); viewModel.clearLinkSuccess() }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
    if (state.linkDialogVisible) {
        LinkSeniorDialog(
            email = state.linkEmail, onEmailChange = viewModel::onLinkEmailChanged,
            onLink = viewModel::linkSenior, onDismiss = viewModel::hideLinkDialog,
            isLoading = state.isLoading
        )
    }
    if (state.showVoiceAlarmRecorder) {
        VoiceRecordDialog(
            title = "🎙️ Record Alarm Voice",
            hint  = "Example: \"Thatha, medicine edunga!\"",
            isRecording = isRecordingAlarm, saved = alarmSaved,
            onStart = { isRecordingAlarm = true; VoiceAlarmManager.startRecording(context) },
            onStop  = {
                isRecordingAlarm = false
                val file = VoiceAlarmManager.stopRecording()
                val sid  = state.seniorSnapshot?.user?.id ?: ""
                if (file != null && sid.isNotBlank()) {
                    coroutineScope.launch {
                        if (VoiceAlarmManager.uploadAlarmVoice(sid, file)) {
                            VoiceAlarmManager.markAlarmVoiceSaved(context, sid); alarmSaved = true
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
            hint  = "Example: \"Thatha, how are you today?\"",
            isRecording = isRecordingNote, saved = noteSaved,
            onStart = { isRecordingNote = true; VoiceAlarmManager.startRecording(context) },
            onStop  = {
                isRecordingNote = false
                val file = VoiceAlarmManager.stopRecording()
                val sid  = state.seniorSnapshot?.user?.id ?: ""
                if (file != null && sid.isNotBlank()) {
                    coroutineScope.launch {
                        if (VoiceAlarmManager.uploadFamilyNote(sid, file)) {
                            VoiceAlarmManager.markFamilyNoteSaved(context, sid); noteSaved = true
                        }
                    }
                }
            },
            onDismiss = viewModel::hideFamilyNoteRecorder
        )
    }
    if (state.showEmergencyDialog) {
        EmergencyContactDialog(
            contactName  = state.emergencyContact,
            contactPhone = state.emergencyContactPhone,
            onSave = { name, phone ->
                // Save to SharedPreferences so MedAlarmReceiver can auto-call this number
                context.getSharedPreferences("medicare_prefs", Context.MODE_PRIVATE)
                    .edit().putString("family_phone", phone).apply()
                viewModel.saveEmergencyContact(name, phone)
            },
            onDismiss = viewModel::hideEmergencyDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MediCare Dashboard",
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White,
                            letterSpacing = 0.3.sp)
                        Text("Caregiver View",
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showLinkDialog) {
                        Icon(Icons.Default.Add, "Link senior", tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBar)
            )
        },
        containerColor = Color(0xFFF4F6F9)
    ) { padding ->
        if (state.isLoading && state.seniorSnapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CareBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Greeting
            item {
                Text(
                    "Hello, ${state.caregiver?.name ?: "Caregiver"} 👋",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LabelGray
                )
            }

            // Success banner
            state.linkSuccess?.let { msg ->
                item {
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(10.dp)) {
                        Text("✅ $msg", modifier = Modifier.padding(12.dp),
                            color = MedGreen, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }

            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item { NoSeniorLinkedCard(onLinkClick = viewModel::showLinkDialog) }
            } else {
                val snap = state.seniorSnapshot!!

                // Patient info bar
                item { PatientInfoBar(snap = snap, streak = state.streak) }

                // Emergency alert
                if (snap.inactiveMinutes > 30 && state.emergencyContactPhone.isNotBlank()) {
                    item { InactivityAlertBar(snap = snap, phone = state.emergencyContactPhone, context = context) }
                }

                // 4-KPI chips row
                item { KpiRow(doses = snap.todayDoses) }

                // Medicine STATUS TABLE
                item { MedicineStatusTable(doses = snap.todayDoses, medications = state.medications) }

                // Mood + Vitals compact row
                item {
                    MoodVitalsRow(
                        mood = snap.latestMood,
                        bp = state.vitalBP, hr = state.vitalHR, spo2 = state.vitalSpO2,
                        onRefreshVitals = viewModel::simulateVitals
                    )
                }

                // Quick action bar
                item {
                    QuickActionBar(
                        monitoringEnabled = state.isMonitoringEnabled,
                        onToggleMonitor = viewModel::toggleMonitoring,
                        onVoiceNote   = viewModel::showFamilyNoteRecorder,
                        onEmergency   = viewModel::showEmergencyDialog,
                        onDetail      = { onViewSeniorDetail(snap.user.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── Patient Info Bar ─────────────────────────────────────────────────────────

@Composable
private fun PatientInfoBar(snap: SeniorSnapshot, streak: Int) {
    val lastSeen = when {
        snap.inactiveMinutes < 1  -> "Active now"
        snap.inactiveMinutes < 60 -> "${snap.inactiveMinutes} min ago"
        else                      -> "${snap.inactiveMinutes / 60}h ago"
    }
    Surface(
        color = Color.White, shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(levelToColor(snap.statusLevel).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text("👴", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(snap.user.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(lastSeen, fontSize = 12.sp,
                        color = if (snap.inactiveMinutes > 30) Color(0xFFE65100) else LabelGray)
                    if (streak > 0) Text("🔥 $streak days", fontSize = 12.sp, color = Color(0xFFFF6F00),
                        fontWeight = FontWeight.SemiBold)
                }
            }
            StatusPill(level = snap.statusLevel)
        }
    }
}

// ─── Inactivity Alert ─────────────────────────────────────────────────────────

@Composable
private fun InactivityAlertBar(snap: SeniorSnapshot, phone: String, context: Context) {
    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🚨", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text("No activity for ${snap.inactiveMinutes} min",
                fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color(0xFFC62828),
                modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) { Text("Call", color = Color.White, fontSize = 12.sp) }
        }
    }
}

// ─── KPI Row ──────────────────────────────────────────────────────────────────

@Composable
private fun KpiRow(doses: List<DoseRecord>) {
    val taken   = doses.count { it.isTaken() }
    val missed  = doses.count { it.isMissed() || it.isOverdue() }
    val pending = doses.size - taken - missed
    val total   = doses.size

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KpiChip(modifier = Modifier.weight(1f), value = "$taken",
            label = "Taken", color = MedGreen, bg = Color(0xFFE8F5E9))
        KpiChip(modifier = Modifier.weight(1f), value = "$pending",
            label = "Pending", color = Color(0xFFE65100), bg = Color(0xFFFFF3E0))
        KpiChip(modifier = Modifier.weight(1f), value = "$missed",
            label = "Missed", color = StatusRed, bg = Color(0xFFFFEBEE))
        KpiChip(modifier = Modifier.weight(1f), value = "$total",
            label = "Total", color = HeaderBlue, bg = Color(0xFFE3F2FD))
    }
}

@Composable
private fun KpiChip(modifier: Modifier = Modifier, value: String, label: String, color: Color, bg: Color) {
    Surface(modifier = modifier, color = bg, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
            Text(label, fontSize = 11.sp, color = LabelGray, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Medicine Status Table ─────────────────────────────────────────────────────

@Composable
private fun MedicineStatusTable(doses: List<DoseRecord>, medications: List<Medication>) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    if (doses.isEmpty()) return

    Surface(color = Color.White, shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            // Table header
            Row(modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFF0F4FF))
                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Medicine", modifier = Modifier.weight(1.7f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HeaderBlue)
                Text("Time", modifier = Modifier.weight(1f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HeaderBlue,
                    textAlign = TextAlign.Center)
                Text("Status", modifier = Modifier.weight(1.3f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HeaderBlue,
                    textAlign = TextAlign.End)
            }

            doses.forEachIndexed { index, dose ->
                val med = medications.find { it.id == dose.medicationId }
                val medColor = MedPalette.colorForMedication(
                    med?.pillColorHex ?: "#4CAF50", med?.colorIndex ?: -1
                )
                val (statusText, statusColor, statusBg) = when {
                    dose.isTaken() -> Triple("Taken ✅", MedGreen, Color(0xFFE8F5E9))
                    dose.isMissed() || dose.isOverdue() -> Triple("Missed ✗", StatusRed, Color(0xFFFFEBEE))
                    else -> Triple("Pending ◷", Color(0xFFE65100), Color(0xFFFFF8F0))
                }

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1.7f), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(medColor))
                        Spacer(Modifier.width(8.dp))
                        Text(dose.medicationName, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(timeFmt.format(Date(dose.scheduledTime)),
                        modifier = Modifier.weight(1f), fontSize = 13.sp, color = LabelGray,
                        textAlign = TextAlign.Center)
                    Box(modifier = Modifier.weight(1.3f), contentAlignment = Alignment.CenterEnd) {
                        Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                            Text(statusText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (index < doses.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                        color = DividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─── Mood + Vitals Compact Row ────────────────────────────────────────────────

@Composable
private fun MoodVitalsRow(mood: MoodRecord?, bp: String, hr: Int, spo2: Int, onRefreshVitals: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(modifier = Modifier.weight(1f), color = Color.White,
            shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mood", fontSize = 11.sp, color = LabelGray, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (mood != null) {
                    Text(mood.moodLevel().emoji, fontSize = 28.sp)
                    Text(mood.moodLevel().label, fontSize = 11.sp, color = Color(0xFF880E4F))
                } else {
                    Text("😐", fontSize = 28.sp)
                    Text("Unknown", fontSize = 11.sp, color = LabelGray)
                }
            }
        }
        Surface(modifier = Modifier.weight(1.6f), color = Color.White,
            shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vitals", fontSize = 11.sp, color = LabelGray,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefreshVitals, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Refresh, null, tint = LabelGray, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                VitalRow("❤️", "BP", bp, "mmHg")
                VitalRow("💓", "HR", "$hr", "bpm")
                VitalRow("🫁", "O₂", "$spo2", "%")
            }
        }
    }
}

@Composable
private fun VitalRow(icon: String, label: String, value: String, unit: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = LabelGray, modifier = Modifier.width(22.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(unit, fontSize = 10.sp, color = LabelGray)
    }
}

// ─── Quick Action Bar ─────────────────────────────────────────────────────────

@Composable
private fun QuickActionBar(
    monitoringEnabled: Boolean,
    onToggleMonitor: () -> Unit,
    onVoiceNote: () -> Unit,
    onEmergency: () -> Unit,
    onDetail: () -> Unit
) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionBtn(
                modifier = Modifier.weight(1f),
                label = if (monitoringEnabled) "Monitor ON" else "Monitor",
                icon = Icons.Default.Notifications,
                tint = if (monitoringEnabled) MedGreen else LabelGray,
                onClick = onToggleMonitor
            )
            ActionBtn(modifier = Modifier.weight(1f), label = "Voice Note",
                icon = Icons.Default.Mic, tint = CareBlue, onClick = onVoiceNote)
            ActionBtn(modifier = Modifier.weight(1f), label = "Emergency",
                icon = Icons.Default.Phone, tint = StatusRed, onClick = onEmergency)
            ActionBtn(modifier = Modifier.weight(1f), label = "Details",
                icon = Icons.Default.Settings, tint = LabelGray, onClick = onDetail)
        }
    }
}

@Composable
private fun ActionBtn(modifier: Modifier = Modifier, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Status Pill ──────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(level: SeniorStatusLevel) {
    val color = levelToColor(level)
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.12f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(level.label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun VoiceRecordDialog(
    title: String, hint: String, isRecording: Boolean, saved: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Record your voice — it plays when the patient needs to hear from you.",
                    fontSize = 13.sp, color = LabelGray)
                Text(hint, fontSize = 12.sp, color = CareBlue, fontStyle = FontStyle.Italic)
                if (saved) Text("✅ Voice saved!", color = MedGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Button(
                    onClick = if (isRecording) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) StatusRed else CareBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (isRecording) Icons.Default.Close else Icons.Default.Mic, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRecording) "Stop & Save" else "Start Recording",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                if (isRecording) Text("● Recording...", color = StatusRed, fontSize = 13.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontSize = 13.sp) } }
    )
}

@Composable
private fun EmergencyContactDialog(
    contactName: String, contactPhone: String,
    onSave: (String, String) -> Unit, onDismiss: () -> Unit
) {
    var name  by remember { mutableStateOf(contactName) }
    var phone by remember { mutableStateOf(contactPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency Contact", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("If the patient is inactive for 30+ minutes, this number will be called automatically.",
                    fontSize = 12.sp, color = LabelGray)
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Contact Name", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone Number", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue),
                enabled = phone.isNotBlank()) {
                Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NoSeniorLinkedCard(onLinkClick: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👴", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text("No Senior Linked", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Link a family member's account to start monitoring their medication schedule.",
                fontSize = 13.sp, color = LabelGray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLinkClick, modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Link Family Member", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LinkSeniorDialog(
    email: String, onEmailChange: (String) -> Unit, onLink: () -> Unit,
    onDismiss: () -> Unit, isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Family Member", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
        text = {
            Column {
                Text("Enter the senior's account email address.", fontSize = 13.sp, color = LabelGray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = email, onValueChange = onEmailChange,
                    label = { Text("Senior's Email", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onLink, enabled = email.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                else Text("Link Account", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun levelToColor(level: SeniorStatusLevel): Color = when (level) {
    SeniorStatusLevel.GREEN -> Color(0xFF4CAF50)
    SeniorStatusLevel.AMBER -> Color(0xFFFFA726)
    SeniorStatusLevel.RED   -> Color(0xFFF44336)
}
