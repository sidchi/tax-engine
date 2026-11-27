package io.github.sidchi.tax.domain

/**
 * The level of government that levies a tax. In the US one sale can owe tax at several levels at once
 * (state + county + city + special district); in the EU a sale usually owes VAT at [COUNTRY] level only.
 */
public enum class JurisdictionLevel {
    /** National tax, e.g. VAT or GST. */
    COUNTRY,

    /** State or province. */
    STATE,

    /** County or parish. */
    COUNTY,

    /** City or municipality. */
    CITY,

    /** Special-purpose district, e.g. a transit or stadium district. */
    DISTRICT,
}

/**
 * A taxing authority, e.g. "State of Washington" at [JurisdictionLevel.STATE].
 *
 * [code] is the identifier the provider uses for it (a FIPS code, a provider-specific id, or the ISO country code
 * for VAT); [name] is for people reading quotes and audit logs.
 */
public data class Jurisdiction(
    /** The country the jurisdiction belongs to. */
    public val country: CountryCode,
    /** The level of government. */
    public val level: JurisdictionLevel,
    /** Provider-assigned identifier, unique within [country] and [level]. */
    public val code: String,
    /** Human-readable name. */
    public val name: String,
) {
    init {
        require(code.isNotBlank()) { "Jurisdiction code must not be blank" }
        require(name.isNotBlank()) { "Jurisdiction name must not be blank" }
    }
}

/** A rate levied by one jurisdiction on one line: the unit a provider returns and a quote reports. */
public data class JurisdictionRate(
    /** Who levies the tax. */
    public val jurisdiction: Jurisdiction,
    /** At what rate. A zero rate means the line is taxable but zero-rated in this jurisdiction. */
    public val rate: TaxRate,
)
