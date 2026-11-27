package io.github.sidchi.tax.calculation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.math.RoundingMode

class AllocationSpec : FunSpec({
    fun decimals(vararg values: String) = values.map(::BigDecimal)

    test("leftover cents go to the largest remainders") {
        allocateRounded(decimals("0.071775", "0.071775", "0.071775"), BigDecimal("0.22"), 2) shouldContainExactly
            decimals("0.08", "0.07", "0.07")
        allocateRounded(decimals("1.001", "1.009", "1.005"), BigDecimal("3.02"), 2) shouldContainExactly
            decimals("1.00", "1.01", "1.01")
    }

    test("ties go to the earlier value, so results are deterministic") {
        allocateRounded(decimals("0.335", "0.335"), BigDecimal("0.67"), 2) shouldContainExactly
            decimals("0.34", "0.33")
    }

    test("works for zero-decimal currencies") {
        allocateRounded(decimals("33.3", "33.3", "33.4"), BigDecimal("100"), 0) shouldContainExactly
            decimals("33", "33", "34")
    }

    test("rejects a total that is not a rounding of the sum") {
        shouldThrow<IllegalArgumentException> { allocateRounded(decimals("1.00"), BigDecimal("2.00"), 2) }
        shouldThrow<IllegalArgumentException> { allocateRounded(decimals("1.00", "-0.50"), BigDecimal("0.50"), 2) }
    }

    test("allocations always add up to the rounded total and stay within one cent of each value") {
        val modes = Arb.element(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.UP, RoundingMode.DOWN)
        val values = Arb.list(Arb.bigDecimal(BigDecimal.ZERO, BigDecimal("1000")), 1..20)
        checkAll(values, modes) { exact, mode ->
            val total = exact.fold(BigDecimal.ZERO, BigDecimal::add).setScale(2, mode)
            val allocated = allocateRounded(exact, total, 2)
            allocated.fold(BigDecimal.ZERO, BigDecimal::add).compareTo(total) shouldBe 0
            exact.zip(allocated).forEach { (value, share) ->
                ((share - value).abs() < BigDecimal("0.01")) shouldBe true
            }
        }
    }
})
