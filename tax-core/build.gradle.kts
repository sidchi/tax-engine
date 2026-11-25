plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    `java-library`
}

description = "Framework-free tax domain: lines, jurisdictions, quotes, rounding and the provider SPI."

kotlin {
    // tax-core is consumed by tax-service and by every provider adapter: keep its surface deliberate.
    explicitApi()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.platform.launcher)

    detektPlugins(libs.detekt.formatting)
}

// Guardrail for the module boundary declared in settings.gradle.kts: fail `check` if Spring (or anything
// pulling it in transitively) ever lands on tax-core's runtime classpath.
val verifyFrameworkFree by tasks.registering {
    group = "verification"
    description = "Fails if tax-core's runtime classpath contains Spring artifacts."
    val runtimeClasspath = configurations.runtimeClasspath
    doLast {
        val leaked = runtimeClasspath.get().incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            .filter { it.group.startsWith("org.springframework") }
        check(leaked.isEmpty()) { "tax-core must stay framework-free, but depends on: $leaked" }
    }
}

tasks.named("check") {
    dependsOn(verifyFrameworkFree)
}
