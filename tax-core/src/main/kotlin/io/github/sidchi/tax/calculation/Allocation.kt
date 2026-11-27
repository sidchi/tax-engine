package io.github.sidchi.tax.calculation

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounds each of [values] to [scale] so that the results add up to exactly [total], using the largest-remainder
 * method: every value is first rounded down, then the leftover minor units go one each to the values that lost the
 * most to rounding down (ties go to the earlier value, so the result is deterministic).
 *
 * [values] must be non-negative and [total] must be a rounding of their sum at [scale]; any of the standard
 * rounding modes satisfies that. This is how an invoice-level tax total is spread back over its lines.
 */
internal fun allocateRounded(values: List<BigDecimal>, total: BigDecimal, scale: Int): List<BigDecimal> {
    require(values.all { it.signum() >= 0 }) { "Only non-negative amounts can be allocated: $values" }
    val floors = values.map { it.setScale(scale, RoundingMode.DOWN) }
    val unit = BigDecimal.ONE.movePointLeft(scale)
    val leftover = (total - floors.fold(BigDecimal.ZERO, BigDecimal::add)).divide(unit).intValueExact()
    require(leftover in 0..values.size) {
        "Total ${total.toPlainString()} is not a rounding of the sum of $values at scale $scale"
    }
    val receivers = values.indices
        .sortedWith(compareByDescending<Int> { values[it] - floors[it] }.thenBy { it })
        .take(leftover)
        .toSet()
    return floors.mapIndexed { index, floor -> if (index in receivers) floor + unit else floor }
}
