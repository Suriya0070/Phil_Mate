package com.jsf.app.medication_solution.ui.caregiver

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.jsf.app.medication_solution.MedApp
import com.jsf.app.medication_solution.data.model.Alert
import com.jsf.app.medication_solution.data.model.AlertType
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.repository.DayAdherence
import com.jsf.app.medication_solution.service.VoiceAlarmManager
import com.jsf.app.medication_solution.ui.theme.MedPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Dark command-center palette ───────────────────────────────────────────────
private val D_BG     = Color(0xFF0D1117)
private val D_CARD   = Color(0xFF161B22)
private val D_CARD2  = Color(0xFF21262D)
private val D_BORDER = Color(0xFF30363D)
private val D_NAV    = Color(0xFF010409)
private val D_TEXT   = Color(0xFFF0F6FC)
private val D_SUB    = Color(0xFF8B949E)
private val D_BLUE   = Color(0xFF58A6FF)
private val D_GREEN  = Color(0xFF3FB950)
private val D_AMBER  = Color(0xFFD29922)
private val D_RED    = Color(0xFFF85149)
private val D_PURP   = Color(0xFFBC8CFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    viewModel: CaregiverViewModel,
    onViewSeniorDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val state    by viewModel.state.collectAsState()
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()

    var recordingAlarm by remember { mutableStateOf(false) }
    var recordingNote  by remember { mutableStateOf(false) }
    var alarmSaved     by remember { mutableStateOf(false) }
    var noteSaved      by remember { mutableStateOf(false) }

    val seniorId = state.seniorSnapshot?.user?.id ?: ""

    LaunchedEffect(seniorId) {
        if (seniorId.isNotBlank()) {
            alarmSaved = VoiceAlarmManager.isAlarmVoiceSaved(context, seniorId)
            noteSaved  = VoiceAlarmManager.isFamilyNoteSaved(context, seniorId)
        }
    }
    LaunchedEffect(state.newMissedAlert) {
        val alert = state.newMissedAlert ?: return@LaunchedEffect
        showMissedDoseNotification(context, alert.medicationName)
        viewModel.clearNewMissedAlert()
    }
    state.linkSuccess?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); viewModel.clearLinkSuccess() }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (state.showAddMedDialog) {
        AddMedicineDialog(
            onAdd = { name, purpose, dosage, times ->
                viewModel.addMedication(name, dosage, purpose, times, "", "💊", "#4CAF50")
            },
            onDismiss = viewModel::hideAddMedDialog
        )
    }
    if (state.linkDialogVisible) {
        LinkDialog(state.linkEmail, viewModel::onLinkEmailChanged,
            viewModel::linkSenior, viewModel::hideLinkDialog, state.isLoading)
    }
    if (state.showFamilyNoteRecorder) {
        VoiceNoteDialog(
            recording = recordingNote, saved = noteSaved,
            onStart = { recordingNote = true; VoiceAlarmManager.startRecording(context) },
            onStop  = {
                recordingNote = false
                val f = VoiceAlarmManager.stopRecording()
                if (f != null && seniorId.isNotBlank()) scope.launch {
                    if (VoiceAlarmManager.uploadFamilyNote(seniorId, f)) {
                        VoiceAlarmManager.markFamilyNoteSaved(context, seniorId)
                        noteSaved = true
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

    // ── Full-screen dark layout ───────────────────────────────────────────────
    Column(Modifier.fillMaxSize().background(D_BG)) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().background(D_NAV)) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(D_GREEN))
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🩺", fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("MediCare", fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp, color = D_TEXT)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(D_GREEN.copy(0.2f))
                                .border(1.dp, D_GREEN.copy(0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("CAREGIVER", fontSize = 8.sp, color = D_GREEN,
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                    Text("COMMAND CENTER",
                        fontSize = 9.sp, color = D_GREEN, letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold)
                }
                state.seniorSnapshot?.let { snap ->
                    val lc = statusColor(snap.statusLevel)
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(lc.copy(0.15f))
                            .border(1.dp, lc.copy(0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(lc))
                        Spacer(Modifier.width(5.dp))
                        Text(snap.statusLevel.label, fontSize = 10.sp, color = lc,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = viewModel::showLinkDialog) {
                    Icon(Icons.Default.Add, "Link", tint = D_BLUE)
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.ExitToApp, "Logout", tint = D_SUB)
                }
            }
        }

        // ── Scrollable content ────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(D_BG),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            state.linkSuccess?.let {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(D_GREEN.copy(0.15f))
                            .border(1.dp, D_GREEN.copy(0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) { Text("✅ $it", color = D_GREEN, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // DAILY BRIEF — patient summary (only when patient linked)
            // ══════════════════════════════════════════════════════════════════
            if (seniorId.isNotBlank() && state.seniorSnapshot != null) {
                item {
                    DailyBriefCard(state = state, snap = state.seniorSnapshot!!)
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // FEATURE 1 — ALARM VOICE RECORDING (HERO, always at top)
            // ══════════════════════════════════════════════════════════════════
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (alarmSaved) D_GREEN.copy(0.08f) else D_BLUE.copy(0.06f))
                        .border(
                            2.dp,
                            when { recordingAlarm -> D_RED; alarmSaved -> D_GREEN; else -> D_BLUE },
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(D_BLUE.copy(0.2f)),
                            contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 22.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ALARM VOICE RECORDING",
                                fontSize = 11.sp, color = D_BLUE, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp)
                            Text(
                                if (alarmSaved) "✅ Saved — plays on patient phone at every alarm"
                                else if (!seniorId.isNotBlank()) "Link a patient account first"
                                else "Plays when patient's medicine alarm rings",
                                fontSize = 11.sp, color = D_SUB, lineHeight = 15.sp
                            )
                        }
                    }
                    if (recordingAlarm) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(D_RED.copy(0.15f))
                                .border(1.dp, D_RED.copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(D_RED))
                            Spacer(Modifier.width(8.dp))
                            Text("● RECORDING — speak your message now",
                                color = D_RED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (recordingAlarm) {
                                recordingAlarm = false
                                val f = VoiceAlarmManager.stopRecording()
                                if (f != null && seniorId.isNotBlank()) scope.launch {
                                    if (VoiceAlarmManager.uploadAlarmVoice(seniorId, f)) {
                                        VoiceAlarmManager.markAlarmVoiceSaved(context, seniorId)
                                        alarmSaved = true
                                    }
                                }
                            } else {
                                recordingAlarm = true
                                VoiceAlarmManager.startRecording(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when { recordingAlarm -> D_RED; alarmSaved -> D_GREEN; else -> D_BLUE }
                        )
                    ) {
                        Icon(if (recordingAlarm) Icons.Default.Close else Icons.Default.Mic,
                            null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when { recordingAlarm -> "Stop & Save Recording"; alarmSaved -> "✅ Saved  ·  Tap to Re-Record"; else -> "Tap to Record Alarm Voice" },
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // FEATURE 2 — PATIENT MEDICINES (HERO, always visible)
            // ══════════════════════════════════════════════════════════════════
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(D_PURP.copy(0.06f))
                        .border(2.dp, D_PURP, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(D_PURP.copy(0.18f)),
                            contentAlignment = Alignment.Center) { Text("💊", fontSize = 22.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("PATIENT MEDICINES",
                                fontSize = 11.sp, color = D_PURP, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp)
                            Text("Add here → appears instantly on patient's phone",
                                fontSize = 11.sp, color = D_SUB)
                        }
                    }
                    if (state.medications.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(D_BORDER))
                        Spacer(Modifier.height(6.dp))
                        state.medications.forEachIndexed { index, med ->
                            val times = med.scheduleTimes.joinToString(", ")
                            val purposePart = if (med.purpose.isNotBlank()) " — ${med.purpose}" else ""
                            Text(
                                "${index + 1}. ${med.name}  ${med.dosage}  ·  $times$purposePart",
                                fontSize = 13.sp,
                                color = D_TEXT,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (!seniorId.isNotBlank()) "Link a patient first, then add medicines"
                            else "No medicines yet — add the first one below",
                            fontSize = 11.sp, color = D_SUB, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { if (seniorId.isNotBlank()) viewModel.showAddMedDialog() else viewModel.showLinkDialog() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = D_PURP)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Medicine for Patient", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // FEATURE 3 — PER-MEDICINE VOICE (each tablet gets its own audio)
            // ══════════════════════════════════════════════════════════════════
            if (state.medications.isNotEmpty() && seniorId.isNotBlank()) {
                item {
                    PerMedicineAudioSection(
                        medications = state.medications,
                        seniorId = seniorId,
                        scope = scope,
                        context = context
                    )
                }
            }

            // ── No patient linked ──────────────────────────────────────────────
            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(D_CARD).border(1.dp, D_BORDER, RoundedCornerShape(14.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👴", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No Patient Linked", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = D_TEXT)
                        Text("Link a family member to monitor their medicines.",
                            fontSize = 12.sp, color = D_SUB, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::showLinkDialog,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = D_BLUE)) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Link Patient Account", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
                return@LazyColumn
            }

            val snap = state.seniorSnapshot!!

            // ── Patient overview ───────────────────────────────────────────────
            item { SectionLabel("👴  PATIENT STATUS") }
            item { PatientOverviewCard(snap, state) }

            item {
                val taken   = snap.todayDoses.count { it.isTaken() }
                val missed  = snap.todayDoses.count { it.isMissed() || it.isOverdue() }
                val pending = snap.todayDoses.size - taken - missed
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(Modifier.weight(1f), "$taken",   "TAKEN",   D_GREEN)
                    StatCard(Modifier.weight(1f), "$pending", "PENDING", D_AMBER)
                    StatCard(Modifier.weight(1f), "$missed",  "MISSED",  D_RED)
                    StatCard(Modifier.weight(1f),
                        if (state.streak > 0) "${state.streak}d" else "${snap.todayDoses.size}",
                        if (state.streak > 0) "STREAK" else "TOTAL",
                        if (state.streak > 0) D_PURP else D_BLUE)
                }
            }

            item { SectionLabel("📅  TODAY'S SCHEDULE") }
            item { ScheduleTimeline(snap.todayDoses, state.medications) }

            if (state.weeklyChart.isNotEmpty()) {
                item { SectionLabel("📊  7-DAY TREND") }
                item { WeeklyChart(state.weeklyChart) }
            }

            val recentAlerts = state.alerts.filter { !it.isResolved }.take(5)
            if (recentAlerts.isNotEmpty()) {
                item { SectionLabel("🔔  RECENT ALERTS") }
                item { AlertsList(recentAlerts) }
            }

            item { SectionLabel("⚡  QUICK ACTIONS") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(Modifier.weight(1f), "💌", "Voice\nNote",   viewModel::showFamilyNoteRecorder)
                    ActionTile(Modifier.weight(1f), "🚨", "SOS\nContact", viewModel::showEmergencyDialog)
                    ActionTile(Modifier.weight(1f), "📊", "Vitals",        viewModel::simulateVitals)
                    ActionTile(Modifier.weight(1f), "⏰", "Set\nAlarms",  { viewModel.scheduleMedicationAlarms(context) })
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
        color = D_SUB, letterSpacing = 1.sp)
}

@Composable
private fun PatientOverviewCard(snap: SeniorSnapshot, state: CaregiverUiState) {
    val taken = snap.todayDoses.count { it.isTaken() }
    val total = snap.todayDoses.size
    val pct   = if (total == 0) 0f else taken.toFloat() / total
    val lc    = statusColor(snap.statusLevel)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(D_CARD).border(1.dp, D_BORDER, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(lc.copy(0.12f))
                .border(2.dp, lc.copy(0.4f), CircleShape), contentAlignment = Alignment.Center) {
                Text("👴", fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(snap.user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = D_TEXT)
                val lastSeen = when {
                    snap.inactiveMinutes < 1  -> "Active now"
                    snap.inactiveMinutes < 60 -> "${snap.inactiveMinutes}m ago"
                    else -> "${snap.inactiveMinutes / 60}h ago"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape)
                        .background(if (snap.inactiveMinutes < 5) D_GREEN else D_AMBER))
                    Spacer(Modifier.width(4.dp))
                    Text(lastSeen, fontSize = 11.sp, color = if (snap.inactiveMinutes > 30) D_RED else D_SUB)
                }
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { pct }, modifier = Modifier.size(52.dp),
                    color = lc, trackColor = D_BORDER, strokeWidth = 5.dp)
                Text("$taken/$total", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = D_TEXT)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(D_BORDER))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            VitalBox(Modifier.weight(1f), "BP",    state.vitalBP)
            Box(Modifier.width(0.5.dp).height(32.dp).align(Alignment.CenterVertically).background(D_BORDER))
            VitalBox(Modifier.weight(1f), "HR",    "${state.vitalHR} bpm")
            Box(Modifier.width(0.5.dp).height(32.dp).align(Alignment.CenterVertically).background(D_BORDER))
            VitalBox(Modifier.weight(1f), "SpO2",  "${state.vitalSpO2}%")
            Box(Modifier.width(0.5.dp).height(32.dp).align(Alignment.CenterVertically).background(D_BORDER))
            VitalBox(Modifier.weight(1f), "Status", snap.statusLevel.emoji)
        }
    }
}

@Composable
private fun VitalBox(modifier: Modifier, label: String, value: String) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = D_SUB, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, color = D_TEXT, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(0.1f))
        .border(1.dp, color.copy(0.3f), RoundedCornerShape(12.dp))
        .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
        Text(label, fontSize = 9.sp, color = D_SUB, fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScheduleTimeline(doses: List<DoseRecord>, medications: List<Medication>) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    if (doses.isEmpty()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D_CARD)
            .border(1.dp, D_BORDER, RoundedCornerShape(12.dp)).padding(24.dp),
            contentAlignment = Alignment.Center) {
            Text("No medicines scheduled today", fontSize = 13.sp, color = D_SUB)
        }
        return
    }
    data class Slot(val label: String, val icon: String, val range: IntRange)
    val slots = listOf(
        Slot("Morning","🌅",6..11), Slot("Afternoon","☀️",12..16),
        Slot("Evening","🌆",17..20), Slot("Night","🌙",21..29))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D_CARD)
        .border(1.dp, D_BORDER, RoundedCornerShape(12.dp)).padding(16.dp)) {
        var first = true
        for (slot in slots) {
            val slotDoses = doses.filter { d ->
                val c = Calendar.getInstance(); c.timeInMillis = d.scheduledTime
                val h = c.get(Calendar.HOUR_OF_DAY)
                (if (h < 6) h + 24 else h) in slot.range
            }
            if (slotDoses.isEmpty()) continue
            if (!first) Spacer(Modifier.height(14.dp))
            first = false
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(slot.icon, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
                Text(slot.label.uppercase(), fontSize = 9.sp, color = D_SUB,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(0.5.dp).background(D_BORDER))
            }
            Spacer(Modifier.height(8.dp))
            slotDoses.forEachIndexed { idx, dose ->
                val isLast = idx == slotDoses.size - 1
                val med    = medications.find { it.id == dose.medicationId }
                val dot    = MedPalette.colorForMedication(med?.pillColorHex ?: "#4CAF50", med?.colorIndex ?: -1)
                val (statusLabel, statusColor, statusBg) = when {
                    dose.isTaken()                      -> Triple("TAKEN",   D_GREEN, D_GREEN.copy(0.12f))
                    dose.isMissed() || dose.isOverdue() -> Triple("MISSED",  D_RED,   D_RED.copy(0.12f))
                    else                                 -> Triple("PENDING", D_AMBER, D_AMBER.copy(0.12f))
                }
                val takenStr = if (dose.isTaken() && dose.takenAt > 0)
                    "  ·  ${timeFmt.format(Date(dose.takenAt))}" else ""
                Row(Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                        if (!isLast) Box(Modifier.width(1.5.dp).height(36.dp).background(D_BORDER))
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dose.medicationName, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = D_TEXT,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${timeFmt.format(Date(dose.scheduledTime))}$takenStr",
                                fontSize = 10.sp, color = D_SUB)
                        }
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(statusBg)
                            .border(0.5.dp, statusColor.copy(0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(statusLabel, fontSize = 9.sp, color = statusColor,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyChart(data: List<DayAdherence>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D_CARD)
        .border(1.dp, D_BORDER, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().height(70.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom) {
            data.forEach { day ->
                val pct = if (day.total == 0) 0f else day.taken.toFloat() / day.total
                val bc  = when { day.total == 0 -> D_BORDER; pct >= 1f -> D_GREEN; pct >= 0.5f -> D_AMBER; else -> D_RED }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom) {
                    val bh = ((pct * 48).toInt().coerceAtLeast(if (day.total > 0) 3 else 2)).dp
                    Box(Modifier.fillMaxWidth().height(bh)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(bc))
                    Spacer(Modifier.height(3.dp))
                    Text(day.dayLabel, fontSize = 8.sp, color = D_SUB)
                    Text(if (day.total > 0) "${(pct * 100).toInt()}%" else "–",
                        fontSize = 7.sp, color = if (pct >= 1f) D_GREEN else D_SUB)
                }
            }
        }
    }
}

@Composable
private fun AlertsList(alerts: List<Alert>) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(D_CARD)
        .border(1.dp, D_BORDER, RoundedCornerShape(12.dp)).padding(10.dp)) {
        alerts.forEachIndexed { idx, alert ->
            val type  = alert.alertType()
            val color = when (type) {
                AlertType.MISSED_DOSE, AlertType.MOOD_LOW -> D_RED
                AlertType.INACTIVITY                      -> D_AMBER
                AlertType.DOSE_CONFIRMED                  -> D_GREEN
                else                                       -> D_SUB
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(0.12f)),
                    contentAlignment = Alignment.Center) { Text(type.emoji, fontSize = 14.sp) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
                    Text(alert.medicationName.ifBlank { timeFmt.format(Date(alert.timestamp)) },
                        fontSize = 10.sp, color = D_SUB, maxLines = 1)
                }
            }
            if (idx < alerts.size - 1) Box(Modifier.fillMaxWidth().height(0.5.dp).background(D_BORDER))
        }
    }
}

