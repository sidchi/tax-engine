package io.github.sidchi.tax.domain

/**
 * Classifies what is being sold for tax purposes, e.g. `SAAS`, `DIGITAL.EBOOK` or `GENERAL`.
 *
 * The same price can be fully taxable, reduced-rate, zero-rated or exempt depending on the product and the
 * jurisdiction, so every line carries a code and providers map it to their own taxability rules. Codes are
 * provider-neutral here: an upper-case slug of 1 to [MAX_LENGTH] characters, `A-Z` and `0-9` segments separated by
 * single `.`, `-` or `_`. Mapping to a provider's own code set is the adapter's job.
 */
@JvmInline
public value class ProductTaxCode(
    /** The code, e.g. `SAAS`. */
    public val value: String,
) {
    init {
        require(value.length <= MAX_LENGTH && CODE.matches(value)) {
            "Product tax code must be an upper-case code of at most $MAX_LENGTH characters ([A-Z0-9] segments " +
                "separated by '.', '-' or '_'), but was '$value'"
        }
    }

    /** The code itself. */
    override fun toString(): String = value

    /** Validation rules. */
    public companion object {
        /** Longest accepted code. */
        public const val MAX_LENGTH: Int = 32

        private val CODE = Regex("[A-Z0-9]+([._-][A-Z0-9]+)*")
    }
}
