package com.example.fractions.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculationTest {

    private fun input(
        negative: Boolean = false,
        whole: String = "",
        numerator: String = "",
        denominator: String = "",
    ) = OperandInput(negative, whole, numerator, denominator)

    @Test
    fun emptyInputIsIncomplete() {
        assertEquals(ParseResult.Incomplete, input().parse())
    }

    @Test
    fun numeratorWithoutDenominatorIsIncomplete() {
        assertEquals(ParseResult.Incomplete, input(numerator = "3").parse())
    }

    @Test
    fun parsesPlainFraction() {
        assertEquals(
            ParseResult.Valid(Fraction.of(1, 2)),
            input(numerator = "1", denominator = "2").parse(),
        )
    }

    @Test
    fun wholePartIsAddedToFraction() {
        assertEquals(
            ParseResult.Valid(Fraction.of(7, 3)),
            input(whole = "2", numerator = "1", denominator = "3").parse(),
        )
    }

    @Test
    fun negativeSignAppliesToMixedNumber() {
        assertEquals(
            ParseResult.Valid(Fraction.of(-7, 3)),
            input(negative = true, whole = "2", numerator = "1", denominator = "3").parse(),
        )
    }

    @Test
    fun wholeNumberOnly() {
        assertEquals(ParseResult.Valid(Fraction.of(5, 1)), input(whole = "5").parse())
        assertEquals(ParseResult.Valid(Fraction.of(-5, 1)), input(negative = true, whole = "5").parse())
    }

    @Test
    fun emptyNumeratorMeansZero() {
        assertEquals(ParseResult.Valid(Fraction.of(0, 1)), input(denominator = "4").parse())
    }

    @Test
    fun zeroDenominatorIsDetected() {
        assertEquals(ParseResult.ZeroDenominator, input(numerator = "1", denominator = "0").parse())
        assertEquals(ParseResult.ZeroDenominator, input(denominator = "0").parse())
    }

    @Test
    fun sanitizeKeepsOnlyDigitsAndLimitsLength() {
        assertEquals("123456789", OperandInput(numerator = "a1b2 3-4567890").sanitized().numerator)
        assertEquals("", OperandInput(denominator = "xyz").sanitized().denominator)
    }

    @Test
    fun addsTwoFractions() {
        val result = calculate(
            input(numerator = "1", denominator = "2"),
            Operation.ADD,
            input(numerator = "1", denominator = "3"),
        )
        assertEquals(CalcResult.Value(Fraction.of(5, 6)), result)
    }

    @Test
    fun mixedNumberPlusFraction() {
        // 1 1/2 + 2/3 = 13/6
        val result = calculate(
            input(whole = "1", numerator = "1", denominator = "2"),
            Operation.ADD,
            input(numerator = "2", denominator = "3"),
        )
        assertEquals(CalcResult.Value(Fraction.of(13, 6)), result)
    }

    @Test
    fun negativeOperands() {
        // −1/2 × −4/3 = 2/3
        val result = calculate(
            input(negative = true, numerator = "1", denominator = "2"),
            Operation.MULTIPLY,
            input(negative = true, numerator = "4", denominator = "3"),
        )
        assertEquals(CalcResult.Value(Fraction.of(2, 3)), result)
    }

    @Test
    fun subtractionCanBeNegative() {
        val result = calculate(
            input(numerator = "1", denominator = "3"),
            Operation.SUBTRACT,
            input(numerator = "1", denominator = "2"),
        )
        assertEquals(CalcResult.Value(Fraction.of(-1, 6)), result)
    }

    @Test
    fun incompleteSecondOperandGivesIncomplete() {
        val result = calculate(input(numerator = "1", denominator = "2"), Operation.MULTIPLY, input())
        assertEquals(CalcResult.Incomplete, result)
    }

    @Test
    fun zeroDenominatorInFirstOperandIsReported() {
        val result = calculate(
            input(numerator = "1", denominator = "0"),
            Operation.ADD,
            input(numerator = "1", denominator = "2"),
        )
        assertEquals(CalcResult.Failure(CalcError.ZeroDenominator(1)), result)
    }

    @Test
    fun zeroDenominatorInSecondOperandIsReported() {
        val result = calculate(
            input(numerator = "1", denominator = "2"),
            Operation.ADD,
            input(numerator = "1", denominator = "0"),
        )
        assertEquals(CalcResult.Failure(CalcError.ZeroDenominator(2)), result)
    }

    @Test
    fun divisionByZeroIsReported() {
        val result = calculate(
            input(numerator = "1", denominator = "2"),
            Operation.DIVIDE,
            input(numerator = "0", denominator = "5"),
        )
        assertEquals(CalcResult.Failure(CalcError.DivisionByZero), result)
    }

    @Test
    fun zeroSecondOperandIsFineForOtherOperations() {
        val result = calculate(
            input(numerator = "1", denominator = "2"),
            Operation.ADD,
            input(numerator = "0", denominator = "5"),
        )
        assertEquals(CalcResult.Value(Fraction.of(1, 2)), result)
    }

    @Test
    fun divisionOfFractions() {
        // 1/2 ÷ 1/4 = 2
        val result = calculate(
            input(numerator = "1", denominator = "2"),
            Operation.DIVIDE,
            input(numerator = "1", denominator = "4"),
        )
        assertEquals(CalcResult.Value(Fraction.of(2, 1)), result)
    }
}
