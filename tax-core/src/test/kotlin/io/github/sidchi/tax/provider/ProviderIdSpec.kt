package io.github.sidchi.tax.provider

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

class ProviderIdSpec : FunSpec({
    val slugChars = ('a'..'z') + ('0'..'9')
    val segment = Arb.list(Arb.element(slugChars), 1..6).map { it.joinToString("") }
    val slugs = Arb.list(segment, 1..4).map { it.joinToString("-") }

    test("accepts any well-formed slug and renders it unchanged") {
        checkAll(slugs) { slug ->
            val id = ProviderId(slug)
            id.value shouldBe slug
            id.toString() shouldBe slug
        }
    }

    test("ids with the same slug are equal") {
        ProviderId("rule-table") shouldBe ProviderId("rule-table")
    }

    test("accepts an id of exactly the maximum length") {
        ProviderId("a".repeat(ProviderId.MAX_LENGTH)).value.length shouldBe ProviderId.MAX_LENGTH
    }

    listOf(
        "" to "empty",
        "Vertex" to "upper case",
        "vertex " to "trailing space",
        "rule_table" to "underscore",
        "-vertex" to "leading hyphen",
        "vertex-" to "trailing hyphen",
        "rule--table" to "double hyphen",
        "a".repeat(ProviderId.MAX_LENGTH + 1) to "too long",
    ).forEach { (raw, why) ->
        test("rejects an id that is $why") {
            val error = shouldThrow<IllegalArgumentException> { ProviderId(raw) }
            error.message shouldContain "lowercase slug"
        }
    }
})
