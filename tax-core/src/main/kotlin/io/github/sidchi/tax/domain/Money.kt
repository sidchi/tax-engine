package io.github.sidchi.tax.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * An amount in a single ISO 4217 currency, backed by [BigDecimal].
 *
 * Money is deliberately **not** rounded on construction or arithmetic: `19.99 × 1.5` stays `29.985` until
 * someone decides how to round it. Tax engines get cents wrong precisely when rounding happens implicitly, so the
 * only way to get a currency-precision amount is [rounded], which takes the [RoundingMode] as an explicit argument.
 *
 * Equality is numeric (`1.0 USD == 1.00 USD`), unlike [BigDecimal.equals], so amounts computed along different paths
 * compare equal when they are the same value. Mixing currencies in arithmetic or comparison is a programming error
 * and throws [IllegalArgumentException]; conversion is out of scope for a tax engine, which always calculates in the
 * invoice currency.
 */
public class Money(
    /** The exact, possibly unrounded amount. */
    public val amount: BigDecimal,
    /** The currency; must have a minor unit (so precious-metal and fund codes such as `XAU` are rejected). */
    public val currency: Currency,
) : Comparable<Money> {
    init {
        require(currency.defaultFractionDigits >= 0) {
            "Currency ${currency.currencyCode} has no minor unit and cannot be invoiced"
        }
    }

    /** Digits after the decimal point in this currency's minor unit: 2 for USD and EUR, 0 for JPY, 3 for KWD. */
    public val minorUnits: Int get() = currency.defaultFractionDigits

    /** True when the amount is exactly zero, whatever its scale. */
    public val isZero: Boolean get() = amount.signum() == 0

    /** True when the amount is below zero. */
    public val isNegative: Boolean get() = amount.signum() < 0

    /** Exact sum; both amounts must share a currency. */
    public operator fun plus(other: Money): Money = Money(amount + sameCurrency(other).amount, currency)

    /** Exact difference; both amounts must share a currency. */
    public operator fun minus(other: Money): Money = Money(amount - sameCurrency(other).amount, currency)

    /** Exact product, e.g. unit price × quantity or base × rate. The result is not rounded. */
    public operator fun times(factor: BigDecimal): Money = Money(amount.multiply(factor), currency)

    /** This amount at exactly the currency's minor-unit scale, rounded with [mode]. */
    public fun rounded(mode: RoundingMode): Money = Money(amount.setScale(minorUnits, mode), currency)

    /** True when the amount is already representable in the currency's minor unit (e.g. no fractional cents). */
    public fun isWholeMinorUnits(): Boolean = amount.stripTrailingZeros().scale() <= minorUnits

    override fun compareTo(other: Money): Int = amount.compareTo(sameCurrency(other).amount)

    override fun equals(other: Any?): Boolean =
        other is Money && currency == other.currency && amount.compareTo(other.amount) == 0

    override fun hashCode(): Int = HASH_PRIME * currency.hashCode() + amount.stripTrailingZeros().hashCode()

    /** Plain notation followed by the currency code, e.g. `29.985 USD`. */
    override fun toString(): String = "${amount.toPlainString()} ${currency.currencyCode}"

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) {
            "Currency mismatch: ${currency.currencyCode} vs ${other.currency.currencyCode}"
        }
        return other
    }

    /** Factories. */
    public companion object {
        private const val HASH_PRIME = 31

        /** Parses [amount] exactly (no binary floating point), e.g. `Money.of("19.99", "USD")`. */
        public fun of(amount: String, currencyCode: String): Money =
            Money(BigDecimal(amount), Currency.getInstance(currencyCode))

        /** Zero in [currency], at the currency's minor-unit scale. */
        public fun zero(currency: Currency): Money =
            Money(BigDecimal.ZERO.setScale(currency.defaultFractionDigits.coerceAtLeast(0)), currency)
    }
}

/** Exact sum of these amounts; [currency] is required so that an empty list still has a well-defined result. */
public fun Iterable<Money>.sum(currency: Currency): Money = fold(Money.zero(currency)) { acc, money -> acc + money }