@Composable
private fun ActionTile(modifier: Modifier, emoji: String, label: String, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }
        .background(D_CARD).border(1.dp, D_BORDER, RoundedCornerShape(12.dp))
        .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(emoji, fontSize = 22.sp)
        Text(label, fontSize = 9.sp, color = D_SUB, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun statusColor(level: SeniorStatusLevel) = when (level) {
    SeniorStatusLevel.GREEN -> D_GREEN
    SeniorStatusLevel.AMBER -> D_AMBER
    SeniorStatusLevel.RED   -> D_RED
}

@Composable
private fun VoiceNoteDialog(recording: Boolean, saved: Boolean,
                             onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = D_CARD2,
        title = { Text("💌 Leave a Voice Note", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = D_TEXT) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Plays for the patient when they open the app.", fontSize = 12.sp, color = D_SUB)
                if (saved) Text("✅ Saved!", color = D_GREEN, fontWeight = FontWeight.Bold)
                Button(onClick = if (recording) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = if (recording) D_RED else D_BLUE),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(if (recording) Icons.Default.Close else Icons.Default.Mic, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(if (recording) "Stop & Save" else "Start Recording", color = Color.White)
                }
                if (recording) Text("● Recording...", color = D_RED, fontSize = 12.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = D_SUB) } }
    )
}

