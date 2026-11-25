# tax-engine

[![CI](https://github.com/sidchi/tax-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/sidchi/tax-engine/actions/workflows/ci.yml)

A sales tax and VAT service for subscription commerce, written in Kotlin on Spring Boot 3. It calculates tax through
pluggable providers, manages the **quote → commit → void/refund** lifecycle, and stays correct when a provider is slow
or down.

> **Sample data only.** Provider integrations run against local stubs built from public documentation concepts.
> Every rate, address and jurisdiction in this repo is a synthetic or clearly labeled sample, not authoritative tax
> data, and there are no real provider credentials anywhere.

> **Status:** scaffold (build, CI, service skeleton). The domain model is next. See [ROADMAP.md](ROADMAP.md).

## The problem

Charging tax on a one-off order is a lookup. Charging it on subscriptions, at scale, is a distributed-systems
problem wearing an accounting hat:

- **Tax is a two-phase transaction.** A checkout gets a *quote*; only a paid invoice gets *committed* to the
  provider, which is what ends up on the filed return. Failed payments must not leave committed tax behind, and a
  refund must reverse exactly what was committed — no more, no less.
- **Providers are remote, slow and occasionally down.** A renewal run can't stall because the tax API has a bad
  minute, yet guessing tax wrong is a compliance problem. The engine needs timeouts, a circuit breaker per provider
  and a clearly flagged *estimated* fallback that is reconciled later.
- **Rounding is policy, not an afterthought.** Per-line vs. per-invoice rounding, tax-inclusive (EU VAT) vs.
  tax-exclusive (US sales tax) pricing, and multi-jurisdiction stacking (state + county + city + district) each
  change the cents a customer pays.
- **Subscriptions keep changing underneath the tax.** Mid-cycle upgrades prorate, address changes move the
  customer into a new jurisdiction, trials convert to paid, and partial refunds need recalculation against the
  original rates, not today's.
- **Every number must be explainable later.** Auditors and support ask "why did this customer pay 8.875%?" months
  after the fact, so every quote, commit and void is recorded immutably and reconciled against the provider.

## Planned architecture

```
            checkout / renewal / refund (billing system)
                              │  REST
                              ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │ tax-service  (Spring Boot)                                       │
   │                                                                  │
   │  TaxController ──► TransactionService                            │
   │                    quote → commit → void / refund                │
   │                    idempotency keys, state machine               │
   │                         │                 │                      │
   │                         ▼                 ▼                      │
   │                  ProviderRouter       AuditLog ──► reconcile job │
   │    timeout · retry · breaker per provider                        │
   │                 │                     │                          │
   │                 ▼                     ▼                          │
   │        cached-rate fallback     Postgres (Flyway)                │
   │        ("estimated" + reconcile-later queue)                     │
   └───────────┬───────────────────────────┬──────────────────────────┘
               ▼                           ▼
     ┌──────────────────┐        ┌──────────────────────┐
     │ RuleTableProvider│        │ Vertex-style adapter │ ──► WireMock stub
     │ (sample rates)   │        │ (public concepts)    │
     └──────────────────┘        └──────────────────────┘
       both implement the TaxProvider SPI in tax-core (no Spring)
```

## Modules

| Module | What lives there | Depends on |
|--------|------------------|------------|
| `tax-core` | Domain model (lines, addresses, jurisdictions, quotes, rounding) and the provider SPI. Plain Kotlin in explicit-API mode. | Kotlin stdlib only |
| `tax-service` | Spring Boot app: HTTP API, transaction lifecycle, persistence, provider wiring, resilience, metrics. | `tax-core`, Spring Boot |

`tax-core` staying framework-free is enforced, not just intended: its `verifyFrameworkFree` task (part of
`check`) fails the build if any `org.springframework` artifact reaches its runtime classpath. Provider adapters and
the tax math can then be unit-tested in milliseconds and reused outside Spring.

## Build and run

Requires JDK 21. The Gradle wrapper downloads everything else.

```bash
./gradlew build                   # compile, detekt (incl. ktlint rules), unit + Spring tests
./gradlew :tax-service:bootRun    # start the service on http://localhost:8080
curl localhost:8080/actuator/health
```

Only `health` (with Kubernetes-style `liveness`/`readiness` groups) and `info` are exposed over HTTP; tests
assert that `env`, `beans` and `configprops` stay closed.

## Tooling

- **Kotlin 2.1** on **Java 21**, `-Werror` and `-Xjsr305=strict` so Spring's nullability annotations are enforced.
- **Spring Boot 3.5**, imported as a native Gradle `platform()` BOM rather than the dependency-management plugin,
  so version alignment never leaks into detekt's isolated classpath.
- **JUnit 5 + Kotest**: Kotest specs (including property-based tests) for `tax-core`, JUnit 5 with Spring's test
  support for `tax-service`, both on the JUnit Platform.
- **detekt** with the ktlint formatting rules; zero issues allowed. Overrides live in
  [`config/detekt/detekt.yml`](config/detekt/detekt.yml), each with its reason.
- **GitHub Actions** runs `./gradlew build` on every push and pull request.
