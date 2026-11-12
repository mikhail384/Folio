package com.example.fractions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fractions.ui.CalculatorScreen
import com.example.fractions.ui.theme.FractionCalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FractionCalculatorTheme {
                CalculatorScreen()
            }
        }
    }
}