@Composable
private fun EmergencyDialog(name: String, phone: String,
                             onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var p by remember { mutableStateOf(phone) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = D_CARD2,
        title = { Text("Emergency Contact", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = D_TEXT) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Called automatically if patient is inactive for 30+ min.", fontSize = 12.sp, color = D_SUB)
                OutlinedTextField(n, { n = it }, label = { Text("Name", color = D_SUB) },
                    textStyle = LocalTextStyle.current.copy(color = D_TEXT),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Phone", color = D_SUB) },
                    textStyle = LocalTextStyle.current.copy(color = D_TEXT),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(n, p) }, enabled = p.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = D_BLUE)) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = D_SUB) } }
    )
}

@Composable
private fun LinkDialog(email: String, onChange: (String) -> Unit,
                       onLink: () -> Unit, onDismiss: () -> Unit, loading: Boolean) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = D_CARD2,
        title = { Text("Link Patient", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = D_TEXT) },
        text = {
            Column {
                Text("Enter the patient's account email.", fontSize = 12.sp, color = D_SUB)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email, onChange, label = { Text("Email", color = D_SUB) },
                    textStyle = LocalTextStyle.current.copy(color = D_TEXT),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onLink, enabled = email.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = D_BLUE)) {
                if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                else Text("Link", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = D_SUB) } }
    )
}

