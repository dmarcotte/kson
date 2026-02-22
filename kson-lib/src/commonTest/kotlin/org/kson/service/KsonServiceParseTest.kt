package org.kson.service

import org.kson.Kson
import org.kson.KsonService
import org.kson.OutputFormat
import org.kson.ParseResult
import org.kson.Result
import org.kson.TranspileOptions
import kotlin.test.*

/**
 * Tests for [Kson.parse], which combines schema parsing, source validation,
 * and optional transpilation into a single [KsonService] operation.
 *
 * Tests verify that [ParseResult.Success] is returned when parsing completes
 * (even with findings), and [ParseResult.Failure] when the schema or source
 * cannot be parsed.
 */
class KsonServiceParseTest {

    private val service: KsonService = Kson

    private val objectSchema = """{ type: object, properties: { name: { type: string }, age: { type: number } } }"""

    @Test
    fun parse_validDocument() {
        val result = service.parse(
            kson = """{ name: "Tester", age: 42 }""",
            schema = objectSchema
        )
        assertIs<ParseResult.Success>(result)
        assertTrue(result.messages.isEmpty(), "Valid document should produce no messages")
        assertNull(result.output, "No outputFormat means no output")
    }

    @Test
    fun parse_invalidDocument() {
        val result = service.parse(
            kson = """{ name: 42 }""",
            schema = """{
                type: object
                properties: {
                    name: { type: string }
                }
            }"""
        )
        assertIs<ParseResult.Success>(result, "Parsing completed, so result is Success with findings")
        assertTrue(result.messages.isNotEmpty(), "Invalid document should produce validation messages")
        assertNull(result.output, "No outputFormat means no output")
    }

    @Test
    fun parse_invalidSchema() {
        val result = service.parse(
            kson = """{ project: "KSON" }""",
            schema = """{ this is not valid }"""
        )
        assertIs<ParseResult.Failure>(result, "Unparseable schema should produce Failure")
        assertTrue(result.errors.isNotEmpty(), "Failure should include error messages")
    }

    @Test
    fun parse_invalidSource() {
        val result = service.parse(
            kson = """{ invalid: }""",
            schema = """{ type: object }"""
        )
        assertIs<ParseResult.Failure>(result, "Unparseable source should produce Failure")
        assertTrue(result.errors.isNotEmpty(), "Failure should include error messages")
    }

    @Test
    fun parse_withFilepath() {
        val result = service.parse(
            kson = """{ project: "KSON" }""",
            schema = """{ type: object }""",
            filepath = "/path/to/document.kson"
        )
        assertIs<ParseResult.Success>(result)
        assertTrue(result.messages.isEmpty(), "Valid document with filepath should produce no messages")
    }

    @Test
    fun parse_withJsonOutput() {
        val kson = """{ name: "Tester", age: 42 }"""
        val result = service.parse(
            kson = kson,
            schema = objectSchema,
            outputFormat = OutputFormat.JSON
        )
        assertIs<ParseResult.Success>(result)
        assertTrue(result.messages.isEmpty())
        assertNotNull(result.output, "JSON outputFormat should produce output")

        // The output should match a direct toJson conversion
        val directResult = service.toJson(kson)
        assertIs<Result.Success>(directResult)
        assertEquals(directResult.output, result.output)
    }

    @Test
    fun parse_withYamlOutput() {
        val kson = """{ name: "Tester", age: 42 }"""
        val result = service.parse(
            kson = kson,
            schema = objectSchema,
            outputFormat = OutputFormat.YAML
        )
        assertIs<ParseResult.Success>(result)
        assertTrue(result.messages.isEmpty())
        assertNotNull(result.output, "YAML outputFormat should produce output")

        // The output should match a direct toYaml conversion
        val directResult = service.toYaml(kson)
        assertIs<Result.Success>(directResult)
        assertEquals(directResult.output, result.output)
    }

    @Test
    fun parse_withRetainEmbedTagsFalse() {
        val kson = "script: %bash\necho hello\n%%"
        val schema = """{ type: object }"""
        val result = service.parse(
            kson = kson,
            schema = schema,
            outputFormat = OutputFormat.JSON,
            retainEmbedTags = false
        )
        assertIs<ParseResult.Success>(result)
        assertTrue(result.messages.isEmpty())
        assertNotNull(result.output)

        // Output should match a direct toJson conversion with the same option
        val directResult = service.toJson(kson, TranspileOptions.Json(retainEmbedTags = false))
        assertIs<Result.Success>(directResult)
        assertEquals(directResult.output, result.output)

        // And should differ from retainEmbedTags = true (the default)
        val defaultResult = service.toJson(kson)
        assertIs<Result.Success>(defaultResult)
        assertNotEquals(defaultResult.output, result.output)
    }

    @Test
    fun parse_withRetainEmbedTagsFalseYaml() {
        val kson = "script: %bash\necho hello\n%%"
        val schema = """{ type: object }"""
        val result = service.parse(
            kson = kson,
            schema = schema,
            outputFormat = OutputFormat.YAML,
            retainEmbedTags = false
        )
        assertIs<ParseResult.Success>(result)
        assertTrue(result.messages.isEmpty())
        assertNotNull(result.output)

        // Output should match a direct toYaml conversion with the same option
        val directResult = service.toYaml(kson, TranspileOptions.Yaml(retainEmbedTags = false))
        assertIs<Result.Success>(directResult)
        assertEquals(directResult.output, result.output)

        // And should differ from retainEmbedTags = true (the default)
        val defaultResult = service.toYaml(kson)
        assertIs<Result.Success>(defaultResult)
        assertNotEquals(defaultResult.output, result.output)
    }

    @Test
    fun parse_withFindingsAndOutput() {
        val result = service.parse(
            kson = """{ name: 42 }""",
            schema = """{
                type: object
                properties: {
                    name: { type: string }
                }
            }""",
            outputFormat = OutputFormat.JSON
        )
        assertIs<ParseResult.Success>(result, "Output is always produced when requested, even with findings")
        assertTrue(result.messages.isNotEmpty(), "Should have validation findings")
        assertNotNull(result.output, "Output should still be produced despite findings")
    }
}
