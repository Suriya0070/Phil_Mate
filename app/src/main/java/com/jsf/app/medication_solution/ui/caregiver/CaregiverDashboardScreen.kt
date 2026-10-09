package com.jsf.app.medication_solution.ui.caregiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontStyle
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

// ─── Dark professional palette — completely distinct from patient's light UI ──
private val PageBg   = Color(0xFF0D1117)   // near-black page background
private val CardBg   = Color(0xFF161B22)   // dark card surface
private val CardBg2  = Color(0xFF21262D)   // slightly raised card
private val BorderC  = Color(0xFF30363D)   // subtle borders
private val NavBg    = Color(0xFF010409)   // very dark top bar
private val TextPri  = Color(0xFFF0F6FC)   // near-white text
private val TextSec  = Color(0xFF8B949E)   // muted text
private val AccBlue  = Color(0xFF58A6FF)   // blue accent
private val AccGrn   = Color(0xFF3FB950)   // green
private val AccAmb   = Color(0xFFD29922)   // amber
private val AccRed   = Color(0xFFF85149)   // red
private val AccPurp  = Color(0xFFBC8CFF)   // purple (streak)

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

    // Force dark theme — overrides the app-wide lightColorScheme so caregiver UI is always dark
    MaterialTheme(
        colorScheme = darkColorScheme(
            background  = PageBg,
            surface     = CardBg,
            surfaceVariant = CardBg2,
            primary     = AccBlue,
            secondary   = AccGrn,
            error       = AccRed,
            onBackground = TextPri,
            onSurface    = TextPri,
            onPrimary    = Color.White
        )
    ) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MediCare", fontWeight = FontWeight.Bold,
                            fontSize = 17.sp, color = TextPri)
                        Text(
                            "Caregiver Dashboard  ·  ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}",
                            fontSize = 11.sp, color = TextSec
                        )
                    }
                },
                actions = {
                    state.seniorSnapshot?.let { snap ->
                        val lc = levelColor(snap.statusLevel)
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = lc.copy(alpha = 0.15f)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(lc))
                                Spacer(Modifier.width(5.dp))
                                Text(snap.statusLevel.label, fontSize = 11.sp, color = lc,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = viewModel::showLinkDialog) {
                        Icon(Icons.Default.Add, "Link", tint = TextSec)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = TextSec)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavBg)
            )
        },
        containerColor = PageBg
    ) { padding ->

        if (state.isLoading && state.seniorSnapshot == null) {
            Box(Modifier.fillMaxSize().background(PageBg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(PageBg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            state.linkSuccess?.let {
                item {
                    Surface(color = AccGrn.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccGrn.copy(alpha = 0.3f))) {
                        Text("✅ $it", Modifier.padding(12.dp), color = AccGrn,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item { NoSeniorCard(viewModel::showLinkDialog) }
                return@LazyColumn
            }

            val snap = state.seniorSnapshot!!

            // ── 1. Patient overview ────────────────────────────────────────
            item { PatientOverviewCard(snap, state) }

            // ── 2. Inactivity alert ────────────────────────────────────────
            if (snap.inactiveMinutes > 30 && state.emergencyContactPhone.isNotBlank()) {
                item {
                    Surface(color = AccAmb.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccAmb.copy(alpha = 0.3f))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("No activity for ${snap.inactiveMinutes} min",
                                    fontSize = 13.sp, color = AccAmb, fontWeight = FontWeight.SemiBold)
                                Text("Consider calling the patient", fontSize = 11.sp, color = TextSec)
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_CALL,
                                                Uri.parse("tel:${state.emergencyContactPhone}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                border = BorderStroke(1.dp, AccRed),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) { Text("Call Now", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ── 3. Metrics row ─────────────────────────────────────────────
            item {
                val taken   = snap.todayDoses.count { it.isTaken() }
                val missed  = snap.todayDoses.count { it.isMissed() || it.isOverdue() }
                val pending = snap.todayDoses.size - taken - missed
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkMetricCard(Modifier.weight(1f), "$taken",   "TAKEN",   AccGrn,  "✅")
                    DarkMetricCard(Modifier.weight(1f), "$pending", "PENDING", AccAmb,  "⏳")
                    DarkMetricCard(Modifier.weight(1f), "$missed",  "MISSED",  AccRed,  "❌")
                    DarkMetricCard(
                        Modifier.weight(1f),
                        if (state.streak > 0) "${state.streak}d" else "${snap.todayDoses.size}",
                        if (state.streak > 0) "STREAK" else "TOTAL",
                        if (state.streak > 0) AccPurp else AccBlue,
                        if (state.streak > 0) "🔥" else "💊"
                    )
                }
            }

            // ── 4. ALARM VOICE (inline, prominent) ─────────────────────────
            item { DashSection("ALARM VOICE") }
            item {
                AlarmVoiceCard(
                    saved     = alarmSaved,
                    recording = recordingAlarm,
                    onStart   = {
                        recordingAlarm = true
                        VoiceAlarmManager.startRecording(context)
                    },
                    onStop    = {
                        recordingAlarm = false
                        val f   = VoiceAlarmManager.stopRecording()
                        val sid = snap.user.id
                        if (f != null) scope.launch {
                            if (VoiceAlarmManager.uploadAlarmVoice(sid, f)) {
                                VoiceAlarmManager.markAlarmVoiceSaved(context, sid)
                                alarmSaved = true
                            }
                        }
                    }
                )
            }

            // ── 5. MEDICINES (inline, prominent) ───────────────────────────
            item { DashSection("PATIENT MEDICINES") }
            item {
                MedicinesCard(
                    medications   = state.medications,
                    onAddMedicine = viewModel::showAddMedDialog
                )
            }

            // ── 6. Refill alerts ───────────────────────────────────────────
            val lowPillMeds = state.medications.filter { it.remainingPills in 1..5 }
            if (lowPillMeds.isNotEmpty()) {
                item {
                    Surface(color = AccAmb.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccAmb.copy(alpha = 0.25f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Text("💊", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("REFILL NEEDED", fontSize = 10.sp, color = AccAmb,
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(2.dp))
                                lowPillMeds.forEach { med ->
                                    Text(
                                        "${med.name}  —  ${med.remainingPills} pill${if (med.remainingPills == 1) "" else "s"} remaining",
                                        fontSize = 12.sp, color = TextPri
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 7. Today's schedule ────────────────────────────────────────
            item { DashSection("TODAY'S SCHEDULE") }
            item { DarkScheduleTimeline(snap.todayDoses, state.medications) }

            // ── 8. Chart + Alerts side by side ─────────────────────────────
            val recentAlerts = state.alerts.filter { !it.isResolved }.take(5)
            if (state.weeklyChart.isNotEmpty() || recentAlerts.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.weeklyChart.isNotEmpty()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DashSection("7-DAY TREND")
                                DarkWeeklyChart(state.weeklyChart)
                            }
                        }
                        if (recentAlerts.isNotEmpty()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DashSection("ALERTS")
                                DarkAlertsPanel(recentAlerts)
                            }
                        }
                    }
                }
            }

            // ── 9. Other actions ───────────────────────────────────────────
            item { DashSection("OTHER ACTIONS") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionCard(Modifier.weight(1f), "💌", "Voice\nNote",
                        viewModel::showFamilyNoteRecorder)
                    SmallActionCard(Modifier.weight(1f), "🚨", "SOS\nContact",
                        viewModel::showEmergencyDialog)
                    SmallActionCard(Modifier.weight(1f), "📊", "Refresh\nVitals",
                        viewModel::simulateVitals)
                    SmallActionCard(Modifier.weight(1f), "⏰", "Set\nAlarms",
                        { viewModel.scheduleMedicationAlarms(context) })
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    } // end MaterialTheme dark wrapper
}

// ─── Patient Overview Card ────────────────────────────────────────────────────

@Composable
private fun PatientOverviewCard(snap: SeniorSnapshot, state: CaregiverUiState) {
    val taken    = snap.todayDoses.count { it.isTaken() }
    val total    = snap.todayDoses.size
    val pct      = if (total == 0) 0f else taken.toFloat() / total
    val lvlColor = levelColor(snap.statusLevel)

    Surface(color = CardBg, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape)
                            .background(lvlColor.copy(alpha = 0.12f))
                            .border(2.dp, lvlColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("👴", fontSize = 28.sp) }
                    Box(Modifier.size(14.dp).clip(CircleShape).background(CardBg),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(lvlColor))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(snap.user.name, fontWeight = FontWeight.Bold,
                        fontSize = 17.sp, color = TextPri)
                    val lastSeen = when {
                        snap.inactiveMinutes < 1  -> "Active now"
                        snap.inactiveMinutes < 60 -> "${snap.inactiveMinutes}m ago"
                        else -> "${snap.inactiveMinutes / 60}h ago"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape)
                            .background(if (snap.inactiveMinutes < 5) AccGrn else AccAmb))
                        Spacer(Modifier.width(4.dp))
                        Text(lastSeen, fontSize = 12.sp,
                            color = if (snap.inactiveMinutes > 30) AccRed else TextSec)
                    }
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { pct }, modifier = Modifier.size(52.dp),
                        color = lvlColor, trackColor = BorderC, strokeWidth = 5.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$taken/$total", fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = TextPri)
                        Text("doses", fontSize = 8.sp, color = TextSec)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = BorderC, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                VitalCell(Modifier.weight(1f), "BP", state.vitalBP)
                Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(BorderC))
                VitalCell(Modifier.weight(1f), "Heart Rate", "${state.vitalHR} bpm")
                Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(BorderC))
                VitalCell(Modifier.weight(1f), "SpO2", "${state.vitalSpO2}%")
                Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(BorderC))
                VitalCell(Modifier.weight(1f), "Status", snap.statusLevel.emoji)
            }
        }
    }
}

@Composable
private fun VitalCell(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = TextSec, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 14.sp, color = TextPri, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
    }
}

// ─── Dark Metric Card ─────────────────────────────────────────────────────────

@Composable
private fun DarkMetricCard(modifier: Modifier, value: String, label: String,
                             color: Color, icon: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon.isNotEmpty()) Text(icon, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
            Text(label, fontSize = 9.sp, color = TextSec, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp, textAlign = TextAlign.Center)
        }
    }
}

