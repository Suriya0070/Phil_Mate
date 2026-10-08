package com.jsf.app.medication_solution.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jsf.app.medication_solution.data.model.DoseRecord
import com.jsf.app.medication_solution.data.model.Medication
import com.jsf.app.medication_solution.data.model.UserRole
import com.jsf.app.medication_solution.ui.auth.AuthViewModel
import com.jsf.app.medication_solution.ui.auth.LoginScreen
import com.jsf.app.medication_solution.ui.auth.RoleSelectionScreen
import com.jsf.app.medication_solution.ui.caregiver.CaregiverDashboardScreen
import com.jsf.app.medication_solution.ui.caregiver.CaregiverViewModel
import com.jsf.app.medication_solution.ui.caregiver.SeniorDetailScreen
import com.jsf.app.medication_solution.ui.senior.MedicationDetailScreen
import com.jsf.app.medication_solution.ui.senior.MoodCheckScreen
import com.jsf.app.medication_solution.ui.senior.SeniorHomeScreen
import com.jsf.app.medication_solution.ui.senior.SeniorViewModel
import com.jsf.app.medication_solution.ui.senior.VoiceAssistantScreen
import com.jsf.app.medication_solution.ui.senior.VoiceAssistantViewModel

object Routes {
    const val ROLE_SELECTION = "role_selection"
    const val LOGIN = "login/{role}"
    const val SENIOR_HOME = "senior_home"
    const val MEDICATION_DETAIL = "medication_detail"
    const val MOOD_CHECK = "mood_check"
    const val VOICE_ASSISTANT = "voice_assistant"
    const val CAREGIVER_DASHBOARD = "caregiver_dashboard"
    const val SENIOR_DETAIL = "senior_detail"

    fun login(role: UserRole) = "login/${role.name}"
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel = viewModel()) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsState()

    val startDest = when {
        authState.isLoggedIn && authState.user != null -> {
            if (authState.user!!.userRole() == UserRole.SENIOR) Routes.SENIOR_HOME
            else Routes.CAREGIVER_DASHBOARD
        }
        else -> Routes.ROLE_SELECTION
    }

    // Shared state for passing medication/dose between screens within senior flow
    var currentDose by remember { mutableStateOf<DoseRecord?>(null) }
    var currentMed by remember { mutableStateOf<Medication?>(null) }

    NavHost(navController = navController, startDestination = startDest) {

        composable(Routes.ROLE_SELECTION) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    navController.navigate(Routes.login(role))
                }
            )
        }

        composable(
            route = Routes.LOGIN,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStack ->
            val roleStr = backStack.arguments?.getString("role") ?: UserRole.SENIOR.name
            val role = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.SENIOR)
            LoginScreen(
                role = role,
                viewModel = authViewModel,
                onLoginSuccess = {
                    val dest = if (authViewModel.state.value.user?.userRole() == UserRole.SENIOR)
                        Routes.SENIOR_HOME else Routes.CAREGIVER_DASHBOARD
                    navController.navigate(dest) {
                        popUpTo(Routes.ROLE_SELECTION) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SENIOR_HOME) {
            val seniorViewModel: SeniorViewModel = viewModel()
            SeniorHomeScreen(
                viewModel = seniorViewModel,
                onMedicationClick = { dose, med ->
                    currentDose = dose
                    currentMed = med
                    navController.navigate(Routes.MEDICATION_DETAIL)
                },
                onVoiceAssistant = { navController.navigate(Routes.VOICE_ASSISTANT) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.VOICE_ASSISTANT) {
            val voiceViewModel: VoiceAssistantViewModel = viewModel()
            VoiceAssistantScreen(
                viewModel = voiceViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MEDICATION_DETAIL) {
            val dose = currentDose
            val med = currentMed
            if (dose == null || med == null) {
                navController.popBackStack()
                return@composable
            }
            val seniorViewModel: SeniorViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.SENIOR_HOME)
            )
            MedicationDetailScreen(
                doseRecord = dose,
                medication = med,
                viewModel = seniorViewModel,
                onBack = { navController.popBackStack() },
                onShowMoodCheck = {
                    navController.navigate(Routes.MOOD_CHECK)
                }
            )
        }

        composable(Routes.MOOD_CHECK) {
            val dose = currentDose
            val med = currentMed
            if (dose == null || med == null) {
                navController.popBackStack()
                return@composable
            }
            val seniorViewModel: SeniorViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.SENIOR_HOME)
            )
            MoodCheckScreen(
                doseRecord = dose,
                medicationName = med.name,
                viewModel = seniorViewModel,
                onDone = {
                    currentDose = null
                    currentMed = null
                    navController.popBackStack(Routes.SENIOR_HOME, false)
                }
            )
        }

        composable(Routes.CAREGIVER_DASHBOARD) {
            val caregiverViewModel: CaregiverViewModel = viewModel()
            CaregiverDashboardScreen(
                viewModel = caregiverViewModel,
                onViewSeniorDetail = { _ ->
                    navController.navigate(Routes.SENIOR_DETAIL)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.ROLE_SELECTION) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SENIOR_DETAIL) {
            val caregiverViewModel: CaregiverViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Routes.CAREGIVER_DASHBOARD)
            )
            SeniorDetailScreen(
                viewModel = caregiverViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
