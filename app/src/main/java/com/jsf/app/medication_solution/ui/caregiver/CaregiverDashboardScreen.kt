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

// ── Professional dashboard palette (nothing like the patient green/colorful UI) ─
private val NavBar   = Color(0xFF0F172A)   // very dark slate — top bar
private val Indigo   = Color(0xFF4F46E5)   // indigo — FAB, accents
private val BgPage   = Color(0xFFF1F5F9)   // cool light slate
private val BgCard   = Color.White
private val Stroke   = Color(0xFFE2E8F0)   // card borders / dividers
private val TextPri  = Color(0xFF0F172A)   // primary text
private val TextSec  = Color(0xFF64748B)   // secondary text
private val GoodGrn  = Color(0xFF059669)   // emerald — taken / good
private val WarnAmb  = Color(0xFFD97706)   // amber — pending / warning
private val DangerRd = Color(0xFFDC2626)   // red — missed / urgent

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
                        Text("MediCare",
                            fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text(
                            "Caregiver Dashboard  ·  ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}",
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                },
                actions = {
                    // Live patient status badge in top bar
                    state.seniorSnapshot?.let { snap ->
                        val lvl = snap.statusLevel
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = levelToColor(lvl).copy(alpha = 0.18f)) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(levelToColor(lvl)))
                                Spacer(Modifier.width(5.dp))
                                Text(lvl.label, fontSize = 11.sp,
                                    color = levelToColor(lvl), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = viewModel::showLinkDialog) {
                        Icon(Icons.Default.Add, "Link", tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavBar)
            )
        },
        floatingActionButton = {
            if (state.seniorSnapshot != null) {
                FloatingActionButton(onClick = viewModel::showAddMedDialog, containerColor = Indigo) {
                    Icon(Icons.Default.Add, "Add Medicine", tint = Color.White)
                }
            }
        },
        containerColor = BgPage
    ) { padding ->

        if (state.isLoading && state.seniorSnapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Indigo)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            state.linkSuccess?.let {
                item {
                    Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(8.dp)) {
                        Text("✅ $it", Modifier.padding(12.dp),
                            color = GoodGrn, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item { NoSeniorCard(viewModel::showLinkDialog) }
                return@LazyColumn
            }

            val snap = state.seniorSnapshot!!

            // ── 1. Patient overview card ──────────────────────────────────
            item {
                PatientOverviewCard(
                    snap   = snap,
                    state  = state,
                    onSettingsTap = { onViewSeniorDetail(snap.user.id) }
                )
            }

            // ── 2. Inactivity banner ──────────────────────────────────────
            if (snap.inactiveMinutes > 30 && state.emergencyContactPhone.isNotBlank()) {
                item {
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WarnAmb.copy(alpha = 0.4f))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("No activity for ${snap.inactiveMinutes} min",
                                    fontSize = 13.sp, color = Color(0xFF92400E),
                                    fontWeight = FontWeight.SemiBold)
                                Text("Consider calling the patient",
                                    fontSize = 11.sp, color = TextSec)
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_CALL, Uri.parse("tel:${state.emergencyContactPhone}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                border = BorderStroke(1.dp, DangerRd),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRd),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Call Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── 3. Metrics row ────────────────────────────────────────────
            item {
                val taken   = snap.todayDoses.count { it.isTaken() }
                val missed  = snap.todayDoses.count { it.isMissed() || it.isOverdue() }
                val pending = snap.todayDoses.size - taken - missed
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(Modifier.weight(1f), "$taken",    "TAKEN",   GoodGrn,  "✅")
                    MetricCard(Modifier.weight(1f), "$pending",  "PENDING", WarnAmb,  "⏳")
                    MetricCard(Modifier.weight(1f), "$missed",   "MISSED",  DangerRd, "❌")
                    MetricCard(
                        Modifier.weight(1f),
                        if (state.streak > 0) "${state.streak}d" else "${snap.todayDoses.size}",
                        if (state.streak > 0) "STREAK" else "TOTAL",
                        if (state.streak > 0) WarnAmb else Indigo,
                        if (state.streak > 0) "🔥" else "💊"
                    )
                }
            }

            // ── 4. Refill alerts ──────────────────────────────────────────
            val lowPillMeds = state.medications.filter { it.remainingPills in 1..5 }
            if (lowPillMeds.isNotEmpty()) {
                item {
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WarnAmb.copy(alpha = 0.3f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Text("💊", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("REFILL NEEDED", fontSize = 10.sp, color = WarnAmb,
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

            // ── 5. Today's schedule (timeline) ────────────────────────────
            item { DashSection("TODAY'S SCHEDULE") }
            item { ScheduleTimeline(snap.todayDoses, state.medications) }

            // ── 6. Trend chart + Recent alerts side-by-side ───────────────
            val recentAlerts = state.alerts.filter { !it.isResolved }.take(5)
            if (state.weeklyChart.isNotEmpty() || recentAlerts.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.weeklyChart.isNotEmpty()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DashSection("7-DAY TREND")
                                WeeklyAdherenceChart(state.weeklyChart)
                            }
                        }
                        if (recentAlerts.isNotEmpty()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DashSection("RECENT ALERTS")
                                AlertsPanel(recentAlerts)
                            }
                        }
                    }
                }
            }

            // ── 7. Quick actions grid ─────────────────────────────────────
            item { DashSection("QUICK ACTIONS") }
            item {
                QuickActionsGrid(
                    onVoiceNote      = viewModel::showFamilyNoteRecorder,
                    onAlarmVoice     = viewModel::showVoiceAlarmRecorder,
                    onSosContact     = viewModel::showEmergencyDialog,
                    onAddMedicine    = viewModel::showAddMedDialog,
                    onScheduleAlarms = { viewModel.scheduleMedicationAlarms(context) },
                    onSimulateVitals = viewModel::simulateVitals
                )
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

// ─── Patient Overview Card ────────────────────────────────────────────────────

@Composable
private fun PatientOverviewCard(
    snap: SeniorSnapshot,
    state: CaregiverUiState,
    onSettingsTap: () -> Unit
) {
    val taken   = snap.todayDoses.count { it.isTaken() }
    val total   = snap.todayDoses.size
    val pct     = if (total == 0) 0f else taken.toFloat() / total
    val lvlColor = levelToColor(snap.statusLevel)

    Surface(
        color = BgCard, shape = RoundedCornerShape(16.dp),
        shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            // Top row: avatar + info + progress ring + settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar with live status ring
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape)
                            .background(lvlColor.copy(alpha = 0.1f))
                            .border(2.dp, lvlColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("👴", fontSize = 28.sp) }
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
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
                        else                       -> "${snap.inactiveMinutes / 60}h ${snap.inactiveMinutes % 60}m ago"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (snap.inactiveMinutes < 5) GoodGrn else WarnAmb)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(lastSeen, fontSize = 12.sp,
                            color = if (snap.inactiveMinutes > 30) DangerRd else TextSec)
                    }
                }
                // Donut progress
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.size(52.dp),
                        color = lvlColor,
                        trackColor = Stroke,
                        strokeWidth = 5.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$taken/$total",
                            fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPri)
                        Text("doses", fontSize = 8.sp, color = TextSec)
                    }
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onSettingsTap, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Settings, null, tint = TextSec, modifier = Modifier.size(17.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Stroke, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            // Vitals row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                VitalCell(Modifier.weight(1f), "Blood Pressure", state.vitalBP)
                Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(Stroke))
                VitalCell(Modifier.weight(1f), "Heart Rate", "${state.vitalHR} bpm")
                Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(Stroke))
                VitalCell(Modifier.weight(1f), "SpO2", "${state.vitalSpO2}%")
                Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(Stroke))
                VitalCell(Modifier.weight(1f), "Overall", snap.statusLevel.emoji)
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

