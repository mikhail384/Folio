package com.example.fractions.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.fractions.core.OperandInput
import com.example.fractions.core.Operation

data class CalculatorUiState(
    val first: OperandInput = OperandInput(),
    val operation: Operation = Operation.ADD,
    val second: OperandInput = OperandInput(),
)

/** Хранит ввод пользователя; переживает поворот экрана. */
class CalculatorViewModel : ViewModel() {

    var uiState by mutableStateOf(CalculatorUiState())
        private set

    fun updateFirst(input: OperandInput) {
        uiState = uiState.copy(first = input.sanitized())
    }

    fun updateSecond(input: OperandInput) {
        uiState = uiState.copy(second = input.sanitized())
    }

    fun selectOperation(operation: Operation) {
        uiState = uiState.copy(operation = operation)
    }

    fun clear() {
        uiState = uiState.copy(first = OperandInput(), second = OperandInput())
    }
}