@Composable
private fun AddMedicineDialog(
    onAdd: (name: String, purpose: String, dosage: String, times: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name    by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var dosage  by remember { mutableStateOf("1 tab") }
    val slots   = remember { mutableStateMapOf("07:00" to false, "13:00" to false, "18:00" to false, "21:00" to false) }
    val slotLabels = linkedMapOf(
        "07:00" to "🌅 Morning   7:00 AM",
        "13:00" to "☀️ Afternoon  1:00 PM",
        "18:00" to "🌆 Evening   6:00 PM",
        "21:00" to "🌙 Night     9:00 PM"
    )
    AlertDialog(onDismissRequest = onDismiss, containerColor = D_CARD2,
        title = { Text("💊 Add Medicine for Patient", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = D_TEXT) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Medicine Name *", color = D_SUB) },
                    textStyle = LocalTextStyle.current.copy(color = D_TEXT),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose / Condition", color = D_SUB) },
                    textStyle = LocalTextStyle.current.copy(color = D_TEXT),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dosage, { dosage = it }, label = { Text("Dosage (e.g. 1 tab)", color = D_SUB) },
                    textStyle = LocalTextStyle.current.copy(color = D_TEXT),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("When to give: *", fontSize = 12.sp, color = D_SUB, fontWeight = FontWeight.Medium)
                slotLabels.forEach { (time, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = slots[time] == true, onCheckedChange = { slots[time] = it },
                            colors = CheckboxDefaults.colors(checkedColor = D_BLUE, uncheckedColor = D_SUB))
                        Text(label, fontSize = 13.sp, color = D_TEXT)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val times = slots.filterValues { it }.keys.sorted()
                    if (name.isNotBlank() && times.isNotEmpty())
                        onAdd(name.trim(), purpose.trim(), dosage.trim(), times.toList())
                },
                enabled = name.isNotBlank() && slots.any { it.value },
                colors = ButtonDefaults.buttonColors(containerColor = D_PURP)
            ) { Text("Add Medicine", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = D_SUB) } }
    )
}

