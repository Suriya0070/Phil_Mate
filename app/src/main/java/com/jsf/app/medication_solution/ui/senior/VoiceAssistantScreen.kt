package com.jsf.app.medication_solution.ui.senior

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.ui.theme.MedGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(
    viewModel: VoiceAssistantViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var typedText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.conversation.size) {
        if (state.conversation.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.conversation.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🎙️ Voice Assistant", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (state.isOllamaAvailable) "AI: ${state.selectedModel}" else "AI: Offline mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (state.conversation.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearConversation) {
                            Icon(Icons.Default.Delete, "Clear", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedGreen)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Emotion indicator
                if (state.conversation.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = emotionColor(state.detectedEmotion).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${state.detectedEmotion.emoji} Feeling ${state.detectedEmotion.label}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = emotionColor(state.detectedEmotion),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Status message
                if (state.currentTranscript.isNotBlank()) {
                    Text(
                        "\"${state.currentTranscript}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MedGreen,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Text input + mic
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { typedText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type or tap mic to speak...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (typedText.isNotBlank()) {
                                viewModel.sendMessage(typedText)
                                typedText = ""
                                keyboardController?.hide()
                            }
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        enabled = state.voiceState != VoiceState.LISTENING && state.voiceState != VoiceState.THINKING
                    )
                    Spacer(Modifier.width(8.dp))
                    if (typedText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(typedText)
                                typedText = ""
                                keyboardController?.hide()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(MedGreen, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, null, tint = Color.White)
                        }
                    } else {
                        MicButton(
                            voiceState = state.voiceState,
                            onStart = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onStop = viewModel::stopListening
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F8E9))
                .padding(padding)
        ) {
            // Ollama not available banner
            if (!state.isOllamaAvailable) {
                var urlInput by remember { mutableStateOf(state.customOllamaUrl) }
                val ctx = LocalContext.current
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚠️ Cannot reach Ollama", style = MaterialTheme.typography.titleSmall, color = Color(0xFFF57F17), fontWeight = FontWeight.Bold)
                        Text("Is Ollama running on your laptop?", style = MaterialTheme.typography.bodySmall, color = Color(0xFF795548))
                        Text("Start it with: ollama serve", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Text("Or enter a custom Ollama URL:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF795548))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("http://192.168.1.x:11434", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.updateOllamaUrl(ctx, urlInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17))
                            ) { Text("Connect", color = Color.White) }
                        }
                        Text("💡 Offline mode: answers from built-in knowledge", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }

            if (state.conversation.isEmpty()) {
                WelcomePrompt(seniorName = state.seniorName, onSuggestion = { viewModel.sendMessage(it) })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.conversation) { turn ->
                        ChatBubble(turn = turn)
                    }
                    if (state.voiceState == VoiceState.THINKING) {
                        item { ThinkingBubble() }
                    }
                    if (state.errorMessage != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Text(
                                    "⚠️ ${state.errorMessage}",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB71C1C)
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MicButton(voiceState: VoiceState, onStart: () -> Unit, onStop: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = if (voiceState == VoiceState.LISTENING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "scale"
    )

    val (bgColor, icon) = when (voiceState) {
        VoiceState.LISTENING -> Color(0xFFD32F2F) to Icons.Default.MicOff
        VoiceState.THINKING -> Color(0xFFFFA000) to Icons.Default.Mic
        VoiceState.SPEAKING -> Color(0xFF388E3C) to Icons.Default.Mic
        else -> MedGreen to Icons.Default.Mic
    }

    IconButton(
        onClick = {
            if (voiceState == VoiceState.LISTENING) onStop() else onStart()
        },
        enabled = voiceState != VoiceState.THINKING && voiceState != VoiceState.SPEAKING,
        modifier = Modifier
            .size(52.dp)
            .scale(scale)
            .background(bgColor, CircleShape)
    ) {
        Icon(icon, "Mic", tint = Color.White)
    }
}

@Composable
private fun ChatBubble(turn: ConversationTurn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!turn.isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MedGreen),
                contentAlignment = Alignment.Center
            ) { Text("🤖", fontSize = 18.sp) }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (turn.isUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (turn.isUser) 16.dp else 4.dp,
                    bottomEnd = if (turn.isUser) 4.dp else 16.dp
                ),
                color = if (turn.isUser) MedGreen else Color.White,
                shadowElevation = 2.dp
            ) {
                Text(
                    turn.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (turn.isUser) Color.White else Color(0xFF1A2E1A)
                )
            }
            turn.emotion?.let { emotion ->
                if (!turn.isUser) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${emotion.emoji} ${emotion.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = emotionColor(emotion)
                    )
                }
            }
        }
        if (turn.isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) { Text("👴", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val anim = rememberInfiniteTransition(label = "dots")
    val alpha by anim.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(MedGreen),
            contentAlignment = Alignment.Center
        ) { Text("🤖", fontSize = 18.sp) }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = Color.White, shadowElevation = 2.dp) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(MedGreen.copy(alpha = if (i == 0) alpha else if (i == 1) 0.6f else 1f - alpha), CircleShape)
                    )
                    if (i < 2) Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomePrompt(seniorName: String, onSuggestion: (String) -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎙️", fontSize = 72.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Hi ${seniorName.ifBlank { "there" }}! 👋",
            style = MaterialTheme.typography.headlineMedium,
            color = MedGreen,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "I'm your medication companion.\nAsk me anything!",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        // Quick suggestion chips
        val suggestions = listOf(
            "Did I take my BP tablet?",
            "What medicines do I take now?",
            "How am I doing today?",
            "I'm not feeling well"
        )
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSuggestion(suggestion) },
                label = { Text(suggestion) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Tap the 🎙️ mic button below to speak,\nor type your question.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

private fun emotionColor(emotion: DetectedEmotion) = when (emotion) {
    DetectedEmotion.HAPPY -> Color(0xFF388E3C)
    DetectedEmotion.NEUTRAL -> Color(0xFF757575)
    DetectedEmotion.TIRED -> Color(0xFF7B1FA2)
    DetectedEmotion.WORRIED -> Color(0xFFF57C00)
    DetectedEmotion.SAD -> Color(0xFF1565C0)
    DetectedEmotion.PAIN -> Color(0xFFD32F2F)
}
