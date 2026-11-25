# tax-engine

A sales tax and VAT service for subscription commerce, written in Kotlin on Spring Boot 3. It calculates tax through pluggable providers, manages the quote → commit → void/refund lifecycle, and stays correct when a provider is slow or down. Provider integrations run against local stubs built from public documentation; all rates and addresses are synthetic samples, not authoritative tax data.
