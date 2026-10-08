package com.jsf.app.medication_solution.ui.senior

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.ui.theme.MedGreen
import com.jsf.app.medication_solution.ui.theme.StatusGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    doseRecord: DoseRecord,
    medication: Medication,
    viewModel: SeniorViewModel,
    onBack: () -> Unit,
    onShowMoodCheck: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val liveRecord = viewModel.getDoseForMedication(medication.id) ?: doseRecord

    val pillColor = try {
        val hex = medication.pillColorHex.trimStart('#')
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f
        )
    } catch (_: Exception) { MedGreen }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medicine Detail", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pillColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F8E9))
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Pill visual
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(pillColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(medication.pillEmoji, fontSize = 64.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                medication.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A2E1A),
                textAlign = TextAlign.Center
            )
            Text(
                medication.dosage,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )

            Spacer(Modifier.height(24.dp))

            // Purpose card
            InfoRow(icon = "🎯", label = "Purpose", value = medication.purpose)
            Spacer(Modifier.height(8.dp))
            InfoRow(
                icon = "🕐",
                label = "Scheduled",
                value = timeFmt.format(Date(liveRecord.scheduledTime))
            )
            if (medication.instructions.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoRow(icon = "📝", label = "Instructions", value = medication.instructions)
            }
            Spacer(Modifier.height(8.dp))
            InfoRow(
                icon = "💊",
                label = "Pills remaining",
                value = "${medication.remainingPills} tablets",
                valueColor = if (medication.remainingPills <= 7) Color(0xFFF57F17) else Color(0xFF1A2E1A)
            )

            Spacer(Modifier.height(32.dp))

            // Status / Action area
            when {
                liveRecord.isTaken() -> {
                    TakenStatusCard(takenAt = timeFmt.format(Date(liveRecord.takenAt)))
                }
                liveRecord.isMissed() -> {
                    MissedStatusCard()
                }
                liveRecord.isOverdue() -> {
                    OverdueActionSection(
                        isLoading = state.isLoading,
                        onConfirm = { onShowMoodCheck() }
                    )
                }
                else -> {
                    PendingActionSection(
                        scheduledTime = timeFmt.format(Date(liveRecord.scheduledTime)),
                        isLoading = state.isLoading,
                        onConfirm = { onShowMoodCheck() }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: String, valueColor: Color = Color(0xFF1A2E1A)) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PendingActionSection(scheduledTime: String, isLoading: Boolean, onConfirm: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Scheduled for $scheduledTime",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "I TOOK IT ✓",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun OverdueActionSection(isLoading: Boolean, onConfirm: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFF57F17))
                Spacer(Modifier.width(8.dp))
                Text(
                    "This dose is overdue! Did you take it?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF7F5000)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17)),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            } else {
                Text(
                    "Yes, I Took It ✓",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun TakenStatusCard(takenAt: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = StatusGreen,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Taken at $takenAt",
                style = MaterialTheme.typography.headlineSmall,
                color = MedGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Great job! Your caregiver has been notified.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MissedStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("😟", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "This dose was missed",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFFB71C1C),
                fontWeight = FontWeight.Bold
            )
            Text(
                "Your caregiver has been notified. Please do not double your next dose.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF7F0000),
                textAlign = TextAlign.Center
            )
        }
    }
}
