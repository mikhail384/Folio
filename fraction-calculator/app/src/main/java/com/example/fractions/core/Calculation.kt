package com.example.fractions.core

import java.math.BigInteger

/** Максимальное число цифр в каждом поле дроби. */
const val MAX_DIGITS = 9

/**
 * Ввод одной дроби с экрана: знак, целая часть, числитель и знаменатель.
 * Значение: ±(целая часть + числитель / знаменатель). Пустое поле означает «не введено».
 */
data class OperandInput(
    val negative: Boolean = false,
    val whole: String = "",
    val numerator: String = "",
    val denominator: String = "",
) {
    /** Оставляет только цифры и ограничивает каждое поле [MAX_DIGITS] знаками. */
    fun sanitized(): OperandInput = copy(
        whole = whole.digitsOnly(),
        numerator = numerator.digitsOnly(),
        denominator = denominator.digitsOnly(),
    )
}

private fun String.digitsOnly(): String = filter { it in '0'..'9' }.take(MAX_DIGITS)

sealed interface ParseResult {
    /** Данных недостаточно: дробь не введена или нет знаменателя. */
    data object Incomplete : ParseResult

    /** Знаменатель введён и равен нулю. */
    data object ZeroDenominator : ParseResult

    data class Valid(val value: Fraction) : ParseResult
}

/**
 * Превращает ввод в дробь.
 * - Если заполнена только целая часть, значение равно ей.
 * - Если введена дробная часть, знаменатель обязателен; пустой числитель считается нулём.
 */
fun OperandInput.parse(): ParseResult {
    val input = sanitized()
    val hasFraction = input.numerator.isNotEmpty() || input.denominator.isNotEmpty()
    val hasWhole = input.whole.isNotEmpty()

    val magnitude: Fraction = when {
        !hasFraction && !hasWhole -> return ParseResult.Incomplete
        !hasFraction -> Fraction.of(BigInteger(input.whole), BigInteger.ONE)
        input.denominator.isEmpty() -> return ParseResult.Incomplete
        else -> {
            val denominator = BigInteger(input.denominator)
            if (denominator.signum() == 0) return ParseResult.ZeroDenominator
            val whole = BigInteger(input.whole.ifEmpty { "0" })
            val numerator = BigInteger(input.numerator.ifEmpty { "0" })
            Fraction.of(whole * denominator + numerator, denominator)
        }
    }
    return ParseResult.Valid(if (input.negative) -magnitude else magnitude)
}

enum class Operation(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷");

    fun evaluate(a: Fraction, b: Fraction): Fraction = when (this) {
        ADD -> a + b
        SUBTRACT -> a - b
        MULTIPLY -> a * b
        DIVIDE -> a / b
    }
}

sealed interface CalcError {
    /** [operand] — номер дроби (1 или 2), у которой знаменатель равен нулю. */
    data class ZeroDenominator(val operand: Int) : CalcError

    /** Делитель равен нулю. */
    data object DivisionByZero : CalcError
}

sealed interface CalcResult {
    /** Одна из дробей ещё не введена полностью. */
    data object Incomplete : CalcResult

    data class Failure(val error: CalcError) : CalcResult

    data class Value(val fraction: Fraction) : CalcResult
}

/** Вычисляет выражение [first] [operation] [second] и возвращает результат или причину, почему его нет. */
fun calculate(first: OperandInput, operation: Operation, second: OperandInput): CalcResult {
    val a = first.parse()
    val b = second.parse()
    if (a is ParseResult.ZeroDenominator) return CalcResult.Failure(CalcError.ZeroDenominator(1))
    if (b is ParseResult.ZeroDenominator) return CalcResult.Failure(CalcError.ZeroDenominator(2))
    if (a !is ParseResult.Valid || b !is ParseResult.Valid) return CalcResult.Incomplete
    if (operation == Operation.DIVIDE && b.value.signum() == 0) {
        return CalcResult.Failure(CalcError.DivisionByZero)
    }
    return CalcResult.Value(operation.evaluate(a.value, b.value))
}
