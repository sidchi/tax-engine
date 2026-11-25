import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    // Opens @Component/@Configuration classes so Spring can proxy them (Kotlin classes are final by default).
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.detekt)
}

description = "Spring Boot service exposing tax quotes and the quote -> commit -> void/refund lifecycle."

dependencies {
    // Spring Boot's BOM as a native Gradle platform instead of the io.spring.dependency-management
    // plugin: versions are aligned only on the configurations that import it, so detekt's isolated
    // classpath is left alone. The Kotlin BOM pins every org.jetbrains.kotlin artifact (kotlin-reflect
    // included) to the compiler's version; Gradle picks the highest constraint, so it beats the older
    // Kotlin version Spring Boot manages and no mixed-version runtime jars end up on the classpath.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.kotlin.bom))

    implementation(project(":tax-core"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    detektPlugins(libs.detekt.formatting)
}

springBoot {
    // Exposes artifact/version on /actuator/info. "time" is excluded so the task stays cacheable.
    buildInfo {
        excludes.set(setOf("time"))
    }
}
