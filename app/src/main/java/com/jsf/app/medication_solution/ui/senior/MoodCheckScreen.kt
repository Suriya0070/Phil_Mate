package com.jsf.app.medication_solution.ui.senior

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.jsf.app.medication_solution.data.model.MoodLevel
import com.jsf.app.medication_solution.ui.theme.MedGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodCheckScreen(
    doseRecord: DoseRecord,
    medicationName: String,
    viewModel: SeniorViewModel,
    onDone: () -> Unit
) {
    var selectedMood by remember { mutableStateOf<MoodLevel?>(null) }
    var submitted by remember { mutableStateOf(false) }
    // Use live record if available so we pass the correct one to confirmDose
    val liveRecord = viewModel.getDoseForMedication(doseRecord.medicationId) ?: doseRecord

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How Are You Feeling?", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedGreen)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F8E9))
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            if (!submitted) {
                Text("😊", fontSize = 80.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "After taking $medicationName,\nhow are you feeling?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A2E1A),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Your response helps your doctor\nand family know you're doing well.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(40.dp))

                // 3 main mood options in large format
                val mainMoods = listOf(MoodLevel.GREAT, MoodLevel.OKAY, MoodLevel.NOT_GOOD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    mainMoods.forEach { mood ->
                        MoodButton(
                            mood = mood,
                            isSelected = selectedMood == mood,
                            onClick = { selectedMood = mood }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = {
                        viewModel.confirmDose(liveRecord, selectedMood ?: MoodLevel.OKAY)
                        submitted = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
                ) {
                    Text(
                        "Submit & Done",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.confirmDose(liveRecord, null)
                        onDone()
                    }
                ) {
                    Text("Skip for now", color = Color.Gray)
                }
            } else {
                ThankYouContent(mood = selectedMood, onDone = onDone)
            }
        }
    }
}

@Composable
private fun MoodButton(mood: MoodLevel, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) when (mood) {
                        MoodLevel.GREAT, MoodLevel.GOOD -> Color(0xFFE8F5E9)
                        MoodLevel.OKAY -> Color(0xFFFFF8E1)
                        else -> Color(0xFFFFEBEE)
                    } else Color.White
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) when (mood) {
                        MoodLevel.GREAT, MoodLevel.GOOD -> MedGreen
                        MoodLevel.OKAY -> Color(0xFFF57F17)
                        else -> Color(0xFFB71C1C)
                    } else Color.LightGray,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(mood.emoji, fontSize = 44.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            mood.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = Color(0xFF1A2E1A)
        )
    }
}

@Composable
private fun ThankYouContent(mood: MoodLevel?, onDone: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Text(mood?.emoji ?: "✅", fontSize = 96.sp)
    Spacer(Modifier.height(24.dp))
    Text(
        "Thank You!",
        style = MaterialTheme.typography.headlineLarge,
        color = MedGreen,
        fontWeight = FontWeight.ExtraBold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        when {
            mood == null || mood.score >= 3 -> "Your family has been notified that you're doing well!"
            else -> "Your family has been notified. Someone will check in with you soon."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Gray,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(40.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
    ) {
        Text("Back to My Medicines", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
