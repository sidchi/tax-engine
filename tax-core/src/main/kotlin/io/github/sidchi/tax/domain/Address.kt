package io.github.sidchi.tax.domain

import java.util.Locale

/** An ISO 3166-1 alpha-2 country code in upper case, e.g. `US`, `DE`. */
@JvmInline
public value class CountryCode(
    /** The two-letter code. */
    public val value: String,
) {
    init {
        require(value in ISO_COUNTRIES) { "Not an ISO 3166-1 alpha-2 country code: '$value'" }
    }

    /** The code itself. */
    override fun toString(): String = value

    /** Known codes and commonly used constants. */
    public companion object {
        private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()

        /** United States. */
        public val US: CountryCode = CountryCode("US")
    }
}

/**
 * A postal address used to source tax: which jurisdictions apply to a sale.
 *
 * Only the fields that drive jurisdiction lookup are modeled; names, phone numbers and the like belong to the
 * billing system. For US addresses [region] (the state, e.g. `WA`) and [postalCode] are required, because sales tax
 * cannot be sourced without them.
 */
public data class Address(
    /** First street line. */
    public val line1: String,
    /** City or locality. */
    public val city: String,
    /** State, province or region code, e.g. `WA` or `BY`; null where the country has none. */
    public val region: String?,
    /** Postal or ZIP code; null where the country has none. */
    public val postalCode: String?,
    /** Country. */
    public val country: CountryCode,
    /** Optional second street line. */
    public val line2: String? = null,
) {
    init {
        require(line1.isNotBlank()) { "Address line1 must not be blank" }
        require(city.isNotBlank()) { "Address city must not be blank" }
        require(listOfNotNull(region, postalCode, line2).none { it.isBlank() }) {
            "Optional address fields must be null rather than blank"
        }
        require(country != CountryCode.US || (region != null && postalCode != null)) {
            "US addresses need a state (region) and ZIP code (postalCode) to source sales tax"
        }
    }
}
