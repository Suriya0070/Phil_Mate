package com.jsf.app.medication_solution.ui.caregiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jsf.app.medication_solution.service.VoiceAlarmManager
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen
import com.jsf.app.medication_solution.ui.theme.MedPalette
import com.jsf.app.medication_solution.ui.theme.StatusRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BgPage   = Color(0xFFF1F3F6)
private val BgCard   = Color.White
private val TopBar   = Color(0xFF0D1117)
private val Divider  = Color(0xFFE8ECEF)
private val TextSub  = Color(0xFF6E7681)
private val TextHead = Color(0xFF1B2333)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    viewModel: CaregiverViewModel,
    onViewSeniorDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val state   by viewModel.state.collectAsState()
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()

    var recordingAlarm by remember { mutableStateOf(false) }
    var recordingNote  by remember { mutableStateOf(false) }
    var alarmSaved     by remember { mutableStateOf(false) }
    var noteSaved      by remember { mutableStateOf(false) }

    LaunchedEffect(state.seniorSnapshot?.user?.id) {
        val sid = state.seniorSnapshot?.user?.id ?: return@LaunchedEffect
        alarmSaved = VoiceAlarmManager.isAlarmVoiceSaved(context, sid)
        noteSaved  = VoiceAlarmManager.isFamilyNoteSaved(context, sid)
    }
    state.linkSuccess?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); viewModel.clearLinkSuccess() }
    }

    // Dialogs
    if (state.linkDialogVisible) {
        LinkDialog(state.linkEmail, viewModel::onLinkEmailChanged, viewModel::linkSenior, viewModel::hideLinkDialog, state.isLoading)
    }
    if (state.showVoiceAlarmRecorder) {
        VoiceDialog(
            title = "🎙️ Record Alarm Voice", hint = "E.g. \"Thatha, medicine edunga!\"",
            recording = recordingAlarm, saved = alarmSaved,
            onStart = { recordingAlarm = true; VoiceAlarmManager.startRecording(context) },
            onStop  = {
                recordingAlarm = false
                val f = VoiceAlarmManager.stopRecording()
                val sid = state.seniorSnapshot?.user?.id ?: ""
                if (f != null && sid.isNotBlank()) scope.launch {
                    if (VoiceAlarmManager.uploadAlarmVoice(sid, f)) {
                        VoiceAlarmManager.markAlarmVoiceSaved(context, sid); alarmSaved = true
                    }
                }
            },
            onDismiss = viewModel::hideVoiceAlarmRecorder
        )
    }
    if (state.showFamilyNoteRecorder) {
        VoiceDialog(
            title = "💌 Leave a Voice Note", hint = "E.g. \"Thatha, how are you today?\"",
            recording = recordingNote, saved = noteSaved,
            onStart = { recordingNote = true; VoiceAlarmManager.startRecording(context) },
            onStop  = {
                recordingNote = false
                val f = VoiceAlarmManager.stopRecording()
                val sid = state.seniorSnapshot?.user?.id ?: ""
                if (f != null && sid.isNotBlank()) scope.launch {
                    if (VoiceAlarmManager.uploadFamilyNote(sid, f)) {
                        VoiceAlarmManager.markFamilyNoteSaved(context, sid); noteSaved = true
                    }
                }
            },
            onDismiss = viewModel::hideFamilyNoteRecorder
        )
    }
    if (state.showEmergencyDialog) {
        EmergencyDialog(
            name = state.emergencyContact, phone = state.emergencyContactPhone,
            onSave = { n, p ->
                context.getSharedPreferences("medicare_prefs", Context.MODE_PRIVATE)
                    .edit().putString("family_phone", p).apply()
                viewModel.saveEmergencyContact(n, p)
            },
            onDismiss = viewModel::hideEmergencyDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MediCare", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Text("Caregiver Dashboard", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showLinkDialog) { Icon(Icons.Default.Add, "Link", tint = Color.White) }
                    IconButton(onClick = onLogout)                  { Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBar)
            )
        },
        containerColor = BgPage
    ) { padding ->

        if (state.isLoading && state.seniorSnapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CareBlue) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Success banner
            state.linkSuccess?.let {
                item {
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                        Text("✅ $it", Modifier.padding(12.dp), color = MedGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item { NoSeniorCard(viewModel::showLinkDialog) }
                return@LazyColumn
            }

            val snap = state.seniorSnapshot!!

            // ── Patient header row ────────────────────────────────────────
            item {
                Surface(color = BgCard, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape)
                            .background(levelToColor(snap.statusLevel).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center) { Text("👴", fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(snap.user.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextHead)
                            val lastSeen = when {
                                snap.inactiveMinutes < 1  -> "Active now"
                                snap.inactiveMinutes < 60 -> "${snap.inactiveMinutes} min ago"
                                else                      -> "${snap.inactiveMinutes / 60}h ago"
                            }
                            Text(lastSeen, fontSize = 12.sp, color = if (snap.inactiveMinutes > 30) Color(0xFFE65100) else TextSub)
                        }
                        StatusPill(snap.statusLevel)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onViewSeniorDetail(snap.user.id) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Settings, null, tint = TextSub, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Inactivity alert ─────────────────────────────────────────
            if (snap.inactiveMinutes > 30 && state.emergencyContactPhone.isNotBlank()) {
                item {
                    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("No activity for ${snap.inactiveMinutes} min",
                                fontSize = 13.sp, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${state.emergencyContactPhone}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                                colors = ButtonDefaults.textButtonColors(contentColor = StatusRed),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Call", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                }
            }

            // ── KPI chips ────────────────────────────────────────────────
            item {
                val taken   = snap.todayDoses.count { it.isTaken() }
                val missed  = snap.todayDoses.count { it.isMissed() || it.isOverdue() }
                val pending = snap.todayDoses.size - taken - missed
                val total   = snap.todayDoses.size
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard(Modifier.weight(1f), "$taken",  "Taken",   MedGreen,           Color(0xFFE8F5E9))
                    KpiCard(Modifier.weight(1f), "$pending","Pending", Color(0xFFE65100),  Color(0xFFFFF3E0))
                    KpiCard(Modifier.weight(1f), "$missed", "Missed",  StatusRed,          Color(0xFFFFEBEE))
                    KpiCard(Modifier.weight(1f), "$total",  "Total",   CareBlue,           Color(0xFFE3F2FD))
                }
            }

            // ── Medicine status table ─────────────────────────────────────
            item { SectionLabel("Today's Medicines") }
            item { MedicineTable(snap.todayDoses, state.medications) }

            // ── Action row ───────────────────────────────────────────────
            item {
                Surface(color = BgCard, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        ActionChip(Modifier.weight(1f), "💌 Voice Note",  CareBlue)  { viewModel.showFamilyNoteRecorder() }
                        ActionChip(Modifier.weight(1f), "🎙️ Alarm Voice", TextSub)   { viewModel.showVoiceAlarmRecorder() }
                        ActionChip(Modifier.weight(1f), "🚨 SOS Contact", StatusRed) { viewModel.showEmergencyDialog() }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ─── Medicine Table ───────────────────────────────────────────────────────────

@Composable
private fun MedicineTable(doses: List<DoseRecord>, medications: List<Medication>) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    if (doses.isEmpty()) {
        Surface(color = BgCard, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No medicines scheduled today", fontSize = 13.sp, color = TextSub)
            }
        }
        return
    }

    // Group doses by time slot
    data class Slot(val label: String, val emoji: String, val range: IntRange)
    val slots = listOf(
        Slot("Morning",   "🌅", 6..11),
        Slot("Afternoon", "☀️", 12..16),
        Slot("Evening",   "🌆", 17..20),
        Slot("Night",     "🌙", 21..29)
    )

    Surface(color = BgCard, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(Modifier.fillMaxWidth().background(Color(0xFFF0F3FF)).padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text("Medicine",   Modifier.weight(1.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CareBlue)
                Text("Time",       Modifier.weight(1f),   fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CareBlue, textAlign = TextAlign.Center)
                Text("Status",     Modifier.weight(1.3f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CareBlue, textAlign = TextAlign.End)
            }

            var firstRow = true
            for (slot in slots) {
                val slotDoses = doses.filter { dose ->
                    val cal = Calendar.getInstance(); cal.timeInMillis = dose.scheduledTime
                    val h = cal.get(Calendar.HOUR_OF_DAY)
                    (if (h < 6) h + 24 else h) in slot.range
                }
                if (slotDoses.isEmpty()) continue

                // Slot sub-header
                if (!firstRow) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Divider, thickness = 0.5.dp)
                firstRow = false
                Row(Modifier.fillMaxWidth().background(Color(0xFFFAFBFC)).padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${slot.emoji} ${slot.label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = TextSub)
                    Spacer(Modifier.weight(1f))
                    Text("${slotDoses.size} med${if (slotDoses.size > 1) "s" else ""}",
                        fontSize = 11.sp, color = TextSub)
                }

                slotDoses.forEachIndexed { idx, dose ->
                    val med = medications.find { it.id == dose.medicationId }
                    val dotColor = MedPalette.colorForMedication(med?.pillColorHex ?: "#4CAF50", med?.colorIndex ?: -1)
                    val (statusText, statusColor, statusBg) = when {
                        dose.isTaken()  -> Triple("Taken ✓", MedGreen, Color(0xFFE8F5E9))
                        dose.isMissed() || dose.isOverdue() -> Triple("Missed ✗", StatusRed, Color(0xFFFFEBEE))
                        else -> Triple("Pending", Color(0xFFE65100), Color(0xFFFFF8F0))
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Divider, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1.8f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                            Spacer(Modifier.width(8.dp))
                            Text(dose.medicationName, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(timeFmt.format(Date(dose.scheduledTime)),
                            Modifier.weight(1f), fontSize = 12.sp, color = TextSub, textAlign = TextAlign.Center)
                        Box(Modifier.weight(1.3f), contentAlignment = Alignment.CenterEnd) {
                            Surface(shape = RoundedCornerShape(20.dp), color = statusBg) {
                                Text(statusText, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Small helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSub,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
}

@Composable
private fun KpiCard(modifier: Modifier, value: String, label: String, color: Color, bg: Color) {
    Surface(modifier = modifier, color = bg, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(label, fontSize = 10.sp, color = TextSub, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionChip(modifier: Modifier, label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(level: SeniorStatusLevel) {
    val c = levelToColor(level)
    Surface(shape = RoundedCornerShape(20.dp), color = c.copy(alpha = 0.1f)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(4.dp))
            Text(level.label, fontSize = 11.sp, color = c, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NoSeniorCard(onLink: () -> Unit) {
    Surface(color = BgCard, shape = RoundedCornerShape(14.dp), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👴", fontSize = 48.sp)
            Spacer(Modifier.height(10.dp))
            Text("No Senior Linked", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextHead)
            Spacer(Modifier.height(4.dp))
            Text("Link a family member's account to monitor their medication.", fontSize = 13.sp,
                color = TextSub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onLink, modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Link Family Member", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun VoiceDialog(
    title: String, hint: String, recording: Boolean, saved: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Plays for the patient when needed.", fontSize = 12.sp, color = TextSub)
                Text(hint, fontSize = 12.sp, color = CareBlue, fontStyle = FontStyle.Italic)
                if (saved) Text("✅ Saved!", color = MedGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Button(onClick = if (recording) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = if (recording) StatusRed else CareBlue),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(if (recording) Icons.Default.Close else Icons.Default.Mic, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(if (recording) "Stop & Save" else "Start Recording", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (recording) Text("● Recording...", color = StatusRed, fontSize = 12.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontSize = 13.sp) } }
    )
}

@Composable
private fun EmergencyDialog(name: String, phone: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var p by remember { mutableStateOf(phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency Contact", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Called automatically if patient is inactive for 30+ min.", fontSize = 12.sp, color = TextSub)
                OutlinedTextField(n, { n = it }, label = { Text("Name", fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Phone", fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(n, p) }, colors = ButtonDefaults.buttonColors(containerColor = CareBlue), enabled = p.isNotBlank()) {
                Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LinkDialog(email: String, onChange: (String) -> Unit, onLink: () -> Unit, onDismiss: () -> Unit, loading: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Family Member", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column {
                Text("Enter the senior's account email.", fontSize = 12.sp, color = TextSub)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email, onChange, label = { Text("Email", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onLink, enabled = email.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue)) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                else Text("Link", color = Color.White, fontWeight = FontWeight.SemiBold)
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
