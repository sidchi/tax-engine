package io.github.sidchi.tax.domain

import java.time.LocalDate
import java.util.Currency

/** Whether line prices already include tax. */
public enum class PricingMode {
    /**
     * Prices exclude tax and tax is added on top: the US sales-tax convention. A 100.00 line at 10% costs the
     * customer 110.00.
     */
    TAX_EXCLUSIVE,

    /**
     * Prices include tax and tax is extracted from them: the EU/UK consumer VAT convention. A 110.00 line at 10%
     * costs the customer 110.00, of which 10.00 is tax.
     */
    TAX_INCLUSIVE,
}

/**
 * Everything a provider needs to quote tax for one invoice or checkout.
 *
 * At least one of [shipTo] and [billTo] is required; [taxAddress] says which one sources the tax. Every line must be
 * priced in [currency], and line ids must be unique so tax can be reported, committed and refunded per line.
 */
public data class TaxRequest(
    /** Invoice currency; every line is priced in it. */
    public val currency: Currency,
    /** Date the tax point falls on; rates in effect on this date apply. */
    public val transactionDate: LocalDate,
    /** The lines to tax, in invoice order. */
    public val lines: List<TaxableLine>,
    /** Where goods are delivered or services consumed, when known. */
    public val shipTo: Address?,
    /** The customer's billing address, when known. */
    public val billTo: Address?,
    /** Whether line prices include tax. */
    public val pricingMode: PricingMode = PricingMode.TAX_EXCLUSIVE,
    /** How tax is rounded. */
    public val rounding: RoundingPolicy = RoundingPolicy.DEFAULT,
) {
    init {
        require(lines.isNotEmpty()) { "A tax request needs at least one line" }
        val duplicates = lines.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Line ids must be unique, but these repeat: $duplicates" }
        lines.forEach {
            require(it.unitPrice.currency == currency) {
                "Line ${it.id} is priced in ${it.unitPrice.currency}, but the request currency is $currency"
            }
        }
        require(shipTo != null || billTo != null) { "A tax request needs a ship-to or a bill-to address" }
    }

    /**
     * The address that sources tax: destination-based, so [shipTo] wins and [billTo] is the fallback for sales with
     * no delivery address, such as most digital subscriptions.
     */
    public val taxAddress: Address get() = shipTo ?: checkNotNull(billTo)
}
