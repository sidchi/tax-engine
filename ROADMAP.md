# Roadmap — tax-engine
Sales tax and VAT service for subscription commerce (Kotlin, Spring Boot 3): calculates tax through pluggable providers, manages the quote → commit → void/refund lifecycle, and stays correct when the provider is slow or down. Provider integrations run against local stubs built from public documentation; rates and addresses are synthetic samples, not authoritative tax data. Each item is one PR. The daily pipeline takes the first unchecked item.
- [x] 1. Scaffold: Kotlin 2.x, Spring Boot 3, Gradle (Kotlin DSL), JUnit 5 + Kotest, detekt/ktlint, GitHub Actions CI, README with problem statement and architecture sketch
- [x] 2. Domain model: taxable lines (amount, quantity, discounts, product tax code), ship-to/bill-to addresses and jurisdictions, tax quote with per-jurisdiction detail, BigDecimal money with explicit rounding (per line vs. per invoice), tax-inclusive vs. tax-exclusive pricing
- [ ] 3. Provider abstraction + in-house rule-table provider (sample US state/local rates and EU VAT rates), golden-file tests for tricky cases (exemptions, rounding, zero-rated products)
- [ ] 4. Vertex-style adapter: client modeled on public O Series concepts (quotation, invoice, distribute tax) against a WireMock stub, request/response mapping tests, no real credentials
- [ ] 5. Transaction lifecycle: quote → commit → void and full/partial refund with recalculation, idempotency keys, Postgres persistence with Flyway, state-machine tests
- [ ] 6. Resilience: timeouts, retries with backoff, circuit breaker per provider, fallback to cached rates with an "estimated" flag and a reconcile-later queue
- [ ] 7. Subscription tax: renewals, mid-cycle upgrade proration and credits, customer address changes, trial-to-paid transitions
- [ ] 8. Audit and reconciliation: immutable audit log of every quote/commit/void, daily report comparing committed tax with provider records, mismatch alerts
- [ ] 9. API and ops: REST endpoints with OpenAPI docs, Micrometer metrics, Dockerfile, docker-compose (Postgres + WireMock), k6 load test with documented p99
- [ ] 10. Polish: architecture diagram, design doc on tax trade-offs (rounding, provider fallback, refund recalculation, nexus), README walkthrough, v1.0 release
