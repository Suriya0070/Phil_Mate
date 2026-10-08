package com.jsf.app.medication_solution

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jsf.app.medication_solution.ui.navigation.AppNavigation
import com.jsf.app.medication_solution.ui.theme.Medication_SolutionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Medication_SolutionTheme {
                AppNavigation()
            }
        }
    }
}
