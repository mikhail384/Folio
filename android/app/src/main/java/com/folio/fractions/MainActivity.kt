package com.folio.fractions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { CalculatorScreen() } } }
    }
}

private val ops = listOf("+", "−", "×", "÷")

@Composable
fun FractionInput(label: String, whole: MutableState<String>, n: MutableState<String>, d: MutableState<String>) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            field("Целая", whole, Modifier.weight(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                field("Числитель", n, Modifier.fillMaxWidth())
                field("Знаменатель", d, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun field(label: String, s: MutableState<String>, m: Modifier) = OutlinedTextField(
    value = s.value,
    onValueChange = { v -> if (v.all { it.isDigit() || it == '-' } && v.count { it == '-' } <= 1) s.value = v },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = m,
)

/** Пустое поле целой/числителя = 0; пустой знаменатель = 1. */
private fun parse(whole: String, n: String, d: String): Fraction {
    fun big(s: String, default: Long) =
        if (s.isEmpty()) BigInteger.valueOf(default) else s.toBigIntegerOrNull() ?: throw IllegalArgumentException("Некорректное число")
    val den = big(d, 1)
    require(den.signum() != 0) { "Знаменатель не может быть нулём" }
    return Fraction.mixed(big(whole, 0), big(n, 0), den)
}

@Composable
fun CalculatorScreen() {
    val w1 = remember { mutableStateOf("") }; val n1 = remember { mutableStateOf("") }; val d1 = remember { mutableStateOf("") }
    val w2 = remember { mutableStateOf("") }; val n2 = remember { mutableStateOf("") }; val d2 = remember { mutableStateOf("") }
    var op by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<Fraction?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun calc() {
        try {
            val a = parse(w1.value, n1.value, d1.value)
            val b = parse(w2.value, n2.value, d2.value)
            result = when (op) { 0 -> a + b; 1 -> a - b; 2 -> a * b; else -> a / b }
            error = null
        } catch (e: IllegalArgumentException) {
            result = null; error = e.message
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Калькулятор дробей", style = MaterialTheme.typography.headlineMedium)
        FractionInput("Первая дробь", w1, n1, d1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ops.forEachIndexed { i, s ->
                FilterChip(selected = op == i, onClick = { op = i }, label = { Text(s, fontSize = 20.sp) })
            }
        }
        FractionInput("Вторая дробь", w2, n2, d2)
        Button(onClick = ::calc, Modifier.fillMaxWidth()) { Text("Вычислить") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("= ${r}", fontSize = 32.sp)
                    Text("Смешанное: ${r.toMixedString()}")
                    Text("Десятичное: ${r.toDecimalString()}")
                }
            }
        }
    }
}