// ─── Metric cards ─────────────────────────────────────────────────────────────

@Composable
private fun MetricCard(modifier: Modifier, value: String, label: String, color: Color, icon: String) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.07f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon.isNotEmpty()) Text(icon, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
            Text(label, fontSize = 9.sp, color = TextSec,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center)
        }
    }
}

// ─── Schedule Timeline ────────────────────────────────────────────────────────

@Composable
private fun ScheduleTimeline(doses: List<DoseRecord>, medications: List<Medication>) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    if (doses.isEmpty()) {
        Surface(
            color = BgCard, shape = RoundedCornerShape(12.dp),
            shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()
        ) {
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

    Surface(
        color = BgCard, shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()
    ) {
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

                // Slot sub-header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slot.icon, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(slot.label.uppercase(), fontSize = 10.sp, color = TextSec,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    HorizontalDivider(Modifier.weight(1f), color = Stroke, thickness = 0.5.dp)
                }
                Spacer(Modifier.height(10.dp))

                slotDoses.forEachIndexed { idx, dose ->
                    val isLast = idx == slotDoses.size - 1
                    val med = medications.find { it.id == dose.medicationId }
                    val dotColor = MedPalette.colorForMedication(
                        med?.pillColorHex ?: "#4CAF50", med?.colorIndex ?: -1)
                    val (statusLabel, statusColor, statusBg) = when {
                        dose.isTaken()                         -> Triple("TAKEN",   GoodGrn,  Color(0xFFECFDF5))
                        dose.isMissed() || dose.isOverdue()    -> Triple("MISSED",  DangerRd, Color(0xFFFEF2F2))
                        else                                    -> Triple("PENDING", WarnAmb,  Color(0xFFFFFBEB))
                    }
                    val takenTime = if (dose.isTaken() && dose.takenAt > 0)
                        "  ·  taken at ${timeFmt.format(Date(dose.takenAt))}" else ""

                    Row(Modifier.fillMaxWidth()) {
                        // Timeline rail: dot + vertical line
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(18.dp)
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                            if (!isLast) Box(
                                Modifier.width(1.5.dp).height(38.dp).background(Stroke)
                            )
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
                                Text(
                                    "${timeFmt.format(Date(dose.scheduledTime))}$takenTime",
                                    fontSize = 11.sp, color = TextSec
                                )
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                                Text(
                                    statusLabel,
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp, color = statusColor,
                                    fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── 7-Day Chart ──────────────────────────────────────────────────────────────

@Composable
private fun WeeklyAdherenceChart(data: List<DayAdherence>) {
    Surface(color = BgCard, shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { day ->
                    val pct = if (day.total == 0) 0f
                              else day.taken.toFloat() / day.total.toFloat()
                    val barColor = when {
                        day.total == 0 -> Stroke
                        pct >= 1f      -> GoodGrn
                        pct >= 0.5f    -> WarnAmb
                        else           -> DangerRd
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val barH = ((pct * 50).toInt().coerceAtLeast(if (day.total > 0) 3 else 2)).dp
                        Box(
                            Modifier.fillMaxWidth().height(barH)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(day.dayLabel, fontSize = 8.sp, color = TextSec)
                        Text(if (day.total > 0) "${(pct * 100).toInt()}%" else "–",
                            fontSize = 8.sp, color = if (pct >= 1f) GoodGrn else TextSec,
                            fontWeight = if (pct >= 1f) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// ─── Alerts Panel ─────────────────────────────────────────────────────────────

@Composable
private fun AlertsPanel(alerts: List<Alert>) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Surface(color = BgCard, shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            alerts.forEachIndexed { idx, alert ->
                val type = alert.alertType()
                val color = when (type) {
                    AlertType.MISSED_DOSE, AlertType.MOOD_LOW -> DangerRd
                    AlertType.INACTIVITY                      -> WarnAmb
                    AlertType.DOSE_CONFIRMED                  -> GoodGrn
                    else                                       -> TextSec
                }
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(color.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) { Text(type.emoji, fontSize = 13.sp) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(type.label, fontSize = 11.sp, color = color,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            alert.medicationName.ifBlank {
                                timeFmt.format(Date(alert.timestamp))
                            },
                            fontSize = 10.sp, color = TextSec, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (idx < alerts.size - 1)
                    HorizontalDivider(color = Stroke, thickness = 0.5.dp)
            }
        }
    }
}

// ─── Quick Actions Grid ───────────────────────────────────────────────────────

@Composable
private fun QuickActionsGrid(
    onVoiceNote: () -> Unit, onAlarmVoice: () -> Unit, onSosContact: () -> Unit,
    onAddMedicine: () -> Unit, onScheduleAlarms: () -> Unit, onSimulateVitals: () -> Unit
) {
    val actions = listOf(
        Triple("💌", "Voice Note",    onVoiceNote),
        Triple("🎙️", "Alarm Voice",   onAlarmVoice),
        Triple("🚨", "SOS Contact",   onSosContact),
        Triple("➕", "Add Medicine",  onAddMedicine),
        Triple("⏰", "Set Alarms",    onScheduleAlarms),
        Triple("📊", "Vitals",        onSimulateVitals)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (emoji, label, onClick) ->
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onClick() },
                        color = BgCard, shape = RoundedCornerShape(12.dp),
                        shadowElevation = 1.dp, border = BorderStroke(0.5.dp, Stroke)
                    ) {
                        Column(
                            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(emoji, fontSize = 24.sp)
                            Text(label, fontSize = 10.sp, color = TextSec,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─── Section label ────────────────────────────────────────────────────────────

@Composable
private fun DashSection(label: String) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSec,
        letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 2.dp))
}

// ─── No Senior Card ───────────────────────────────────────────────────────────

@Composable
private fun NoSeniorCard(onLink: () -> Unit) {
    Surface(color = BgCard, shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
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
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Link Family Member", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Dialogs (unchanged logic) ────────────────────────────────────────────────

@Composable
private fun VoiceDialog(
    title: String, hint: String, recording: Boolean, saved: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Plays for the patient when needed.", fontSize = 12.sp, color = TextSec)
                Text(hint, fontSize = 12.sp, color = Indigo, fontStyle = FontStyle.Italic)
                if (saved) Text("✅ Saved!", color = GoodGrn,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Button(
                    onClick = if (recording) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) DangerRd else Indigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (recording) Icons.Default.Close else Icons.Default.Mic,
                        null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(if (recording) "Stop & Save" else "Start Recording",
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (recording) Text("● Recording...", color = DangerRd, fontSize = 12.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontSize = 13.sp) } }
    )
}

@Composable
private fun EmergencyDialog(
    name: String, phone: String,
    onSave: (String, String) -> Unit, onDismiss: () -> Unit
) {
    var n by remember { mutableStateOf(name) }
    var p by remember { mutableStateOf(phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency Contact", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Called automatically if patient is inactive for 30+ min.",
                    fontSize = 12.sp, color = TextSec)
                OutlinedTextField(n, { n = it }, label = { Text("Name", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Phone", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(n, p) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                enabled = p.isNotBlank()) {
                Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LinkDialog(
    email: String, onChange: (String) -> Unit,
    onLink: () -> Unit, onDismiss: () -> Unit, loading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Patient", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column {
                Text("Enter the patient's account email.", fontSize = 12.sp, color = TextSec)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email, onChange, label = { Text("Email", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = onLink, enabled = email.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)) {
                if (loading) CircularProgressIndicator(color = Color.White,
                    modifier = Modifier.size(16.dp))
                else Text("Link", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        "07:00" to "🌅 Morning",
        "13:00" to "☀️ Afternoon",
        "18:00" to "🌆 Evening",
        "21:00" to "🌙 Night"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medicine", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it },
                    label = { Text("Medicine Name", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(purpose, { purpose = it },
                    label = { Text("Purpose / Condition", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dosage, { dosage = it },
                    label = { Text("Dosage (e.g. 1 tab)", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Schedule:", fontSize = 13.sp, color = TextSec,
                    fontWeight = FontWeight.Medium)
                slotLabels.forEach { (time, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = slots[time] == true,
                            onCheckedChange = { slots[time] = it })
                        Text(label, fontSize = 13.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = Indigo)
            ) { Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun levelToColor(level: SeniorStatusLevel): Color = when (level) {
    SeniorStatusLevel.GREEN -> GoodGrn
    SeniorStatusLevel.AMBER -> WarnAmb
    SeniorStatusLevel.RED   -> DangerRd
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
