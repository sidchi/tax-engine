package io.github.sidchi.tax.domain

import io.github.sidchi.tax.SampleData.usd
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

class MoneySpec : FunSpec({
    test("equality is numeric, so scale does not matter") {
        Money.of("1.0", "USD") shouldBe Money.of("1.00", "USD")
        Money.of("1.0", "USD").hashCode() shouldBe Money.of("1.00", "USD").hashCode()
        Money.of("0", "USD") shouldBe Money.zero(Currency.getInstance("USD"))
    }

    test("the same amount in different currencies is not equal") {
        Money.of("1.00", "USD") shouldNotBe Money.of("1.00", "EUR")
    }

    test("arithmetic is exact and does not round") {
        (usd("19.99") * BigDecimal("1.5")).amount shouldBe BigDecimal("29.985")
        (usd("0.10") + usd("0.20")) shouldBe usd("0.30")
        (usd("1.00") - usd("0.01")) shouldBe usd("0.99")
    }

    test("mixing currencies is rejected") {
        val error = shouldThrow<IllegalArgumentException> { usd("1.00") + Money.of("1.00", "EUR") }
        error.message shouldContain "Currency mismatch"
        shouldThrow<IllegalArgumentException> { usd("1.00") > Money.of("1.00", "EUR") }
    }

    test("rounding uses the currency's minor unit and the requested mode") {
        Money.of("2.675", "USD").rounded(RoundingMode.HALF_UP).amount shouldBe BigDecimal("2.68")
        Money.of("2.665", "USD").rounded(RoundingMode.HALF_EVEN).amount shouldBe BigDecimal("2.66")
        Money.of("999.5", "JPY").rounded(RoundingMode.HALF_UP).amount shouldBe BigDecimal("1000")
        Money.of("1.2345", "KWD").rounded(RoundingMode.HALF_UP).amount shouldBe BigDecimal("1.235")
    }

    test("reports whether an amount is already at minor-unit precision") {
        usd("1.50").isWholeMinorUnits().shouldBeTrue()
        usd("1.5000").isWholeMinorUnits().shouldBeTrue()
        usd("1.505").isWholeMinorUnits().shouldBeFalse()
        Money.of("100.5", "JPY").isWholeMinorUnits().shouldBeFalse()
    }

    test("currencies without a minor unit cannot be invoiced") {
        val error = shouldThrow<IllegalArgumentException> { Money.of("1", "XAU") }
        error.message shouldContain "no minor unit"
    }

    test("summing an empty list gives zero in the requested currency") {
        emptyList<Money>().sum(Currency.getInstance("EUR")) shouldBe Money.of("0", "EUR")
        listOf(usd("1.10"), usd("2.20")).sum(Currency.getInstance("USD")) shouldBe usd("3.30")
    }

    test("toString shows plain notation and the currency code") {
        Money(BigDecimal("1E+3"), Currency.getInstance("USD")).toString() shouldBe "1000 USD"
    }

    test("rounding is idempotent and lands on a whole minor unit for every mode") {
        val modes = Arb.element(RoundingMode.entries.filter { it != RoundingMode.UNNECESSARY })
        val amounts = Arb.bigDecimal(BigDecimal("-100000"), BigDecimal("100000"))
        checkAll(amounts, modes) { amount, mode ->
            val once = Money(amount, Currency.getInstance("USD")).rounded(mode)
            once.isWholeMinorUnits().shouldBeTrue()
            once.rounded(mode) shouldBe once
            once.amount.scale() shouldBe 2
        }
    }
})
