package com.folio.fractions

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Несократимая дробь; знаменатель всегда > 0. */
class Fraction private constructor(val num: BigInteger, val den: BigInteger) {

    companion object {
        fun of(n: BigInteger, d: BigInteger): Fraction {
            require(d.signum() != 0) { "Знаменатель не может быть нулём" }
            val g = n.gcd(d).let { if (it.signum() == 0) BigInteger.ONE else it }
            val s = if (d.signum() < 0) -1 else 1
            return Fraction(n.divide(g).multiply(BigInteger.valueOf(s.toLong())),
                d.divide(g).abs())
        }

        fun of(n: Long, d: Long) = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        /** Смешанное число: whole + n/d (для отрицательного whole дробная часть вычитается). */
        fun mixed(whole: BigInteger, n: BigInteger, d: BigInteger): Fraction {
            require(d.signum() != 0) { "Знаменатель не может быть нулём" }
            val frac = of(n.abs(), d.abs())
            val w = Fraction(whole, BigInteger.ONE)
            val negative = whole.signum() < 0 || (whole.signum() == 0 && (n.signum() * d.signum()) < 0)
            return if (negative) w - frac.abs() else w + frac.abs()
        }
    }

    fun abs() = Fraction(num.abs(), den)
    operator fun plus(o: Fraction) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Fraction) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Fraction) = of(num * o.num, den * o.den)
    operator fun div(o: Fraction): Fraction {
        require(o.num.signum() != 0) { "Деление на ноль" }
        return of(num * o.den, den * o.num)
    }

    /** "3/4", "-5", "0" */
    override fun toString() = if (den == BigInteger.ONE) num.toString() else "$num/$den"

    /** Смешанная форма: "1 1/2", "-2 3/4"; для правильных дробей совпадает с toString. */
    fun toMixedString(): String {
        if (den == BigInteger.ONE || num.abs() < den) return toString()
        val (q, r) = num.abs().divideAndRemainder(den)
        val sign = if (num.signum() < 0) "-" else ""
        return "$sign$q $r/$den"
    }

    fun toDecimalString(digits: Int = 8): String =
        BigDecimal(num).divide(BigDecimal(den), MathContext(digits + 10))
            .setScale(digits, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()

    override fun equals(other: Any?) = other is Fraction && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
}
