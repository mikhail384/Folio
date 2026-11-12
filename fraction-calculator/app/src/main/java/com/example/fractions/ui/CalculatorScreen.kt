package com.example.fractions.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fractions.R
import com.example.fractions.core.CalcError
import com.example.fractions.core.CalcResult
import com.example.fractions.core.Fraction
import com.example.fractions.core.OperandInput
import com.example.fractions.core.Operation
import com.example.fractions.core.calculate
import com.example.fractions.core.toDecimalText
import com.example.fractions.core.toFractionString
import com.example.fractions.core.toMixedNumberString
import com.example.fractions.ui.theme.FractionCalculatorTheme

/** Экран калькулятора: берёт состояние из [CalculatorViewModel]. */
@Composable
fun CalculatorScreen(viewModel: CalculatorViewModel = viewModel()) {
    CalculatorContent(
        state = viewModel.uiState,
        onFirstChange = viewModel::updateFirst,
        onSecondChange = viewModel::updateSecond,
        onOperationSelect = viewModel::selectOperation,
        onClear = viewModel::clear,
    )
}

/** Интерфейс без ViewModel: состояние и обработчики передаются снаружи (удобно для превью). */
@Composable
fun CalculatorContent(
    state: CalculatorUiState,
    onFirstChange: (OperandInput) -> Unit,
    onSecondChange: (OperandInput) -> Unit,
    onOperationSelect: (Operation) -> Unit,
    onClear: () -> Unit,
) {
    val outcome = calculate(state.first, state.operation, state.second)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FractionCard(
                title = stringResource(R.string.first_fraction),
                input = state.first,
                onChange = onFirstChange,
            )
            OperationRow(selected = state.operation, onSelect = onOperationSelect)
            FractionCard(
                title = stringResource(R.string.second_fraction),
                input = state.second,
                onChange = onSecondChange,
            )
            ResultCard(outcome = outcome)
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.clear))
            }
        }
    }
}

@Composable
private fun FractionCard(
    title: String,
    input: OperandInput,
    onChange: (OperandInput) -> Unit,
) {
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SignToggle(
                    negative = input.negative,
                    onToggle = { onChange(input.copy(negative = !input.negative)) },
                )
                OutlinedTextField(
                    value = input.whole,
                    onValueChange = { onChange(input.copy(whole = it)) },
                    label = { Text(text = stringResource(R.string.whole_part)) },
                    singleLine = true,
                    keyboardOptions = numberKeyboard,
                    modifier = Modifier.width(96.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = input.numerator,
                        onValueChange = { onChange(input.copy(numerator = it)) },
                        label = { Text(text = stringResource(R.string.numerator)) },
                        singleLine = true,
                        keyboardOptions = numberKeyboard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    OutlinedTextField(
                        value = input.denominator,
                        onValueChange = { onChange(input.copy(denominator = it)) },
                        label = { Text(text = stringResource(R.string.denominator)) },
                        singleLine = true,
                        keyboardOptions = numberKeyboard.copy(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignToggle(negative: Boolean, onToggle: () -> Unit) {
    val description = stringResource(if (negative) R.string.sign_negative else R.string.sign_positive)
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = if (negative) "−" else "+",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun OperationRow(selected: Operation, onSelect: (Operation) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Operation.entries.forEach { operation ->
            val description = stringResource(operation.nameRes())
            val itemModifier = Modifier
                .weight(1f)
                .height(56.dp)
                .semantics { contentDescription = description }
            if (operation == selected) {
                Button(onClick = { onSelect(operation) }, modifier = itemModifier) {
                    Text(text = operation.symbol, style = MaterialTheme.typography.titleLarge)
                }
            } else {
                OutlinedButton(onClick = { onSelect(operation) }, modifier = itemModifier) {
                    Text(text = operation.symbol, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(outcome: CalcResult) {
    val isError = outcome is CalcResult.Failure
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = stringResource(R.string.result), style = MaterialTheme.typography.titleMedium)
            when (outcome) {
                CalcResult.Incomplete -> Text(
                    text = stringResource(R.string.hint_fill_both),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is CalcResult.Failure -> Text(
                    text = stringResource(outcome.error.messageRes()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is CalcResult.Value -> FractionResult(outcome.fraction)
            }
        }
    }
}

@Composable
private fun FractionResult(value: Fraction) {
    Text(
        text = value.toFractionString(),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    value.toMixedNumberString()?.let { mixed ->
        Text(
            text = stringResource(R.string.mixed_number, mixed),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (!value.isInteger) {
        val decimal = value.toDecimalText()
        val prefix = if (decimal.isExact) "" else "≈"
        Text(
            text = stringResource(R.string.decimal_value, prefix + decimal.text),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun Operation.nameRes(): Int = when (this) {
    Operation.ADD -> R.string.op_add
    Operation.SUBTRACT -> R.string.op_subtract
    Operation.MULTIPLY -> R.string.op_multiply
    Operation.DIVIDE -> R.string.op_divide
}

private fun CalcError.messageRes(): Int = when (this) {
    is CalcError.ZeroDenominator ->
        if (operand == 1) R.string.error_zero_denominator_first else R.string.error_zero_denominator_second
    CalcError.DivisionByZero -> R.string.error_division_by_zero
}

@Preview(showBackground = true)
@Composable
private fun CalculatorPreview() {
    FractionCalculatorTheme {
        CalculatorContent(
            state = CalculatorUiState(
                first = OperandInput(whole = "1", numerator = "1", denominator = "2"),
                operation = Operation.ADD,
                second = OperandInput(numerator = "2", denominator = "3"),
            ),
            onFirstChange = {},
            onSecondChange = {},
            onOperationSelect = {},
            onClear = {},
        )
    }
}
