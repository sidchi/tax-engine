package io.github.sidchi.tax.calculation

import io.github.sidchi.tax.SampleData
import io.github.sidchi.tax.SampleData.eur
import io.github.sidchi.tax.SampleData.levy
import io.github.sidchi.tax.SampleData.line
import io.github.sidchi.tax.SampleData.request
import io.github.sidchi.tax.SampleData.usd
import io.github.sidchi.tax.domain.Discount
import io.github.sidchi.tax.domain.JurisdictionLevel.CITY
import io.github.sidchi.tax.domain.JurisdictionLevel.COUNTRY
import io.github.sidchi.tax.domain.JurisdictionLevel.COUNTY
import io.github.sidchi.tax.domain.JurisdictionLevel.STATE
import io.github.sidchi.tax.domain.LineId
import io.github.sidchi.tax.domain.Money
import io.github.sidchi.tax.domain.PricingMode.TAX_EXCLUSIVE
import io.github.sidchi.tax.domain.PricingMode.TAX_INCLUSIVE
import io.github.sidchi.tax.domain.ProductTaxCode
import io.github.sidchi.tax.domain.RoundingLevel.PER_INVOICE
import io.github.sidchi.tax.domain.RoundingLevel.PER_LINE
import io.github.sidchi.tax.domain.RoundingPolicy
import io.github.sidchi.tax.domain.TaxQuote
import io.github.sidchi.tax.domain.sum
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.RoundingMode

