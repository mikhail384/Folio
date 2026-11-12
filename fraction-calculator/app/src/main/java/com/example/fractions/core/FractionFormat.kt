package com.example.fractions.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Десятичная запись. [isExact] = false означает, что число округлено. */
data class DecimalText(val text: String, val isExact: Boolean)

private const val MINUS = '−'

/** Неправильная дробь или целое число: "7/6", "−7/6", "2". */
fun Fraction.toFractionString(): String {
    val sign = if (signum() < 0) MINUS.toString() else ""
    val absolute = numerator.abs()
    return if (isInteger) "$sign$absolute" else "$sign$absolute/$denominator"
}

/** Смешанное число: "2 1/3", "−2 1/3". Возвращает null, если |значение| < 1 или число целое. */
fun Fraction.toMixedNumberString(): String? {
    if (isInteger) return null
    val (whole, rest) = numerator.abs().divideAndRemainder(denominator)
    if (whole.signum() == 0) return null
    val sign = if (signum() < 0) MINUS.toString() else ""
    return "$sign$whole $rest/$denominator"
}

/**
 * Десятичная запись через запятую.
 *
 * Дробь, которая записывается конечным десятичным числом (знаменатель вида 2^a·5^b),
 * выводится точно, если в ней не больше [maxExactDigits] знаков после запятой.
 * Остальные значения округляются до [approximateDigits] знаков (ROUND_HALF_UP).
 */
fun Fraction.toDecimalText(approximateDigits: Int = 6, maxExactDigits: Int = 10): DecimalText {
    val num = BigDecimal(numerator)
    val den = BigDecimal(denominator)
    val terminating: BigDecimal? = try {
        num.divide(den).stripTrailingZeros()
    } catch (e: ArithmeticException) {
        null // бесконечная периодическая запись, например 1/3
    }
    return if (terminating != null && terminating.scale() <= maxExactDigits) {
        DecimalText(terminating.toDisplayText(), isExact = true)
    } else {
        val rounded = num.divide(den, approximateDigits, RoundingMode.HALF_UP).stripTrailingZeros()
        DecimalText(rounded.toDisplayText(), isExact = false)
    }
}

private fun BigDecimal.toDisplayText(): String =
    toPlainString().replace('-', MINUS).replace('.', ',')
