package io.github.sidchi.tax.calculation

import io.github.sidchi.tax.domain.JurisdictionRate
import io.github.sidchi.tax.domain.JurisdictionTax
import io.github.sidchi.tax.domain.LineId
import io.github.sidchi.tax.domain.LineTax
import io.github.sidchi.tax.domain.Money
import io.github.sidchi.tax.domain.PricingMode
import io.github.sidchi.tax.domain.RoundingLevel
import io.github.sidchi.tax.domain.TaxQuote
import io.github.sidchi.tax.domain.TaxRequest
import io.github.sidchi.tax.domain.sum
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Turns a [TaxRequest] plus the rates that apply to each line into a [TaxQuote].
 *
 * This is the money math every provider shares; providers only decide *which* jurisdictions and rates apply. Keeping
 * it in one place means rounding, tax-inclusive extraction and per-invoice allocation behave identically whether the
 * rates came from the in-house rule table, a remote provider, or the cached-rate fallback.
 *
 * The calculation, in order:
 * 1. Each line's net amount (gross − discounts) is rounded to the currency's minor unit with the policy's mode.
 *    That is the price the customer sees on the invoice line.
 * 2. Exact tax per line and jurisdiction: `amount × rate` for tax-exclusive pricing, or
 *    `amount × rate / (1 + combined rate)` for tax-inclusive pricing. Rates stack additively; no jurisdiction
 *    taxes another jurisdiction's tax.
 * 3. Rounding: [RoundingLevel.PER_LINE] rounds every line/jurisdiction amount; [RoundingLevel.PER_INVOICE] rounds
 *    each jurisdiction-and-rate total once and allocates it back to lines with the largest-remainder method.
 * 4. Net-of-tax amounts: the line amount itself when tax-exclusive; the line amount minus its rounded tax when
 *    tax-inclusive, so a tax-inclusive price is never changed by rounding.
 */
public object QuoteCalculator {
    /** Scale for the one inexact step, the tax-inclusive division; far below any currency's minor unit. */
    private const val EXTRACTION_SCALE = 12

    /**
     * Calculates the quote for [request].
     *
     * [rates] must have an entry for every line of the request and nothing else. An empty list means the line is
     * not taxable at the tax address (exempt or out of scope). Requiring the entry, instead of defaulting a missing
     * line to no tax, turns a provider that silently drops a line into an error rather than an undercharge.
     */
    public fun calculate(request: TaxRequest, rates: Map<LineId, List<JurisdictionRate>>): TaxQuote {
        validate(request, rates)
        val mode = request.rounding.mode
        val drafts = request.lines.map { line ->
            val amount = line.netAmount.rounded(mode)
            val levies = rates.getValue(line.id)
            val combined = levies.fold(BigDecimal.ZERO) { acc, levy -> acc + levy.rate.fraction }
            LineDraft(line.id, amount, levies.map { it to exactTax(amount, it, combined, request.pricingMode) })
        }
        val roundedTax = when (request.rounding.level) {
            RoundingLevel.PER_LINE -> drafts.map { draft -> draft.exactTax.map { (_, tax) -> tax.rounded(mode) } }
            RoundingLevel.PER_INVOICE -> roundPerInvoice(drafts, mode)
        }
        val lines = drafts.zip(roundedTax) { draft, taxes -> draft.toLineTax(taxes, request.pricingMode) }
        return TaxQuote(
            currency = request.currency,
            pricingMode = request.pricingMode,
            rounding = request.rounding,
            lines = lines,
            jurisdictions = summarize(lines, request),
        )
    }

    private fun validate(request: TaxRequest, rates: Map<LineId, List<JurisdictionRate>>) {
        val lineIds = request.lines.map { it.id }.toSet()
        require(rates.keys == lineIds) {
            "Rates must cover exactly the request's lines; missing ${lineIds - rates.keys}, " +
                "unknown ${rates.keys - lineIds}"
        }
        rates.forEach { (lineId, levies) ->
            require(levies.map { it.jurisdiction }.toSet().size == levies.size) {
                "Line $lineId lists the same jurisdiction more than once: $levies"
            }
        }
    }

    private fun exactTax(amount: Money, levy: JurisdictionRate, combined: BigDecimal, pricing: PricingMode): Money =
        when (pricing) {
            PricingMode.TAX_EXCLUSIVE -> amount * levy.rate.fraction
            PricingMode.TAX_INCLUSIVE -> Money(
                (amount.amount * levy.rate.fraction)
                    .divide(BigDecimal.ONE + combined, EXTRACTION_SCALE, RoundingMode.HALF_EVEN),
                amount.currency,
            )
        }

    /**
     * Rounds each jurisdiction-and-rate total once, then allocates it back to the lines that contributed to it so
     * that line tax still adds up to the invoice tax.
     */
    private fun roundPerInvoice(drafts: List<LineDraft>, mode: RoundingMode): List<List<Money>> {
        val result = drafts.map { draft -> draft.exactTax.map { (_, tax) -> tax }.toMutableList() }
        val positions = drafts.flatMapIndexed { lineIndex, draft ->
            draft.exactTax.mapIndexed { levyIndex, (levy, _) -> levy to (lineIndex to levyIndex) }
        }.groupBy({ it.first }, { it.second })
        positions.values.forEach { cells ->
            val exact = cells.map { (line, levy) -> result[line][levy] }
            val currency = exact.first().currency
            val total = exact.sum(currency).rounded(mode)
            val allocated = allocateRounded(exact.map { it.amount }, total.amount, total.minorUnits)
            cells.zip(allocated).forEach { (cell, amount) -> result[cell.first][cell.second] = Money(amount, currency) }
        }
        return result
    }

    private fun summarize(lines: List<LineTax>, request: TaxRequest): List<JurisdictionTax> =
        lines.flatMap { it.jurisdictions }
            .groupBy { it.levy }
            .map { (levy, taxes) ->
                JurisdictionTax(
                    levy = levy,
                    taxableAmount = taxes.map { it.taxableAmount }.sum(request.currency),
                    tax = taxes.map { it.tax }.sum(request.currency),
                )
            }

    /** A line with its rounded amount and exact, not yet rounded, tax per jurisdiction. */
    private class LineDraft(
        val lineId: LineId,
        val amount: Money,
        val exactTax: List<Pair<JurisdictionRate, Money>>,
    ) {
        fun toLineTax(roundedTax: List<Money>, pricing: PricingMode): LineTax {
            val taxable = when (pricing) {
                PricingMode.TAX_EXCLUSIVE -> amount
                PricingMode.TAX_INCLUSIVE -> amount - roundedTax.sum(amount.currency)
            }
            val jurisdictions = exactTax.zip(roundedTax) { (levy, _), tax -> JurisdictionTax(levy, taxable, tax) }
            return LineTax(lineId, taxable, jurisdictions)
        }
    }
}