// ─── Inline Alarm Voice Card ──────────────────────────────────────────────────

@Composable
private fun AlarmVoiceCard(saved: Boolean, recording: Boolean,
                            onStart: () -> Unit, onStop: () -> Unit) {
    val borderColor = when {
        recording -> AccRed.copy(alpha = 0.7f)
        saved     -> AccGrn.copy(alpha = 0.5f)
        else      -> BorderC
    }
    Surface(color = CardBg2, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(AccBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alarm Voice Recording", fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = TextPri)
                    Text("Your voice plays on patient's phone when medicine alarm fires",
                        fontSize = 11.sp, color = TextSec)
                }
                if (saved && !recording) {
                    Surface(shape = RoundedCornerShape(8.dp), color = AccGrn.copy(alpha = 0.12f)) {
                        Text("✅ SAVED", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = AccGrn, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (recording) {
                Spacer(Modifier.height(12.dp))
                Surface(color = AccRed.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AccRed.copy(alpha = 0.3f))) {
                    Row(Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AccRed))
                        Spacer(Modifier.width(8.dp))
                        Text("Recording in progress... speak clearly",
                            color = AccRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = if (recording) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        recording -> AccRed; saved -> AccGrn; else -> AccBlue
                    }
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(if (recording) Icons.Default.Close else Icons.Default.Mic, null,
                    tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        recording -> "Stop & Save Recording"
                        saved     -> "Re-Record Voice"
                        else      -> "Tap to Record Your Voice"
                    },
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
            }
            if (saved && !recording) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Voice saved — plays automatically when medicine alarm fires",
                        fontSize = 11.sp, color = AccGrn, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ─── Medicines Management Card ────────────────────────────────────────────────

@Composable
private fun MedicinesCard(medications: List<Medication>, onAddMedicine: () -> Unit) {
    Surface(color = CardBg2, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(AccBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Text("💊", fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Patient Medicines", fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = TextPri)
                    Text("Add medicines here — syncs instantly to patient's screen",
                        fontSize = 11.sp, color = TextSec)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = AccBlue.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onAddMedicine() }) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = AccBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add", color = AccBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = BorderC, thickness = 0.5.dp)
            if (medications.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No medicines added yet.\nTap '+ Add' to set medicines for the patient.",
                    fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            } else {
                medications.forEach { med ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = MedPalette.colorForMedication(med.pillColorHex, med.colorIndex)
                        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(med.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = TextPri)
                            Text(
                                med.scheduleTimes.joinToString("  ·  ") +
                                    if (med.purpose.isNotBlank()) "  ·  ${med.purpose}" else "",
                                fontSize = 11.sp, color = TextSec,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(med.dosage, fontSize = 12.sp, color = AccBlue,
                            fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = BorderC.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAddMedicine,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Medicine for Patient", color = Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Schedule Timeline (dark) ─────────────────────────────────────────────────

@Composable
private fun DarkScheduleTimeline(doses: List<DoseRecord>, medications: List<Medication>) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    if (doses.isEmpty()) {
        Surface(color = CardBg, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No medicines scheduled today", fontSize = 13.sp, color = TextSec)
            }
        }
        return
    }
    data class Slot(val label: String, val icon: String, val range: IntRange)
    val slots = listOf(
        Slot("Morning",   "🌅", 6..11),
        Slot("Afternoon", "☀️", 12..16),
        Slot("Evening",   "🌆", 17..20),
        Slot("Night",     "🌙", 21..29)
    )
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            var firstSlot = true
            for (slot in slots) {
                val slotDoses = doses.filter { dose ->
                    val cal = Calendar.getInstance(); cal.timeInMillis = dose.scheduledTime
                    val h = cal.get(Calendar.HOUR_OF_DAY)
                    (if (h < 6) h + 24 else h) in slot.range
                }
                if (slotDoses.isEmpty()) continue
                if (!firstSlot) Spacer(Modifier.height(16.dp))
                firstSlot = false
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.icon, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(slot.label.uppercase(), fontSize = 10.sp, color = TextSec,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    HorizontalDivider(Modifier.weight(1f), color = BorderC, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(10.dp))
                slotDoses.forEachIndexed { idx, dose ->
                    val isLast = idx == slotDoses.size - 1
                    val med = medications.find { it.id == dose.medicationId }
                    val dotColor = MedPalette.colorForMedication(
                        med?.pillColorHex ?: "#4CAF50", med?.colorIndex ?: -1)
                    val (statusLabel, statusColor, statusBg) = when {
                        dose.isTaken()                      -> Triple("TAKEN",   AccGrn, AccGrn.copy(0.12f))
                        dose.isMissed() || dose.isOverdue() -> Triple("MISSED",  AccRed, AccRed.copy(0.12f))
                        else                                 -> Triple("PENDING", AccAmb, AccAmb.copy(0.12f))
                    }
                    val takenTime = if (dose.isTaken() && dose.takenAt > 0)
                        "  ·  ${timeFmt.format(Date(dose.takenAt))}" else ""
                    Row(Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(18.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                            if (!isLast) Box(Modifier.width(1.5.dp).height(38.dp).background(BorderC))
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dose.medicationName, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = TextPri,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${timeFmt.format(Date(dose.scheduledTime))}$takenTime",
                                    fontSize = 11.sp, color = TextSec)
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = statusBg,
                                border = BorderStroke(0.5.dp, statusColor.copy(0.3f))) {
                                Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp, color = statusColor,
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Weekly Chart (dark) ──────────────────────────────────────────────────────

@Composable
private fun DarkWeeklyChart(data: List<DayAdherence>) {
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom) {
                data.forEach { day ->
                    val pct = if (day.total == 0) 0f
                              else day.taken.toFloat() / day.total.toFloat()
                    val barColor = when {
                        day.total == 0 -> BorderC
                        pct >= 1f      -> AccGrn
                        pct >= 0.5f    -> AccAmb
                        else           -> AccRed
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom) {
                        val barH = ((pct * 50).toInt().coerceAtLeast(if (day.total > 0) 3 else 2)).dp
                        Box(Modifier.fillMaxWidth().height(barH)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(barColor))
                        Spacer(Modifier.height(3.dp))
                        Text(day.dayLabel, fontSize = 8.sp, color = TextSec)
                        Text(if (day.total > 0) "${(pct * 100).toInt()}%" else "–",
                            fontSize = 8.sp, color = if (pct >= 1f) AccGrn else TextSec,
                            fontWeight = if (pct >= 1f) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// ─── Alerts Panel (dark) ─────────────────────────────────────────────────────

@Composable
private fun DarkAlertsPanel(alerts: List<Alert>) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(color = CardBg, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            alerts.forEachIndexed { idx, alert ->
                val type  = alert.alertType()
                val color = when (type) {
                    AlertType.MISSED_DOSE, AlertType.MOOD_LOW -> AccRed
                    AlertType.INACTIVITY                      -> AccAmb
                    AlertType.DOSE_CONFIRMED                  -> AccGrn
                    else                                       -> TextSec
                }
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(0.1f)),
                        contentAlignment = Alignment.Center) {
                        Text(type.emoji, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(type.label, fontSize = 11.sp, color = color,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            alert.medicationName.ifBlank { timeFmt.format(Date(alert.timestamp)) },
                            fontSize = 10.sp, color = TextSec, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (idx < alerts.size - 1) HorizontalDivider(color = BorderC, thickness = 0.5.dp)
            }
        }
    }
}

// ─── Small Action Card ────────────────────────────────────────────────────────

@Composable
private fun SmallActionCard(modifier: Modifier, emoji: String, label: String, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable { onClick() }, color = CardBg,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderC)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 22.sp)
            Text(label, fontSize = 9.sp, color = TextSec, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun DashSection(label: String) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSec,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun NoSeniorCard(onLink: () -> Unit) {
    Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderC), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👴", fontSize = 48.sp)
            Spacer(Modifier.height(10.dp))
            Text("No Patient Linked", fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = TextPri)
            Spacer(Modifier.height(4.dp))
            Text("Link a family member's account to monitor their medication.",
                fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onLink, modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccBlue),
                shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Link Family Member", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun VoiceNoteDialog(recording: Boolean, saved: Boolean,
                             onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg2,
        title = { Text("💌 Voice Note for Patient", fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, color = TextPri) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Plays for the patient when they need encouragement.",
                    fontSize = 12.sp, color = TextSec)
                Text("E.g. \"Thatha, how are you today?\"",
                    fontSize = 12.sp, color = AccBlue, fontStyle = FontStyle.Italic)
                if (saved) Text("✅ Note saved!", color = AccGrn,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Button(
                    onClick = if (recording) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) AccRed else AccBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (recording) Icons.Default.Close else Icons.Default.Mic, null,
                        tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(if (recording) "Stop & Save" else "Start Recording",
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (recording) Text("● Recording...", color = AccRed, fontSize = 12.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) {
            Text("Close", color = TextSec, fontSize = 13.sp) } }
    )
}

@Composable
private fun EmergencyDialog(name: String, phone: String,
                             onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var p by remember { mutableStateOf(phone) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg2,
        title = { Text("Emergency Contact", fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, color = TextPri) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Called automatically if patient is inactive for 30+ min.",
                    fontSize = 12.sp, color = TextSec)
                OutlinedTextField(n, { n = it }, label = { Text("Name", color = TextSec) },
                    textStyle = LocalTextStyle.current.copy(color = TextPri),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Phone", color = TextSec) },
                    textStyle = LocalTextStyle.current.copy(color = TextPri),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(n, p) }, enabled = p.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccBlue)) {
                Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSec) } }
    )
}

@Composable
private fun LinkDialog(email: String, onChange: (String) -> Unit,
                       onLink: () -> Unit, onDismiss: () -> Unit, loading: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg2,
        title = { Text("Link Patient", fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, color = TextPri) },
        text = {
            Column {
                Text("Enter the patient's account email.", fontSize = 12.sp, color = TextSec)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email, onChange, label = { Text("Email", color = TextSec) },
                    textStyle = LocalTextStyle.current.copy(color = TextPri),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onLink, enabled = email.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccBlue)) {
                if (loading) CircularProgressIndicator(color = Color.White,
                    modifier = Modifier.size(16.dp))
                else Text("Link", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSec) } }
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
    val slots   = remember {
        mutableStateMapOf("07:00" to false, "13:00" to false, "18:00" to false, "21:00" to false)
    }
    val slotLabels = linkedMapOf(
        "07:00" to "🌅 Morning    7:00 AM",
        "13:00" to "☀️ Afternoon  1:00 PM",
        "18:00" to "🌆 Evening    6:00 PM",
        "21:00" to "🌙 Night      9:00 PM"
    )
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg2,
        title = { Text("Add Medicine for Patient", fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, color = TextPri) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it },
                    label = { Text("Medicine Name", color = TextSec) },
                    textStyle = LocalTextStyle.current.copy(color = TextPri),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(purpose, { purpose = it },
                    label = { Text("Purpose / Condition", color = TextSec) },
                    textStyle = LocalTextStyle.current.copy(color = TextPri),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dosage, { dosage = it },
                    label = { Text("Dosage  (e.g. 1 tab, 5ml)", color = TextSec) },
                    textStyle = LocalTextStyle.current.copy(color = TextPri),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("When to give:", fontSize = 13.sp, color = TextSec,
                    fontWeight = FontWeight.Medium)
                slotLabels.forEach { (time, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = slots[time] == true,
                            onCheckedChange = { slots[time] = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccBlue, uncheckedColor = TextSec))
                        Text(label, fontSize = 13.sp, color = TextPri)
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
                colors = ButtonDefaults.buttonColors(containerColor = AccBlue)
            ) { Text("Add Medicine", color = Color.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSec) } }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun levelColor(level: SeniorStatusLevel): Color = when (level) {
    SeniorStatusLevel.GREEN -> AccGrn
    SeniorStatusLevel.AMBER -> AccAmb
    SeniorStatusLevel.RED   -> AccRed
}

private fun showMissedDoseNotification(context: Context, medName: String) {
    val nm = context.getSystemService(NotificationManager::class.java)
    val notification = NotificationCompat.Builder(context, MedApp.CHANNEL_ALERT)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("⚠️ Missed Dose Alert")
        .setContentText("$medName was not taken on time!")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    nm.notify(medName.hashCode() + 9000, notification)
}
