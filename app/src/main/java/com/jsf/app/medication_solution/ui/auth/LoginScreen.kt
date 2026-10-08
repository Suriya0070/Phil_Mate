package com.jsf.app.medication_solution.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsf.app.medication_solution.data.model.UserRole
import com.jsf.app.medication_solution.ui.theme.CareBlue
import com.jsf.app.medication_solution.ui.theme.MedGreen

@Composable
fun RoleSelectionScreen(onRoleSelected: (UserRole) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F8E9))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💊", fontSize = 72.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "MediCare",
            style = MaterialTheme.typography.headlineLarge,
            color = MedGreen,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Smart Medication Management",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Text(
            "Who are you?",
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF1A2E1A)
        )
        Spacer(Modifier.height(24.dp))

        RoleCard(
            emoji = "👴",
            title = "I'm a Senior",
            subtitle = "Manage my daily medications\nand health routine",
            color = MedGreen,
            onClick = { onRoleSelected(UserRole.SENIOR) }
        )
        Spacer(Modifier.height(16.dp))
        RoleCard(
            emoji = "👨‍👩‍👧",
            title = "I'm a Caregiver",
            subtitle = "Monitor and support\nmy family member",
            color = CareBlue,
            onClick = { onRoleSelected(UserRole.CAREGIVER) }
        )
    }
}

@Composable
private fun RoleCard(
    emoji: String,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 48.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.weight(1f))
            Text("→", fontSize = 28.sp, color = Color.White)
        }
    }
}

@Composable
fun LoginScreen(
    role: UserRole,
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoggedIn, state.user) {
        if (state.isLoggedIn && state.user != null) onLoginSuccess()
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            // Error shown inline
        }
    }

    val roleColor = if (role == UserRole.SENIOR) MedGreen else CareBlue
    val roleEmoji = if (role == UserRole.SENIOR) "👴" else "👨‍👩‍👧"
    val roleLabel = if (role == UserRole.SENIOR) "Senior" else "Caregiver"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F8E9))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(roleColor),
            contentAlignment = Alignment.Center
        ) {
            Text(roleEmoji, fontSize = 40.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (isRegister) "Create $roleLabel Account" else "$roleLabel Login",
            style = MaterialTheme.typography.headlineMedium,
            color = roleColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(visible = isRegister) {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; viewModel.clearError() },
            label = { Text("Email Address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text("Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        AnimatedVisibility(visible = state.error != null) {
            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠️ $err",
                        color = Color(0xFFB71C1C),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (isRegister) {
                    viewModel.register(name, email, password, role, phone)
                } else {
                    viewModel.login(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = roleColor),
            enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    if (isRegister) "Create Account" else "Sign In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { isRegister = !isRegister; viewModel.clearError() }) {
            Text(
                if (isRegister) "Already have an account? Sign In" else "New here? Create Account",
                color = roleColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        TextButton(onClick = onBack) {
            Text("← Change Role", color = Color.Gray)
        }
    }
}
