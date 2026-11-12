package com.example.fractions.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class FractionTest {

    @Test
    fun normalizesToLowestTerms() {
        assertEquals("1/2", Fraction.of(2, 4).toString())
        assertEquals("1/2", Fraction.of(-3, -6).toString())
    }

    @Test
    fun keepsSignInNumerator() {
        assertEquals("-1/2", Fraction.of(1, -2).toString())
        assertEquals("-1/2", Fraction.of(-1, 2).toString())
    }

    @Test
    fun zeroIsZeroOverOne() {
        val zero = Fraction.of(0, -7)
        assertEquals("0/1", zero.toString())
        assertTrue(zero.isInteger)
        assertEquals(0, zero.signum())
    }

    @Test
    fun zeroDenominatorIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Fraction.of(1, 0) }
    }

    @Test
    fun isIntegerOnlyForWholeValues() {
        assertTrue(Fraction.of(4, 2).isInteger)
        assertFalse(Fraction.of(3, 2).isInteger)
    }

    @Test
    fun adds() {
        assertEquals(Fraction.of(5, 6), Fraction.of(1, 2) + Fraction.of(1, 3))
    }

    @Test
    fun subtractsWithNegativeResult() {
        assertEquals(Fraction.of(-1, 6), Fraction.of(1, 3) - Fraction.of(1, 2))
        assertEquals(Fraction.of(1, 6), Fraction.of(1, 2) - Fraction.of(1, 3))
    }

    @Test
    fun multiplies() {
        assertEquals(Fraction.of(1, 2), Fraction.of(2, 3) * Fraction.of(3, 4))
        assertEquals(Fraction.of(2, 3), Fraction.of(-1, 2) * Fraction.of(-4, 3))
    }

    @Test
    fun divides() {
        assertEquals(Fraction.of(2, 1), Fraction.of(1, 2) / Fraction.of(1, 4))
        assertEquals(Fraction.of(-2, 1), Fraction.of(-3, 4) / Fraction.of(3, 8))
    }

    @Test
    fun dividingByZeroThrows() {
        assertThrows(ArithmeticException::class.java) { Fraction.of(1, 2) / Fraction.of(0, 5) }
    }

    @Test
    fun unaryMinusNegates() {
        assertEquals(Fraction.of(-2, 3), -Fraction.of(2, 3))
        assertEquals(Fraction.of(0, 1), -Fraction.of(0, 1))
    }

    @Test
    fun equalValuesAreEqual() {
        assertEquals(Fraction.of(2, 4), Fraction.of(1, 2))
        assertEquals(Fraction.of(2, 4).hashCode(), Fraction.of(1, 2).hashCode())
    }

    @Test
    fun largeValuesDoNotOverflow() {
        val big = Fraction.of(999_999_999L, 1_000_000_007L)
        val squared = big * big
        assertEquals(BigInteger.valueOf(999_999_999L).pow(2), squared.numerator)
        assertEquals(BigInteger.valueOf(1_000_000_007L).pow(2), squared.denominator)
    }
}
