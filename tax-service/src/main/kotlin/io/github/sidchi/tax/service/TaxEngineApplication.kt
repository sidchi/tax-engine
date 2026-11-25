package io.github.sidchi.tax.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Spring Boot entry point for the tax engine service. */
@SpringBootApplication
class TaxEngineApplication

/** Starts the service; see `application.yml` for defaults. */
fun main(args: Array<String>) {
    @Suppress("SpreadOperator") // runs once at startup; the array copy is irrelevant
    runApplication<TaxEngineApplication>(*args)
}
