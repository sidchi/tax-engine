package io.github.sidchi.tax.domain

import java.util.Currency

/**
 * Tax owed to one jurisdiction at one rate: either on a single line ([LineTax.jurisdictions]) or summed over the
 * whole quote ([TaxQuote.jurisdictions]).
 */
public data class JurisdictionTax(
    /** The jurisdiction and the rate it levies. */
    public val levy: JurisdictionRate,
    /** The amount the rate was applied to, at currency precision. */
    public val taxableAmount: Money,
    /** The tax, at currency precision. */
    public val tax: Money,
)

/**
 * The tax result for one [TaxableLine].
 *
 * [taxableAmount] is the line's price net of tax: for tax-exclusive pricing it is the discounted line amount, for
 * tax-inclusive pricing it is that amount with the tax extracted. Either way, [total] is what the customer pays for
 * the line. A line with no [jurisdictions] is not taxable at the tax address (exempt or out of scope).
 */
public data class LineTax(
    /** The line this result is for. */
    public val lineId: LineId,
    /** Price net of tax, at currency precision. */
    public val taxableAmount: Money,
    /** Per-jurisdiction tax on this line. */
    public val jurisdictions: List<JurisdictionTax>,
) {
    /** Total tax on the line. */
    public val tax: Money = jurisdictions.map { it.tax }.sum(taxableAmount.currency)

    /** What the customer pays for the line: net price plus tax. */
    public val total: Money get() = taxableAmount + tax
}

/**
 * A calculated, uncommitted tax quote.
 *
 * Every amount is at currency precision, and the quote is internally consistent by construction: line tax adds up to
 * [totalTax], and so does the per-jurisdiction summary in [jurisdictions], whatever the [rounding] level. That
 * invariant is what lets a later partial refund reverse exactly the tax that was charged on the refunded lines.
 */
public data class TaxQuote(
    /** Currency of every amount in the quote. */
    public val currency: Currency,
    /** Whether the original prices included tax. */
    public val pricingMode: PricingMode,
    /** The rounding policy the amounts were produced with. */
    public val rounding: RoundingPolicy,
    /** Per-line results, in request order. */
    public val lines: List<LineTax>,
    /** Tax summed by jurisdiction and rate, in order of first appearance; this is what gets filed. */
    public val jurisdictions: List<JurisdictionTax>,
) {
    /** Sum of the lines' net-of-tax amounts. */
    public val subtotal: Money = lines.map { it.taxableAmount }.sum(currency)

    /** Total tax on the quote. */
    public val totalTax: Money = lines.map { it.tax }.sum(currency)

    /** What the customer pays: [subtotal] + [totalTax]. */
    public val grandTotal: Money get() = subtotal + totalTax

    init {
        val summarized = jurisdictions.map { it.tax }.sum(currency)
        require(summarized == totalTax) {
            "Jurisdiction summary ($summarized) does not match the sum of line tax ($totalTax)"
        }
        require(jurisdictions.map { it.levy }.toSet().size == jurisdictions.size) {
            "Each jurisdiction and rate must appear once in the summary"
        }
    }

    /** The result for [lineId], or null if the quote has no such line. */
    public fun line(lineId: LineId): LineTax? = lines.firstOrNull { it.lineId == lineId }
}
