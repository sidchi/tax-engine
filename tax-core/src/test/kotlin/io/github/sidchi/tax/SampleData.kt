package io.github.sidchi.tax

import io.github.sidchi.tax.domain.Address
import io.github.sidchi.tax.domain.CountryCode
import io.github.sidchi.tax.domain.Discount
import io.github.sidchi.tax.domain.Jurisdiction
import io.github.sidchi.tax.domain.JurisdictionLevel
import io.github.sidchi.tax.domain.JurisdictionRate
import io.github.sidchi.tax.domain.LineId
import io.github.sidchi.tax.domain.Money
import io.github.sidchi.tax.domain.PricingMode
import io.github.sidchi.tax.domain.ProductTaxCode
import io.github.sidchi.tax.domain.RoundingPolicy
import io.github.sidchi.tax.domain.TaxRate
import io.github.sidchi.tax.domain.TaxRequest
import io.github.sidchi.tax.domain.TaxableLine
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * Synthetic fixtures for tests. Every address, jurisdiction and rate here is a made-up sample, not real tax data.
 */
object SampleData {
    val US = CountryCode.US
    val DE = CountryCode("DE")
    val JP = CountryCode("JP")

    val usAddress = Address("100 Sample Street", "Sampleville", "WA", "98000", US)
    val deAddress = Address("Beispielweg 1", "Musterstadt", null, "10115", DE)
    val jpAddress = Address("1-1 Sample", "Sample City", null, "100-0001", JP)

    fun levy(level: JurisdictionLevel, code: String, percent: String, country: CountryCode = US) =
        JurisdictionRate(Jurisdiction(country, level, code, "Sample $code"), TaxRate.ofPercent(percent))

    fun line(
        id: String,
        price: String,
        currency: String = "USD",
        quantity: String = "1",
        discounts: List<Discount> = emptyList(),
        taxCode: String = "SAAS",
    ) = TaxableLine(LineId(id), ProductTaxCode(taxCode), Money.of(price, currency), BigDecimal(quantity), discounts)

    fun request(
        lines: List<TaxableLine>,
        currency: String = "USD",
        pricingMode: PricingMode = PricingMode.TAX_EXCLUSIVE,
        rounding: RoundingPolicy = RoundingPolicy.DEFAULT,
        shipTo: Address = usAddress,
    ) = TaxRequest(
        currency = Currency.getInstance(currency),
        transactionDate = LocalDate.of(2026, 1, 15),
        lines = lines,
        shipTo = shipTo,
        billTo = null,
        pricingMode = pricingMode,
        rounding = rounding,
    )

    fun usd(amount: String) = Money.of(amount, "USD")

    fun eur(amount: String) = Money.of(amount, "EUR")
}
