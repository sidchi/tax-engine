package io.github.sidchi.tax.domain

import io.github.sidchi.tax.SampleData
import io.github.sidchi.tax.SampleData.line
import io.github.sidchi.tax.SampleData.usd
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class TaxRequestSpec : FunSpec({
    context("TaxableLine") {
        test("gross amount is unit price times quantity, exact") {
            line("1", "19.99", quantity = "1.5").grossAmount.amount shouldBe BigDecimal("29.985")
        }

        test("discounts are each computed against the gross amount") {
            val discounted = line(
                "1",
                "50.00",
                quantity = "2",
                discounts = listOf(Discount.Percentage(BigDecimal("0.10")), Discount.Fixed(usd("5.00"))),
            )
            discounted.grossAmount shouldBe usd("100.00")
            discounted.discountAmount shouldBe usd("15.00")
            discounted.netAmount shouldBe usd("85.00")
        }

        test("discount order does not change the result") {
            val percent = Discount.Percentage(BigDecimal("0.25"))
            val fixed = Discount.Fixed(usd("3.00"))
            line("1", "40.00", discounts = listOf(percent, fixed)).netAmount shouldBe
                line("1", "40.00", discounts = listOf(fixed, percent)).netAmount
        }

        test("a 100% discount leaves a zero net amount") {
            line("1", "9.99", discounts = listOf(Discount.Percentage(BigDecimal.ONE))).netAmount shouldBe usd("0")
        }

        test("discounts larger than the gross amount are rejected") {
            val error = shouldThrow<IllegalArgumentException> {
                line("1", "10.00", discounts = listOf(Discount.Fixed(usd("10.01"))))
            }
            error.message shouldContain "exceed the gross amount"
        }

        test("a fixed discount must be in the line currency") {
            val error = shouldThrow<IllegalArgumentException> {
                line("1", "10.00", discounts = listOf(Discount.Fixed(SampleData.eur("1.00"))))
            }
            error.message shouldContain "not in the line currency"
        }

        test("quantity must be positive and price non-negative") {
            shouldThrow<IllegalArgumentException> { line("1", "10.00", quantity = "0") }
            shouldThrow<IllegalArgumentException> { line("1", "-0.01") }
            shouldThrow<IllegalArgumentException> { Discount.Fixed(usd("-1")) }
            shouldThrow<IllegalArgumentException> { Discount.Percentage(BigDecimal("1.01")) }
        }
    }

    context("TaxRequest") {
        test("ship-to sources tax when present, bill-to otherwise") {
            val billTo = SampleData.deAddress.copy(city = "Billingstadt")
            SampleData.request(listOf(line("1", "1.00"))).copy(billTo = billTo).taxAddress shouldBe
                SampleData.usAddress
            SampleData.request(listOf(line("1", "1.00"))).copy(shipTo = null, billTo = billTo).taxAddress shouldBe
                billTo
        }

        test("needs at least one address") {
            val error = shouldThrow<IllegalArgumentException> {
                TaxRequest(Currency.getInstance("USD"), LocalDate.of(2026, 1, 1), listOf(line("1", "1")), null, null)
            }
            error.message shouldContain "address"
        }

        test("needs at least one line") {
            shouldThrow<IllegalArgumentException> { SampleData.request(emptyList()) }
        }

        test("line ids must be unique") {
            val error = shouldThrow<IllegalArgumentException> {
                SampleData.request(listOf(line("a", "1.00"), line("a", "2.00")))
            }
            error.message shouldContain "unique"
        }

        test("every line must be in the request currency") {
            val error = shouldThrow<IllegalArgumentException> {
                SampleData.request(listOf(line("1", "1.00", currency = "EUR")), currency = "USD")
            }
            error.message shouldContain "request currency"
        }
    }
})
