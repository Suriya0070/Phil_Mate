package com.jsf.app.medication_solution.ui.senior

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val greeting = getGreeting()
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onUserInteraction()
        viewModel.checkFamilyVoiceNote()
    }

    if (state.showEngagement) {
        EngagementDialog(
            question = state.engagementQuestion,
            onDismiss = viewModel::dismissEngagement
        )
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
                            "$greeting 😊",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.user?.name ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    if (state.isMonitored) {
                        CameraIndicatorDot()
                    }
                    IconButton(onClick = onVoiceAssistant) {
                        Icon(Icons.Default.Mic, "Voice Assistant", tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedGreen,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (state.medications.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.seedDemoMedications() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Load Demo Medicines") },
                    containerColor = MedGreen,
                    contentColor = Color.White
                )
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
            // Date and summary card
            item {
                SummaryCard(taken = taken, total = total)
            }

            // Family voice note card
            if (state.hasVoiceNote) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💌", fontSize = 28.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voice message from your family!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("Tap to listen", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🧠 Daily Brain Exercise", style = MaterialTheme.typography.labelLarge, color = CareBlue, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(challenge, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Keeping your mind sharp!", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }

            // Success message
            item {
                AnimatedVisibility(
                    visible = state.successMessage != null,
                    enter = fadeIn() + slideInVertically()
                ) {
                    state.successMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MedGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Section title
            item {
                Text(
                    "Today's Medicines",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A2E1A)
                )
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
                            Text("💊", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No medicines yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                            )
                            Text(
                                "Tap the button below to load demo medicines",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(state.medications) { medication ->
                    val doseRecord = viewModel.getDoseForMedication(medication.id)
                    MedicationCard(
                        medication = medication,
                        doseRecord = doseRecord,
                        onClick = {
                            if (doseRecord != null) {
                                viewModel.onUserInteraction()
                                onMedicationClick(doseRecord, medication)
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(taken: Int, total: Int) {
    val dateStr = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
    }
    val progress = if (total == 0) 0f else taken.toFloat() / total.toFloat()
    val allDone = taken == total && total > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) MedGreen else Color.White
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                dateStr,
                style = MaterialTheme.typography.bodyMedium,
                color = if (allDone) Color.White.copy(alpha = 0.8f) else Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            if (total == 0) {
                Text(
                    "No medicines scheduled yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
            } else if (allDone) {
                Text(
                    "🎉 All medicines taken!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Great job today!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            } else {
                Text(
                    "$taken of $total medicines taken today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A2E1A)
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = MedGreen,
                    trackColor = Color(0xFFE0E0E0)
                )
            }
        }
    }
}

@Composable
fun MedicationCard(
    medication: Medication,
    doseRecord: DoseRecord?,
    onClick: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val status = doseRecord?.doseStatus() ?: DoseStatus.PENDING
    val isOverdue = doseRecord?.isOverdue() == true

    val (statusColor, statusText, statusBg) = when {
        status == DoseStatus.TAKEN -> Triple(StatusGreen, "✓ Taken at ${timeFmt.format(Date(doseRecord!!.takenAt))}", Color(0xFFE8F5E9))
        status == DoseStatus.MISSED -> Triple(StatusRed, "✗ Missed", Color(0xFFFFEBEE))
        isOverdue -> Triple(StatusAmber, "⏰ Overdue!", Color(0xFFFFF8E1))
        doseRecord != null -> Triple(MedGreen, "Take at ${timeFmt.format(Date(doseRecord.scheduledTime))}", Color.White)
        else -> Triple(Color.Gray, "Not scheduled today", Color.White)
    }

    val pillColor = try {
        val hex = medication.pillColorHex.trimStart('#')
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f
        )
    } catch (_: Exception) { MedGreen }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(pillColor.copy(alpha = 0.15f))
                    .border(3.dp, pillColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(medication.pillEmoji, fontSize = 32.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A2E1A)
                )
                Text(
                    medication.purpose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (status == DoseStatus.PENDING && !isOverdue) {
                Text("→", fontSize = 24.sp, color = MedGreen)
            }
        }
    }
}

@Composable
fun EngagementDialog(question: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("👋 Hey there!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(question, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Just checking in to make sure you're okay!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I'm here! 😊", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = Color.Gray) }
        }
    )
}

@Composable
fun DoubleDoseWarningDialog(medicationName: String, takenAt: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🛡️ Already Taken!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
        },
        text = {
            Text(
                "You already took $medicationName at $takenAt.\n\nTaking it again could be harmful. Please wait for the next scheduled dose.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OK, I understand", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CameraIndicatorDot() {
    val pulse = rememberInfiniteTransition(label = "cam_pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .background(Color(0xFF4CAF50), CircleShape)
    )
}

private fun getGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

