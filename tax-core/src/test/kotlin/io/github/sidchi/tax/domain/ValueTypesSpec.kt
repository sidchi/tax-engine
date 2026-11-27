package io.github.sidchi.tax.domain

import io.github.sidchi.tax.SampleData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.math.RoundingMode

class ValueTypesSpec : FunSpec({
    context("TaxRate") {
        test("parses percentages exactly and normalizes trailing zeros") {
            TaxRate.ofPercent("7.25").fraction shouldBe BigDecimal("0.0725")
            TaxRate.ofPercent("7.250") shouldBe TaxRate.ofPercent("7.25")
            TaxRate.ofPercent("7.250").hashCode() shouldBe TaxRate.ofPercent("7.25").hashCode()
            TaxRate.ofFraction(BigDecimal("0.19")) shouldBe TaxRate.ofPercent("19")
        }

        test("renders as a percentage") {
            TaxRate.ofPercent("8.875").toString() shouldBe "8.875%"
            TaxRate.ofPercent("20").toString() shouldBe "20%"
            TaxRate.ZERO.toString() shouldBe "0%"
        }

        test("zero-rated is a valid rate") {
            TaxRate.ofPercent("0").isZero.shouldBeTrue()
            TaxRate.ofPercent("0.00") shouldBe TaxRate.ZERO
        }

        test("rejects rates outside 0..100%") {
            shouldThrow<IllegalArgumentException> { TaxRate.ofPercent("-1") }
            shouldThrow<IllegalArgumentException> { TaxRate.ofPercent("100.01") }
            TaxRate.ofPercent("100").fraction.compareTo(BigDecimal.ONE) shouldBe 0
        }
    }

    context("ProductTaxCode") {
        test("accepts upper-case codes with separators") {
            listOf("SAAS", "DIGITAL.EBOOK", "SW-050000", "GENERAL_GOODS").forEach {
                ProductTaxCode(it).value shouldBe it
            }
        }

        test("rejects malformed codes") {
            listOf("", "saas", "SAAS ", ".SAAS", "SAAS..X", "A".repeat(ProductTaxCode.MAX_LENGTH + 1)).forEach {
                shouldThrow<IllegalArgumentException> { ProductTaxCode(it) }
            }
        }
    }

    context("CountryCode and Address") {
        test("country codes must be ISO 3166-1 alpha-2") {
            CountryCode("DE").value shouldBe "DE"
            listOf("de", "DEU", "ZZ", "").forEach { shouldThrow<IllegalArgumentException> { CountryCode(it) } }
        }

        test("US addresses need a state and ZIP code") {
            val error = shouldThrow<IllegalArgumentException> {
                Address("1 Sample St", "Sampleville", region = null, postalCode = "98000", country = CountryCode.US)
            }
            error.message shouldContain "ZIP"
        }

        test("addresses outside the US may omit region and postal code") {
            Address("Sample Road 1", "Sample Town", null, null, SampleData.DE).region shouldBe null
        }

        test("blank fields are rejected rather than stored") {
            shouldThrow<IllegalArgumentException> { Address(" ", "City", null, null, SampleData.DE) }
            shouldThrow<IllegalArgumentException> { Address("Line", "City", " ", null, SampleData.DE) }
        }
    }

    context("Jurisdiction and LineId") {
        test("jurisdictions need a code and a name") {
            shouldThrow<IllegalArgumentException> {
                Jurisdiction(CountryCode.US, JurisdictionLevel.STATE, "", "Sample State")
            }
            shouldThrow<IllegalArgumentException> { Jurisdiction(CountryCode.US, JurisdictionLevel.STATE, "S1", " ") }
        }

        test("line ids must be non-blank and bounded") {
            shouldThrow<IllegalArgumentException> { LineId(" ") }
            shouldThrow<IllegalArgumentException> { LineId("x".repeat(LineId.MAX_LENGTH + 1)) }
        }
    }

    context("RoundingPolicy") {
        test("defaults to per-line, half-up") {
            RoundingPolicy.DEFAULT shouldBe RoundingPolicy(RoundingLevel.PER_LINE, RoundingMode.HALF_UP)
        }

        test("rejects UNNECESSARY") {
            shouldThrow<IllegalArgumentException> {
                RoundingPolicy(RoundingLevel.PER_INVOICE, RoundingMode.UNNECESSARY)
            }
        }
    }
})