// ── Per-Medicine Audio Section ────────────────────────────────────────────────

@Composable
private fun PerMedicineAudioSection(
    medications: List<com.jsf.app.medication_solution.data.model.Medication>,
    seniorId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context
) {
    var recordingForMedId by remember { mutableStateOf<String?>(null) }
    val savedIds = remember { mutableStateOf(
        medications.map { it.id }.filter {
            com.jsf.app.medication_solution.service.VoiceAlarmManager.isMedicineAudioSaved(context, seniorId, it)
        }.toSet()
    )}

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A1628))
            .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFFFF9800).copy(0.18f)),
                contentAlignment = Alignment.Center) { Text("🎵", fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("MEDICINE VOICE RECORDINGS",
                    fontSize = 10.sp, color = Color(0xFFFF9800),
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("Record your voice for each tablet's alarm",
                    fontSize = 11.sp, color = D_SUB)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(D_BORDER))
        Spacer(Modifier.height(8.dp))

        medications.forEach { med ->
            val isRecording = recordingForMedId == med.id
            val isSaved = med.id in savedIds.value

            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(med.name, fontSize = 13.sp, color = D_TEXT, fontWeight = FontWeight.SemiBold)
                    Text(med.dosage + "  ·  " + med.scheduleTimes.joinToString(", "),
                        fontSize = 10.sp, color = D_SUB)
                }
                Spacer(Modifier.width(8.dp))
                if (isRecording) {
                    Button(
                        onClick = {
                            recordingForMedId = null
                            val f = com.jsf.app.medication_solution.service.VoiceAlarmManager.stopRecording()
                            if (f != null) scope.launch {
                                if (com.jsf.app.medication_solution.service.VoiceAlarmManager.uploadMedicineAudio(seniorId, med.id, f)) {
                                    com.jsf.app.medication_solution.service.VoiceAlarmManager.markMedicineAudioSaved(context, seniorId, med.id)
                                    savedIds.value = savedIds.value + med.id
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = D_RED),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("⏹ Stop", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            recordingForMedId = med.id
                            com.jsf.app.medication_solution.service.VoiceAlarmManager.startRecording(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) Color(0xFF388E3C) else Color(0xFFFF9800)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            if (isSaved) "✅ Re-Record" else "🎙 Record",
                            fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(D_BORDER.copy(0.3f)))
        }

        if (recordingForMedId != null) {
            Spacer(Modifier.height(8.dp))
            val recMed = medications.find { it.id == recordingForMedId }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(D_RED.copy(0.15f))
                    .border(1.dp, D_RED.copy(0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(D_RED))
                Spacer(Modifier.width(8.dp))
                Text("● Recording for ${recMed?.name ?: ""} — speak now",
                    color = D_RED, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Daily Patient Brief ───────────────────────────────────────────────────────

@Composable
private fun DailyBriefCard(state: CaregiverUiState, snap: SeniorSnapshot) {
    val taken   = snap.todayDoses.count { it.isTaken() }
    val missed  = snap.todayDoses.count { it.isMissed() || it.isOverdue() }
    val pending = snap.todayDoses.size - taken - missed
    val todayPct = if (snap.todayDoses.isEmpty()) 0 else taken * 100 / snap.todayDoses.size
    val dateFmt  = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    val timeFmt  = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val nextDose = snap.todayDoses
        .filter { !it.isTaken() && !it.isMissed() && !it.isOverdue() }
        .minByOrNull { it.scheduledTime }
    val weekPct = if (state.weeklyChart.isEmpty()) 0 else {
        val t = state.weeklyChart.sumOf { it.total }
        val k = state.weeklyChart.sumOf { it.taken }
        if (t == 0) 0 else k * 100 / t
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(D_GREEN.copy(0.06f))
            .border(2.dp, D_GREEN, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(D_GREEN.copy(0.18f)),
                contentAlignment = Alignment.Center) { Text("📋", fontSize = 22.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TODAY'S PATIENT BRIEF", fontSize = 10.sp, color = D_GREEN,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text(dateFmt.format(Date()), fontSize = 11.sp, color = D_SUB)
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { todayPct / 100f }, modifier = Modifier.size(48.dp),
                    color = if (todayPct >= 80) D_GREEN else if (todayPct >= 50) D_AMBER else D_RED,
                    trackColor = D_BORDER, strokeWidth = 5.dp
                )
                Text("$todayPct%", fontSize = 10.sp, color = D_TEXT, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(D_BORDER))
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👴", fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(snap.user.name, fontSize = 13.sp, color = D_TEXT, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            val ago = when {
                snap.inactiveMinutes < 1  -> "Active now"
                snap.inactiveMinutes < 60 -> "${snap.inactiveMinutes}m ago"
                else                      -> "${snap.inactiveMinutes / 60}h ago"
            }
            val dotColor = if (snap.inactiveMinutes < 10) D_GREEN else if (snap.inactiveMinutes < 30) D_AMBER else D_RED
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(4.dp))
            Text(ago, fontSize = 10.sp, color = D_SUB)
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BriefChip(Modifier.weight(1f), "✅", "$taken",   "Taken",   D_GREEN)
            BriefChip(Modifier.weight(1f), "⏳", "$pending", "Pending", D_AMBER)
            BriefChip(Modifier.weight(1f), "❌", "$missed",  "Missed",  D_RED)
        }

        if (nextDose != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(D_BLUE.copy(0.08f))
                    .border(1.dp, D_BLUE.copy(0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⏰", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("Next: ${nextDose.medicationName}  ·  ${timeFmt.format(Date(nextDose.scheduledTime))}",
                    fontSize = 12.sp, color = D_BLUE, fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.weeklyChart.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(D_PURP.copy(0.08f))
                    .border(1.dp, D_PURP.copy(0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text("7-day: $weekPct% adherence  ·  ${state.streak}d streak",
                    fontSize = 12.sp, color = D_PURP, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BriefChip(modifier: Modifier, emoji: String, value: String, label: String, color: Color) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(0.1f))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(value, fontSize = 17.sp, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 9.sp, color = D_SUB)
    }
}

private fun showMissedDoseNotification(context: Context, medName: String) {
    val nm = context.getSystemService(NotificationManager::class.java)
    val n  = NotificationCompat.Builder(context, MedApp.CHANNEL_ALERT)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("⚠️ Missed Dose")
        .setContentText("$medName was not taken on time!")
        .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
    nm.notify(medName.hashCode() + 9000, n)
}
