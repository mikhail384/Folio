package com.folio.fractions

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class FractionTest {
    private fun f(n: Long, d: Long) = Fraction.of(n, d)

    @Test fun reduces() = assertEquals("1/2", f(2, 4).toString())
    @Test fun normalizesSign() = assertEquals("-1/2", f(1, -2).toString())
    @Test fun add() = assertEquals("5/6", (f(1, 2) + f(1, 3)).toString())
    @Test fun subtract() = assertEquals("-1/6", (f(1, 3) - f(1, 2)).toString())
    @Test fun multiply() = assertEquals("1/2", (f(2, 3) * f(3, 4)).toString())
    @Test fun divide() = assertEquals("3/2", (f(3, 4) / f(1, 2)).toString())
    @Test fun integerResult() = assertEquals("2", (f(1, 2) + f(3, 2)).toString())
    @Test fun mixedString() = assertEquals("-2 3/4", f(-11, 4).toMixedString())
    @Test fun decimal() = assertEquals("0.33333333", f(1, 3).toDecimalString())
    @Test fun mixedInput() = assertEquals(
        "7/2", Fraction.mixed(BigInteger.valueOf(3), BigInteger.ONE, BigInteger.valueOf(2)).toString())
    @Test fun negativeMixedInput() = assertEquals(
        "-7/2", Fraction.mixed(BigInteger.valueOf(-3), BigInteger.ONE, BigInteger.valueOf(2)).toString())
    @Test(expected = IllegalArgumentException::class) fun zeroDen() { f(1, 0) }
    @Test(expected = IllegalArgumentException::class) fun divZero() { f(1, 2) / f(0, 1) }
}
