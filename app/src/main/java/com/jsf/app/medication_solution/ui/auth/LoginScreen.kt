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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

// ─── Dark palette for caregiver screens ─────────────────────────────────────
private val CG_BG      = Color(0xFF0D1117)
private val CG_CARD    = Color(0xFF161B22)
private val CG_BORDER  = Color(0xFF30363D)
private val CG_TEXT    = Color(0xFFF0F6FC)
private val CG_SUB     = Color(0xFF8B949E)
private val CG_BLUE    = Color(0xFF58A6FF)

// ─── Role Selection ──────────────────────────────────────────────────────────

@Composable
fun RoleSelectionScreen(onRoleSelected: (UserRole) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // ── Top half — Patient (light green) ──────────────────────────────────
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .background(Color(0xFFF1F8E9))
                .clickable { onRoleSelected(UserRole.SENIOR) }
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("👴", fontSize = 52.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("PATIENT", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                color = MedGreen, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text("Senior Mode", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Manage my daily medications & health routine",
                fontSize = 12.sp, color = Color(0xFF388E3C),
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(MedGreen)
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("I'm a Patient  →", color = Color.White, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold)
            }
        }

        // ── Divider ────────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFCCCCCC)))

        // ── Bottom half — Caregiver (dark, summarized) ────────────────────────
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .background(CG_BG)
                .clickable { onRoleSelected(UserRole.CAREGIVER) }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🩺", fontSize = 36.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("CAREGIVER", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        color = CG_BLUE, letterSpacing = 2.sp)
                    Text("Care Dashboard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CG_TEXT)
                }
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                "📋  View & add patient medicines",
                "🎙️  Record alarm voice messages",
                "🔔  Get missed dose alerts instantly",
                "📊  Track 7-day adherence trends"
            ).forEach { line ->
                Text(line, fontSize = 12.sp, color = CG_SUB, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(CG_BLUE)
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text("I'm a Caregiver  →", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Login Screen ─────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(
    role: UserRole,
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var isRegister by remember { mutableStateOf(false) }
    var name     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone    by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoggedIn, state.user) {
        if (state.isLoggedIn && state.user != null) onLoginSuccess()
    }

    if (role == UserRole.CAREGIVER) {
        CaregiverLoginScreen(
            isRegister = isRegister,
            name = name, email = email, password = password, phone = phone,
            showPassword = showPassword,
            isLoading = state.isLoading, error = state.error,
            onNameChange = { name = it },
            onEmailChange = { email = it; viewModel.clearError() },
            onPasswordChange = { password = it; viewModel.clearError() },
            onPhoneChange = { phone = it },
            onShowPasswordToggle = { showPassword = !showPassword },
            onToggleMode = { isRegister = !isRegister; viewModel.clearError() },
            onSubmit = {
                if (isRegister) viewModel.register(name, email, password, role, phone)
                else viewModel.login(email, password)
            },
            onBack = onBack
        )
    } else {
        SeniorLoginScreen(
            isRegister = isRegister,
            name = name, email = email, password = password, phone = phone,
            showPassword = showPassword,
            isLoading = state.isLoading, error = state.error,
            onNameChange = { name = it },
            onEmailChange = { email = it; viewModel.clearError() },
            onPasswordChange = { password = it; viewModel.clearError() },
            onPhoneChange = { phone = it },
            onShowPasswordToggle = { showPassword = !showPassword },
            onToggleMode = { isRegister = !isRegister; viewModel.clearError() },
            onSubmit = {
                if (isRegister) viewModel.register(name, email, password, role, phone)
                else viewModel.login(email, password)
            },
            onBack = onBack
        )
    }
}

// ─── Senior Login (light/green) ───────────────────────────────────────────────

@Composable
private fun SeniorLoginScreen(
    isRegister: Boolean,
    name: String, email: String, password: String, phone: String,
    showPassword: Boolean, isLoading: Boolean, error: String?,
    onNameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit, onPhoneChange: (String) -> Unit,
    onShowPasswordToggle: () -> Unit, onToggleMode: () -> Unit,
    onSubmit: () -> Unit, onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFFF1F8E9))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(90.dp).clip(CircleShape).background(MedGreen),
            contentAlignment = Alignment.Center) {
            Text("👴", fontSize = 46.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("PillMate 💊", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MedGreen)
        Text(if (isRegister) "Create Senior Account" else "Senior Login",
            fontSize = 15.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(visible = isRegister) {
            Column {
                OutlinedTextField(name, onNameChange, label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(phone, onPhoneChange, label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
            }
        }
        OutlinedTextField(email, onEmailChange, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, onPasswordChange, label = { Text("Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onShowPasswordToggle) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        AnimatedVisibility(error != null) {
            error?.let {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("⚠️ $it", color = Color(0xFFB71C1C), modifier = Modifier.padding(12.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank()) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(if (isRegister) "Create Account" else "Sign In",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onToggleMode) {
            Text(if (isRegister) "Already have account? Sign In" else "New here? Create Account",
                color = MedGreen)
        }
        TextButton(onClick = onBack) { Text("← Change Role", color = Color.Gray) }
    }
}

// ─── Caregiver Login (dark) ───────────────────────────────────────────────────

@Composable
private fun CaregiverLoginScreen(
    isRegister: Boolean,
    name: String, email: String, password: String, phone: String,
    showPassword: Boolean, isLoading: Boolean, error: String?,
    onNameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit, onPhoneChange: (String) -> Unit,
    onShowPasswordToggle: () -> Unit, onToggleMode: () -> Unit,
    onSubmit: () -> Unit, onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(CG_BG)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Top badge
        Box(Modifier.size(90.dp).clip(CircleShape)
            .background(CG_BLUE.copy(alpha = 0.18f))
            .border(2.dp, CG_BLUE.copy(0.5f), CircleShape),
            contentAlignment = Alignment.Center) {
            Text("🩺", fontSize = 46.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("MediCare", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CG_BLUE)
        Text("CAREGIVER COMMAND CENTER", fontSize = 11.sp,
            color = CG_SUB, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (isRegister) "Create Caregiver Account" else "Sign in to monitor your patient",
            fontSize = 13.sp, color = CG_SUB)
        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(visible = isRegister) {
            Column {
                DarkTextField(name, onNameChange, "Full Name")
                Spacer(Modifier.height(10.dp))
                DarkTextField(phone, onPhoneChange, "Phone Number", KeyboardType.Phone)
                Spacer(Modifier.height(10.dp))
            }
        }
        DarkTextField(email, onEmailChange, "Email Address", KeyboardType.Email)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password, onValueChange = onPasswordChange,
            label = { Text("Password", color = CG_SUB) },
            textStyle = androidx.compose.ui.text.TextStyle(color = CG_TEXT, fontSize = 15.sp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onShowPasswordToggle) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = CG_SUB)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CG_BLUE, unfocusedBorderColor = CG_BORDER,
                cursorColor = CG_BLUE,
                focusedLabelColor = CG_BLUE, unfocusedLabelColor = CG_SUB
            ),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        AnimatedVisibility(error != null) {
            error?.let {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF85149).copy(0.12f))
                    .border(1.dp, Color(0xFFF85149).copy(0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)) {
                    Text("⚠️ $it", color = Color(0xFFF85149), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CG_BLUE),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank()) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(if (isRegister) "Create Account" else "Sign In →",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onToggleMode) {
            Text(if (isRegister) "Already have account? Sign In" else "New? Create Account",
                color = CG_BLUE, fontSize = 13.sp)
        }
        TextButton(onClick = onBack) { Text("← Change Role", color = CG_SUB, fontSize = 13.sp) }
    }
}

@Composable
private fun DarkTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = CG_SUB) },
        textStyle = androidx.compose.ui.text.TextStyle(color = CG_TEXT, fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CG_BLUE, unfocusedBorderColor = CG_BORDER,
            cursorColor = CG_BLUE,
            focusedLabelColor = CG_BLUE, unfocusedLabelColor = CG_SUB
        ),
        modifier = Modifier.fillMaxWidth(), singleLine = true
    )
}
