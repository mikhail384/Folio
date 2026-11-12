package com.example.fractions.core

import java.math.BigInteger

/**
 * Рациональное число, всегда хранящееся как несократимая дробь [numerator]/[denominator].
 * Знаменатель строго положителен, знак числа находится в числителе.
 *
 * Создавайте значения через [Fraction.of]: конструктор закрыт, чтобы нельзя было
 * получить ненормализованную дробь.
 */
class Fraction private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    val isInteger: Boolean
        get() = denominator == BigInteger.ONE

    operator fun plus(other: Fraction): Fraction =
        of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    operator fun minus(other: Fraction): Fraction =
        of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)

    operator fun times(other: Fraction): Fraction =
        of(numerator * other.numerator, denominator * other.denominator)

    /** Бросает [ArithmeticException], если делитель [other] равен нулю. */
    operator fun div(other: Fraction): Fraction {
        if (other.numerator.signum() == 0) throw ArithmeticException("Деление на ноль")
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Fraction = Fraction(-numerator, denominator)

    fun signum(): Int = numerator.signum()

    override fun equals(other: Any?): Boolean =
        other is Fraction && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = "$numerator/$denominator"

    companion object {
        /** Создаёт нормализованную дробь. Бросает [IllegalArgumentException], если знаменатель равен нулю. */
        fun of(numerator: BigInteger, denominator: BigInteger): Fraction {
            require(denominator.signum() != 0) { "Знаменатель не может быть нулём" }
            val gcd = numerator.gcd(denominator)
            val sign = if (denominator.signum() < 0) BigInteger.ONE.negate() else BigInteger.ONE
            return Fraction(numerator / gcd * sign, denominator / gcd * sign)
        }

        fun of(numerator: Long, denominator: Long): Fraction =
            of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))
    }
}
