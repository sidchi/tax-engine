rootProject.name = "tax-engine"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// Framework-free domain and tax logic. Must never depend on Spring.
include(":tax-core")
// Spring Boot service: HTTP API, persistence, provider wiring, ops.
include(":tax-service")
