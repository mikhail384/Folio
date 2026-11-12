package com.example.fractions.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FractionFormatTest {

    @Test
    fun fractionStringKeepsImproperForm() {
        assertEquals("7/6", Fraction.of(7, 6).toFractionString())
        assertEquals("−7/6", Fraction.of(-7, 6).toFractionString())
    }

    @Test
    fun integersHaveNoDenominator() {
        assertEquals("2", Fraction.of(4, 2).toFractionString())
        assertEquals("−2", Fraction.of(-4, 2).toFractionString())
        assertEquals("0", Fraction.of(0, 9).toFractionString())
    }

    @Test
    fun mixedNumberForImproperFractions() {
        assertEquals("2 1/3", Fraction.of(7, 3).toMixedNumberString())
        assertEquals("−2 1/3", Fraction.of(-7, 3).toMixedNumberString())
        assertEquals("1 1/2", Fraction.of(6, 4).toMixedNumberString())
    }

    @Test
    fun noMixedNumberForProperFractionsAndIntegers() {
        assertNull(Fraction.of(1, 2).toMixedNumberString())
        assertNull(Fraction.of(-1, 2).toMixedNumberString())
        assertNull(Fraction.of(4, 2).toMixedNumberString())
        assertNull(Fraction.of(0, 1).toMixedNumberString())
    }

    @Test
    fun exactDecimals() {
        assertEquals(DecimalText("0,25", true), Fraction.of(1, 4).toDecimalText())
        assertEquals(DecimalText("2,5", true), Fraction.of(5, 2).toDecimalText())
        assertEquals(DecimalText("−0,125", true), Fraction.of(-1, 8).toDecimalText())
        assertEquals(DecimalText("3", true), Fraction.of(3, 1).toDecimalText())
        assertEquals(DecimalText("0", true), Fraction.of(0, 5).toDecimalText())
        assertEquals(DecimalText("0,0009765625", true), Fraction.of(1, 1024).toDecimalText())
    }

    @Test
    fun nonTerminatingDecimalsAreRounded() {
        assertEquals(DecimalText("0,333333", false), Fraction.of(1, 3).toDecimalText())
        assertEquals(DecimalText("−0,666667", false), Fraction.of(-2, 3).toDecimalText())
        assertEquals(DecimalText("1,166667", false), Fraction.of(7, 6).toDecimalText())
        assertEquals(DecimalText("0,166667", false), Fraction.of(1, 6).toDecimalText())
    }

    @Test
    fun terminatingDecimalsThatAreTooLongAreRounded() {
        // 1/4096 = 0,000244140625 — двенадцать знаков, больше лимита точного вывода
        assertEquals(DecimalText("0,000244", false), Fraction.of(1, 4096).toDecimalText())
    }

    @Test
    fun roundingCanCarryIntoIntegerPart() {
        // 2999999/3000000 = 0,99999966666... → округление даёт 1
        assertEquals(DecimalText("1", false), Fraction.of(2999999, 3000000).toDecimalText())
    }
}
