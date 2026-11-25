package io.github.sidchi.tax.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.http.HttpStatus

/** Boots the full application on a random port and checks the operational surface. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ActuatorEndpointsTest(
    @Autowired private val rest: TestRestTemplate,
) {
    @Test
    fun `health is UP`() {
        val response = rest.getForEntity<Map<String, Any>>("/actuator/health")

        response.statusCode shouldBe HttpStatus.OK
        response.body?.get("status") shouldBe "UP"
    }

    @Test
    fun `liveness and readiness probes are exposed`() {
        listOf("/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            val response = rest.getForEntity<Map<String, Any>>(path)

            response.statusCode shouldBe HttpStatus.OK
            response.body?.get("status") shouldBe "UP"
        }
    }

    @Test
    fun `info reports build metadata`() {
        val response = rest.getForEntity<Map<String, Any>>("/actuator/info")

        response.statusCode shouldBe HttpStatus.OK
        val build = response.body?.get("build") as Map<*, *>
        build["artifact"] shouldBe "tax-service"
        build["version"].toString().shouldNotBeBlank()
    }

    @Test
    fun `sensitive actuator endpoints are not exposed`() {
        listOf("/actuator/env", "/actuator/beans", "/actuator/configprops").forEach { path ->
            rest.getForEntity<String>(path).statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }
}
