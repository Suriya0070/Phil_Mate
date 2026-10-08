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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.data.model.Alert
import com.jsf.app.medication_solution.data.model.AlertSeverity
import com.jsf.app.medication_solution.data.model.AlertType
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.DoseStatus
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
fun CaregiverDashboardScreen(
    viewModel: CaregiverViewModel,
    onViewSeniorDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    state.linkSuccess?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearLinkSuccess()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MediCare", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(
                            "Caregiver Dashboard",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CareBlue)
            )
        }
    ) { padding ->
        if (state.isLoading && state.seniorSnapshot == null) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Caregiver welcome
            item {
                Text(
                    "Welcome, ${state.caregiver?.name ?: "Caregiver"} 👋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CareBlue
                )
            }

            // Link success banner
            state.linkSuccess?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                        Text(
                            "✅ $msg",
                            modifier = Modifier.padding(16.dp),
                            color = MedGreen,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // No senior linked state
            if (state.noSeniorLinked || state.seniorSnapshot == null) {
                item {
                    NoSeniorLinkedCard(onLinkClick = viewModel::showLinkDialog)
                }
            } else {
                val snap = state.seniorSnapshot!!

                // Senior status card
                item {
                    SeniorStatusCard(
                        snapshot = snap,
                        onViewDetail = { onViewSeniorDetail(snap.user.id) },
                        onCall = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${snap.user.phone}"))
                            context.startActivity(intent)
                        }
                    )
                }

                // Stats row
                item {
                    StatsRow(snapshot = snap)
                }

                // Alerts section
                if (state.alerts.isNotEmpty()) {
                    item {
                        Text(
                            "Recent Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E)
                        )
                    }
                    items(state.alerts.take(5)) { alert ->
                        AlertCard(alert = alert, onResolve = { viewModel.resolveAlert(alert.id) })
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SeniorStatusCard(
    snapshot: SeniorSnapshot,
    onViewDetail: () -> Unit,
    onCall: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val statusColor = levelToColor(snapshot.statusLevel)
    val (taken, total) = snapshot.todayDoses.let { doses ->
        doses.count { it.isTaken() } to doses.size
    }
    val lastSeenText = when {
        snapshot.inactiveMinutes < 1 -> "Just now"
        snapshot.inactiveMinutes < 60 -> "${snapshot.inactiveMinutes} min ago"
        else -> "${snapshot.inactiveMinutes / 60}h ${snapshot.inactiveMinutes % 60}m ago"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👴", fontSize = 32.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        snapshot.user.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Last seen: $lastSeenText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (snapshot.inactiveMinutes > 30) Color(0xFFF57F17) else Color.Gray
                    )
                }
                StatusBadge(level = snapshot.statusLevel)
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Dose progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💊", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Today's Doses: $taken / $total taken",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (total > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else taken.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = statusColor,
                            trackColor = Color(0xFFE0E0E0)
                        )
                    }
                }
            }

            snapshot.latestMood?.let { mood ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mood.moodLevel().emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Feeling: ${mood.moodLevel().label}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mood.moodLevel().score <= 2) Color(0xFFB71C1C) else Color(0xFF1A2E1A)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedGreen)
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call")
                }
                Button(
                    onClick = onViewDetail,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CareBlue)
                ) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Full Details", color = Color.White)
                }
            }
        }
    }
}

private fun levelToColor(level: SeniorStatusLevel): Color = when (level) {
    SeniorStatusLevel.GREEN -> Color(0xFF4CAF50.toInt())
    SeniorStatusLevel.AMBER -> Color(0xFFFFA726.toInt())
    SeniorStatusLevel.RED -> Color(0xFFF44336.toInt())
}

@Composable
private fun StatusBadge(level: SeniorStatusLevel) {
    val color = levelToColor(level)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                level.label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatsRow(snapshot: SeniorSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            emoji = "📊",
            label = "7-Day\nAdherence",
            value = "${(snapshot.weeklyAdherence * 100).toInt()}%",
            valueColor = when {
                snapshot.weeklyAdherence >= 0.8f -> StatusGreen
                snapshot.weeklyAdherence >= 0.5f -> StatusAmber
                else -> StatusRed
            }
        )
        StatCard(
            modifier = Modifier.weight(1f),
            emoji = "⏰",
            label = "Inactive\nFor",
            value = if (snapshot.inactiveMinutes < 60)
                "${snapshot.inactiveMinutes}m"
            else "${snapshot.inactiveMinutes / 60}h",
            valueColor = when {
                snapshot.inactiveMinutes < 30 -> StatusGreen
                snapshot.inactiveMinutes < 60 -> StatusAmber
                else -> StatusRed
            }
        )
        StatCard(
            modifier = Modifier.weight(1f),
            emoji = "🚨",
            label = "Open\nAlerts",
            value = "${snapshot.todayDoses.count { it.isMissed() || it.isOverdue() }}",
            valueColor = if (snapshot.todayDoses.any { it.isMissed() }) StatusRed else StatusGreen
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, emoji: String, label: String, value: String, valueColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
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
    val textColor = when (alert.alertSeverity()) {
        AlertSeverity.CRITICAL, AlertSeverity.HIGH -> Color(0xFF7F0000)
        AlertSeverity.MEDIUM -> Color(0xFF7F5000)
        AlertSeverity.LOW -> MedGreen
    }

    if (alert.isResolved) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(alert.alertType().emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.alertType().label,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.8f)
                )
                Text(
                    timeFmt.format(Date(alert.timestamp)),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
            if (alert.alertSeverity() != AlertSeverity.LOW) {
                IconButton(onClick = onResolve) {
                    Icon(Icons.Default.CheckCircle, "Resolve", tint = MedGreen)
                }
            }
        }
    }
}

@Composable
private fun NoSeniorLinkedCard(onLinkClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("👴", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "No Senior Linked Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Link your family member's account to start monitoring their medications.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLinkClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Link Family Member", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LinkSeniorDialog(
    email: String,
    onEmailChange: (String) -> Unit,
    onLink: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Link Family Member", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Enter the email address of the senior's account to link and monitor their medications.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Senior's Email Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onLink,
                enabled = email.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = CareBlue)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Link Account", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
