package com.jsf.app.medication_solution.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = MedGreen,
    onPrimary = Color.White,
    primaryContainer = MedGreenContainer,
    onPrimaryContainer = MedGreenDark,
    secondary = WarnAmber,
    onSecondary = Color.White,
    secondaryContainer = WarnAmberContainer,
    onSecondaryContainer = Color(0xFFE65100),
    tertiary = CareBlue,
    onTertiary = Color.White,
    tertiaryContainer = CareBlueContainer,
    onTertiaryContainer = Color(0xFF0D47A1),
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedContainer,
    onErrorContainer = Color(0xFF7F0000),
    background = Color(0xFFF1F8E9),
    onBackground = Color(0xFF1A2E1A),
    surface = Color.White,
    onSurface = Color(0xFF1A2E1A),
    surfaceVariant = Color(0xFFDCEFDC),
    onSurfaceVariant = Color(0xFF424E42),
    outline = Color(0xFF72886F)
)

@Composable
fun Medication_SolutionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
