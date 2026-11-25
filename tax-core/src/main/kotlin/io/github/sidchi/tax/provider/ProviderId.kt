package io.github.sidchi.tax.provider

/**
 * Stable identifier of a tax provider, e.g. `rule-table` or `vertex-stub`.
 *
 * Almost everything in the engine is keyed per provider: routing, circuit breakers, the cached-rate fallback,
 * reconciliation reports, and the provider column of the audit log. Because the id ends up in metric tags, log
 * fields and database rows, it is restricted to a short lowercase slug: 1 to [MAX_LENGTH] characters of `a-z` and
 * `0-9`, in segments separated by single hyphens. That keeps it safe to use unescaped everywhere and stops
 * `Vertex`, `vertex ` and `vertex` from becoming three different providers.
 */
@JvmInline
public value class ProviderId(
    /** The slug, e.g. `rule-table`. */
    public val value: String,
) {
    init {
        require(value.length <= MAX_LENGTH && SLUG.matches(value)) {
            "Provider id must be a lowercase slug of at most $MAX_LENGTH characters ([a-z0-9] segments " +
                "separated by single hyphens), but was '$value'"
        }
    }

    /** The slug itself, so ids read naturally in logs and messages. */
    override fun toString(): String = value

    /** Validation rules shared by every id. */
    public companion object {
        /** Longest accepted id; generous for names, short enough for metric tags and indexed columns. */
        public const val MAX_LENGTH: Int = 32

        private val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}
