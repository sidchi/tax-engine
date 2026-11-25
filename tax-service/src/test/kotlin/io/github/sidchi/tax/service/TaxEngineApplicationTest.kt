package io.github.sidchi.tax.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/** Smoke test: the Spring context starts with the default configuration. */
@SpringBootTest
class TaxEngineApplicationTest(
    @Autowired private val context: ApplicationContext,
) {
    @Test
    fun `context loads`() {
        context.getBeansOfType(TaxEngineApplication::class.java).size shouldBe 1
    }
}
