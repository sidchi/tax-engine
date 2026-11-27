package io.github.sidchi.tax.domain

import java.math.BigDecimal

/** Identifies a line within one tax request, e.g. the invoice line or subscription item id. */
@JvmInline
public value class LineId(
    /** The identifier. */
    public val value: String,
) {
    init {
        require(value.isNotBlank() && value.length <= MAX_LENGTH) {
            "Line id must be non-blank and at most $MAX_LENGTH characters, but was '$value'"
        }
    }

    /** The identifier itself. */
    override fun toString(): String = value

    /** Validation rules. */
    public companion object {
        /** Longest accepted id. */
        public const val MAX_LENGTH: Int = 64
    }
}

/**
 * A reduction of a line's price before tax.
 *
 * Every discount is computed against the line's gross amount (unit price × quantity), never against the amount left
 * after other discounts, so the order of discounts never changes the result.
 */
public sealed interface Discount {
    /** The exact amount this discount takes off a line whose gross amount is [gross]. */
    public fun amountOff(gross: Money): Money

    /** A fixed amount off, e.g. a 5.00 USD coupon. */
    public data class Fixed(
        /** The amount off; must not be negative. */
        public val amount: Money,
    ) : Discount {
        init {
            require(!amount.isNegative) { "Discount amount must not be negative, but was $amount" }
        }

        override fun amountOff(gross: Money): Money = amount
    }

    /** A percentage off the gross amount, e.g. `0.10` for 10% off. */
    public data class Percentage(
        /** The fraction off, between 0 and 1. */
        public val fraction: BigDecimal,
    ) : Discount {
        init {
            require(fraction.signum() >= 0 && fraction <= BigDecimal.ONE) {
                "Discount fraction must be between 0 and 1, but was ${fraction.toPlainString()}"
            }
        }

        override fun amountOff(gross: Money): Money = gross * fraction
    }
}

/**
 * One taxable item on an invoice or checkout.
 *
 * Amounts here are exact and unrounded. Whether [unitPrice] already includes tax is decided for the whole request by
 * [PricingMode]; the line itself only carries what is sold, for how much and how many. [quantity] may be fractional
 * (metered usage such as 1.5 GB) but must be positive; credits and refunds are modeled as reversals of committed
 * transactions, not as negative lines.
 */
public data class TaxableLine(
    /** Unique within the request. */
    public val id: LineId,
    /** What is being sold, for taxability. */
    public val taxCode: ProductTaxCode,
    /** Price of one unit; must not be negative. */
    public val unitPrice: Money,
    /** Number of units; must be positive. */
    public val quantity: BigDecimal = BigDecimal.ONE,
    /** Discounts applied before tax, each against the gross amount. */
    public val discounts: List<Discount> = emptyList(),
) {
    /** Unit price × quantity, exact. */
    public val grossAmount: Money = unitPrice * quantity

    /** Total of all discounts, exact. */
    public val discountAmount: Money

    /** Gross amount minus discounts, exact: what the line is sold for before rounding and tax. */
    public val netAmount: Money

    init {
        require(!unitPrice.isNegative) { "Unit price must not be negative, but was $unitPrice on line $id" }
        require(quantity.signum() > 0) { "Quantity must be positive, but was ${quantity.toPlainString()} on line $id" }
        discounts.filterIsInstance<Discount.Fixed>().forEach {
            require(it.amount.currency == unitPrice.currency) {
                "Discount ${it.amount} on line $id is not in the line currency ${unitPrice.currency}"
            }
        }
        discountAmount = discounts.map { it.amountOff(grossAmount) }.sum(unitPrice.currency)
        require(discountAmount <= grossAmount) {
            "Discounts ($discountAmount) exceed the gross amount ($grossAmount) on line $id"
        }
        netAmount = grossAmount - discountAmount
    }
}
