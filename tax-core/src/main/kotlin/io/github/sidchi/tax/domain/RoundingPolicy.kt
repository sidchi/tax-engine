package io.github.sidchi.tax.domain

import java.math.RoundingMode

/**
 * Where tax is rounded to the currency's minor unit.
 *
 * The two levels can differ by a few cents on the same invoice, and tax authorities and providers differ on which
 * one they expect, so it is a per-request choice rather than a constant.
 */
public enum class RoundingLevel {
    /**
     * Each line's tax is rounded per jurisdiction, and the invoice total is the sum of the rounded line amounts.
     * Line items are self-consistent on their own, which keeps partial refunds simple.
     */
    PER_LINE,

    /**
     * Unrounded tax is summed across lines per jurisdiction and rate, and only that total is rounded. The rounded
     * total is then allocated back to the lines (largest remainder) so line amounts still add up to the invoice.
     */
    PER_INVOICE,
}

/**
 * How a quote turns exact tax amounts into currency amounts: the [level] at which rounding happens and the [mode]
 * used when it does. The same [mode] is used to round line amounts (unit price × quantity − discounts) to the
 * currency's minor unit before any tax is calculated.
 */
public data class RoundingPolicy(
    /** Whether tax is rounded per line or once per invoice. */
    public val level: RoundingLevel,
    /** The rounding mode; [RoundingMode.UNNECESSARY] is rejected because tax amounts are rarely exact. */
    public val mode: RoundingMode = RoundingMode.HALF_UP,
) {
    init {
        require(mode != RoundingMode.UNNECESSARY) {
            "RoundingMode.UNNECESSARY would throw on the first fractional cent of tax; choose an explicit mode"
        }
    }

    /** Common policies. */
    public companion object {
        /** Per-line, half-up: the most common expectation for US sales tax. */
        public val DEFAULT: RoundingPolicy = RoundingPolicy(RoundingLevel.PER_LINE, RoundingMode.HALF_UP)
    }
}