/** All rates and jurisdictions below are synthetic samples chosen to exercise the math, not real tax rates. */
class QuoteCalculatorSpec : FunSpec({
    val state = levy(STATE, "ST-1", "6.5")
    val county = levy(COUNTY, "CO-1", "0.4")
    val city = levy(CITY, "CI-1", "2.0")
    val stateOnly = levy(STATE, "ST-2", "7.25")
    val vatStandard = levy(COUNTRY, "DE", "19", SampleData.DE)
    val vatReduced = levy(COUNTRY, "DE", "7", SampleData.DE)

    context("tax-exclusive pricing") {
        test("stacks state, county and city tax on one line") {
            val quote = QuoteCalculator.calculate(
                request(listOf(line("1", "100.00"))),
                mapOf(LineId("1") to listOf(state, county, city)),
            )
            quote.lines.single().jurisdictions.map { it.tax } shouldContainExactly
                listOf(usd("6.50"), usd("0.40"), usd("2.00"))
            quote.subtotal shouldBe usd("100.00")
            quote.totalTax shouldBe usd("8.90")
            quote.grandTotal shouldBe usd("108.90")
            quote.jurisdictions.map { it.levy } shouldContainExactly listOf(state, county, city)
        }

        test("applies tax to the discounted amount") {
            val discounted = line(
                "1",
                "50.00",
                quantity = "2",
                discounts = listOf(Discount.Percentage(BigDecimal("0.10")), Discount.Fixed(usd("5.00"))),
            )
            val quote = QuoteCalculator.calculate(
                request(listOf(discounted)),
                mapOf(discounted.id to listOf(stateOnly)),
            )
            quote.subtotal shouldBe usd("85.00")
            quote.totalTax shouldBe usd("6.16") // 85.00 × 7.25% = 6.1625
        }

        test("rounds a fractional line amount before taxing it") {
            val metered = line("1", "0.333", quantity = "3") // 0.999 -> 1.00
            val quote = QuoteCalculator.calculate(request(listOf(metered)), mapOf(metered.id to listOf(stateOnly)))
            quote.subtotal shouldBe usd("1.00")
            quote.totalTax shouldBe usd("0.07")
        }

        test("uses the currency's minor unit: JPY has none") {
            val jpy = levy(COUNTRY, "JP", "10", SampleData.JP)
            val quote = QuoteCalculator.calculate(
                request(listOf(line("1", "999", currency = "JPY")), currency = "JPY", shipTo = SampleData.jpAddress),
                mapOf(LineId("1") to listOf(jpy)),
            )
            quote.totalTax shouldBe Money.of("100", "JPY") // 99.9 rounds up to a whole yen
        }
    }

    context("tax-inclusive pricing") {
        fun vatQuote(price: String, mode: RoundingMode = RoundingMode.HALF_UP) = QuoteCalculator.calculate(
            request(
                listOf(line("1", price, currency = "EUR")),
                currency = "EUR",
                pricingMode = TAX_INCLUSIVE,
                rounding = RoundingPolicy(PER_LINE, mode),
                shipTo = SampleData.deAddress,
            ),
            mapOf(LineId("1") to listOf(vatStandard)),
        )

        test("extracts tax from the price instead of adding it") {
            val quote = vatQuote("119.00")
            quote.subtotal shouldBe eur("100.00")
            quote.totalTax shouldBe eur("19.00")
            quote.grandTotal shouldBe eur("119.00")
        }

        test("never changes the customer-facing price, whatever the rounding mode") {
            val twentyPercent = levy(COUNTRY, "SAMPLE-20", "20", SampleData.DE)
            listOf(RoundingMode.HALF_UP to "1.67", RoundingMode.HALF_EVEN to "1.66").forEach { (mode, tax) ->
                val quote = QuoteCalculator.calculate(
                    request(
                        listOf(line("1", "9.99", currency = "EUR")),
                        currency = "EUR",
                        pricingMode = TAX_INCLUSIVE,
                        rounding = RoundingPolicy(PER_LINE, mode),
                        shipTo = SampleData.deAddress,
                    ),
                    mapOf(LineId("1") to listOf(twentyPercent)),
                )
                quote.totalTax shouldBe eur(tax) // 9.99 / 6 = 1.665 exactly: the mode decides the tie
                quote.grandTotal shouldBe eur("9.99")
            }
        }

        test("splits stacked rates by each jurisdiction's share of the combined rate") {
            val quote = QuoteCalculator.calculate(
                request(listOf(line("1", "108.90")), pricingMode = TAX_INCLUSIVE),
                mapOf(LineId("1") to listOf(state, county, city)),
            )
            quote.subtotal shouldBe usd("100.00")
            quote.lines.single().jurisdictions.map { it.tax } shouldContainExactly
                listOf(usd("6.50"), usd("0.40"), usd("2.00"))
        }
    }

    context("per-line vs. per-invoice rounding") {
        val lines = (1..3).map { line("$it", "0.99") }
        val rates = lines.associate { it.id to listOf(stateOnly) }

        test("per-line rounding sums rounded line tax") {
            val quote = QuoteCalculator.calculate(request(lines), rates)
            quote.lines.map { it.tax } shouldContainExactly List(3) { usd("0.07") } // 0.071775 each
            quote.totalTax shouldBe usd("0.21")
        }

        test("per-invoice rounding rounds the total once and allocates the extra cent to a line") {
            val quote = QuoteCalculator.calculate(request(lines, rounding = RoundingPolicy(PER_INVOICE)), rates)
            quote.totalTax shouldBe usd("0.22") // 0.215325 rounded once
            quote.lines.map { it.tax } shouldContainExactly listOf(usd("0.08"), usd("0.07"), usd("0.07"))
            quote.jurisdictions.single().tax shouldBe usd("0.22")
            quote.jurisdictions.single().taxableAmount shouldBe usd("2.97")
        }

        test("per-invoice rounding with tax-inclusive prices keeps every line price intact") {
            val quote = QuoteCalculator.calculate(
                request(
                    (1..3).map { line("$it", "0.99", currency = "EUR") },
                    currency = "EUR",
                    pricingMode = TAX_INCLUSIVE,
                    rounding = RoundingPolicy(PER_INVOICE),
                    shipTo = SampleData.deAddress,
                ),
                (1..3).associate { LineId("$it") to listOf(vatStandard) },
            )
            quote.totalTax shouldBe eur("0.47") // 3 × 0.158067… = 0.474201…
            quote.lines.map { it.total } shouldContainExactly List(3) { eur("0.99") }
        }
    }

    context("taxability") {
        test("an exempt line (no jurisdictions) carries no tax and no summary row") {
            val quote = QuoteCalculator.calculate(
                request(listOf(line("taxed", "10.00"), line("exempt", "10.00").copy(taxCode = ProductTaxCode("EXEMPT.SAMPLE")))),
                mapOf(LineId("taxed") to listOf(stateOnly), LineId("exempt") to emptyList()),
            )
            val exempt = quote.line(LineId("exempt")).shouldNotBeNull()
            exempt.jurisdictions.shouldBeEmpty()
            exempt.tax shouldBe usd("0")
            quote.totalTax shouldBe usd("0.73")
            quote.jurisdictions shouldHaveSize 1
        }

        test("a zero-rated line is reported with a zero amount, unlike an exempt one") {
            val zeroRated = levy(COUNTRY, "DE", "0", SampleData.DE)
            val quote = QuoteCalculator.calculate(
                request(listOf(line("1", "10.00", currency = "EUR")), currency = "EUR", shipTo = SampleData.deAddress),
                mapOf(LineId("1") to listOf(zeroRated)),
            )
            quote.jurisdictions.single().tax shouldBe eur("0")
            quote.jurisdictions.single().taxableAmount shouldBe eur("10.00")
        }

        test("standard and reduced rates in one jurisdiction are summarized separately") {
            val quote = QuoteCalculator.calculate(
                request(
                    listOf(line("app", "10.00", currency = "EUR"), line("ebook", "10.00", currency = "EUR")),
                    currency = "EUR",
                    shipTo = SampleData.deAddress,
                ),
                mapOf(LineId("app") to listOf(vatStandard), LineId("ebook") to listOf(vatReduced)),
            )
            quote.jurisdictions.map { it.levy.rate.toString() to it.tax } shouldContainExactly
                listOf("19%" to eur("1.90"), "7%" to eur("0.70"))
        }
    }

    context("validation") {
        test("every line needs a rate entry, so a dropped line can't silently go untaxed") {
            val error = shouldThrow<IllegalArgumentException> {
                QuoteCalculator.calculate(
                    request(listOf(line("1", "1.00"), line("2", "1.00"))),
                    mapOf(LineId("1") to listOf(stateOnly)),
                )
            }
            error.message shouldContain "missing [2]"
        }

        test("rates for unknown lines are rejected") {
            shouldThrow<IllegalArgumentException> {
                QuoteCalculator.calculate(
                    request(listOf(line("1", "1.00"))),
                    mapOf(LineId("1") to emptyList(), LineId("ghost") to emptyList()),
                )
            }.message shouldContain "unknown [ghost]"
        }

        test("a jurisdiction may appear only once per line") {
            shouldThrow<IllegalArgumentException> {
                QuoteCalculator.calculate(
                    request(listOf(line("1", "1.00"))),
                    mapOf(LineId("1") to listOf(stateOnly, levy(STATE, "ST-2", "1"))),
                )
            }.message shouldContain "same jurisdiction"
        }
    }

    context("invariants, for any lines, rates and policy") {
        val prices = Arb.list(Arb.bigDecimal(BigDecimal.ZERO, BigDecimal("10000")), 1..8)
        val levyCombinations = listOf(emptyList(), listOf(state), listOf(state, county), listOf(state, county, city))
        val levySets = Arb.list(Arb.element(levyCombinations), 8..8)
        val policies = Arb.element(
            listOf(PER_LINE, PER_INVOICE).flatMap { level ->
                listOf(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN)
                    .map { RoundingPolicy(level, it) }
            },
        )
        val pricingModes = Arb.element(TAX_EXCLUSIVE, TAX_INCLUSIVE)

        test("amounts are at currency precision and every total reconciles") {
            checkAll(prices, levySets, policies, pricingModes) { amounts, sets, policy, pricing ->
                val lines = amounts.mapIndexed { i, price -> line("L$i", price.toPlainString()) }
                val rates = lines.mapIndexed { i, taxable -> taxable.id to sets[i] }.toMap()
                val quote = QuoteCalculator.calculate(request(lines, pricingMode = pricing, rounding = policy), rates)

                quote.assertReconciles()
                if (pricing == TAX_INCLUSIVE) {
                    quote.grandTotal shouldBe lines.map { it.netAmount.rounded(policy.mode) }.sum(quote.currency)
                }
            }
        }

        test("tax-exclusive totals match an independent calculation at the chosen rounding level") {
            checkAll(prices, levySets, policies) { amounts, sets, policy ->
                val lines = amounts.mapIndexed { i, price -> line("L$i", price.toPlainString()) }
                val rates = lines.mapIndexed { i, taxable -> taxable.id to sets[i] }.toMap()
                val quote = QuoteCalculator.calculate(request(lines, rounding = policy), rates)

                quote.jurisdictions.forEach { summary ->
                    val exact = lines
                        .filter { summary.levy in rates.getValue(it.id) }
                        .map { it.netAmount.rounded(policy.mode) * summary.levy.rate.fraction }
                    val expected = when (policy.level) {
                        PER_LINE -> exact.map { it.rounded(policy.mode) }.sum(quote.currency)
                        PER_INVOICE -> exact.sum(quote.currency).rounded(policy.mode)
                    }
                    summary.tax shouldBe expected
                }
            }
        }
    }
})

private fun TaxQuote.assertReconciles() {
    lines.forEach { line ->
        line.taxableAmount.isWholeMinorUnits() shouldBe true
        line.jurisdictions.forEach { it.tax.isWholeMinorUnits() shouldBe true }
        line.total shouldBe line.taxableAmount + line.tax
    }
    jurisdictions.map { it.tax }.sum(currency) shouldBe totalTax
    grandTotal shouldBe subtotal + totalTax
}
