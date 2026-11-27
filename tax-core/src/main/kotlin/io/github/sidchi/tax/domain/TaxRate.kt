package io.github.sidchi.tax.domain

import java.math.BigDecimal

/**
 * A tax rate as an exact decimal fraction, e.g. `0.0725` for 7.25%.
 *
 * Rates are normalized (trailing zeros stripped) on construction, so `7.25%` and `7.250%` are the same rate and can be
 * used as map keys, which matters when a quote groups tax by jurisdiction and rate. Rates range from 0 (zero-rated
 * goods) to 1 (100%) inclusive.
 */
public class TaxRate private constructor(
    /** The rate as a fraction of the taxable amount, normalized. */
    public val fraction: BigDecimal,
) : Comparable<TaxRate> {
    /** The rate in percent, e.g. `7.25`. */
    public val percent: BigDecimal get() = fraction.movePointRight(2).stripTrailingZeros()

    /** True for a zero rate, such as a zero-rated product. */
    public val isZero: Boolean get() = fraction.signum() == 0

    override fun compareTo(other: TaxRate): Int = fraction.compareTo(other.fraction)

    override fun equals(other: Any?): Boolean = other is TaxRate && fraction == other.fraction

    override fun hashCode(): Int = fraction.hashCode()

    /** Percent notation, e.g. `7.25%`. */
    override fun toString(): String = "${percent.toPlainString()}%"

    /** Factories; every rate goes through [ofFraction] so it is validated and normalized once. */
    public companion object {
        /** The zero rate. */
        public val ZERO: TaxRate = ofFraction(BigDecimal.ZERO)

        /** A rate from a fraction between 0 and 1, e.g. `ofFraction(BigDecimal("0.0725"))`. */
        public fun ofFraction(fraction: BigDecimal): TaxRate {
            require(fraction.signum() >= 0 && fraction <= BigDecimal.ONE) {
                "Tax rate must be between 0 and 1 (0% to 100%), but was ${fraction.toPlainString()}"
            }
            return TaxRate(fraction.stripTrailingZeros())
        }

        /** A rate from a percentage string, parsed exactly, e.g. `ofPercent("7.25")`. */
        public fun ofPercent(percent: String): TaxRate = ofFraction(BigDecimal(percent).movePointLeft(2))
    }
}
