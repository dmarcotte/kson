package org.kson.service

import org.kson.Kson
import org.kson.KsonService
import org.kson.KsonCore
import org.kson.Result
import org.kson.TranspileOptions
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import kotlin.test.*

/**
 * Tests for [KsonProtocolHandler], verifying the roundtrip from KSON request
 * document through [KsonService] dispatch back to KSON response document.
 *
 * Responses are parsed back as KSON and verified field-by-field, dogfooding
 * KSON to validate KSON service responses.
 */
class KsonProtocolHandlerTest {

    private val handler = KsonProtocolHandler(Kson)

    /**
     * Parses a KSON response string and returns it as a [KsonObject].
     */
    private fun parseResponse(response: String): KsonObject {
        val parseResult = KsonCore.parseToAst(response)
        assertFalse(parseResult.hasErrors(), "Response should be valid KSON: $response")
        val value = parseResult.ksonValue
        assertIs<KsonObject>(value, "Response should be a KSON object")
        return value
    }

    private fun assertStatus(response: KsonObject, expected: String) {
        val status = response.propertyLookup["status"]
        assertIs<KsonString>(status, "Response should have a status field")
        assertEquals(expected, status.value)
    }

    private fun assertOutput(response: KsonObject): String {
        val output = response.propertyLookup["output"]
        assertIs<KsonString>(output, "Success response should have an output field")
        return output.value
    }

    private fun assertParseMessages(response: KsonObject): KsonList {
        assertStatus(response, "success")
        val messages = response.propertyLookup["messages"]
        assertIs<KsonList>(messages, "Parse response should have a messages field")
        return messages
    }

    private fun assertFailureHasMessages(response: KsonObject) {
        assertStatus(response, "failure")
        val messages = response.propertyLookup["messages"]
        assertIs<KsonList>(messages, "Failure response should have a messages field")
        assertTrue(messages.elements.isNotEmpty(), "Failure response should have at least one message")
    }

    @Test
    fun format_roundtrip() {
        val response = handler.execute("""
            { operation: format, source: "project: KSON" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        // Verify the output matches direct service call
        val expected = Kson.format("project: KSON")
        assertEquals(expected, output)
    }

    @Test
    fun format_withIndentSize() {
        val source = "project:\n  name: KSON"
        val response = handler.execute("""
            {
              operation: format
              source: "$source"
              options: { indentSize: 4 }
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        // Verify the output matches direct service call with same options
        val expected = Kson.format(source, org.kson.FormatOptions(indentType = org.kson.IndentType.Spaces(4)))
        assertEquals(expected, output)
    }

    @Test
    fun format_withFormattingStyle() {
        val source = "datatype: test"
        val response = handler.execute("""
            {
              operation: format
              source: "$source"
              options: { formattingStyle: delimited }
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        val expected = Kson.format(source, org.kson.FormatOptions(formattingStyle = org.kson.FormattingStyle.DELIMITED))
        assertEquals(expected, output)
    }

    @Test
    fun format_withTabIndent() {
        val source = "project:\n  name: KSON"
        val response = handler.execute("""
            {
              operation: format
              source: "$source"
              options: { indentType: tabs }
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        // Verify the output matches direct service call with same options
        val expected = Kson.format(source, org.kson.FormatOptions(indentType = org.kson.IndentType.Tabs))
        assertEquals(expected, output)
    }

    @Test
    fun format_withEmbedBlockSource() {
        val response = handler.execute("""
            {
              operation: format
              source: %kson
                datatype: test
                project: KSON
                %%
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        // Verify the output matches formatting the same content directly
        val expected = Kson.format("datatype: test\nproject: KSON")
        assertEquals(expected, output)
    }

    @Test
    fun format_outputWithSpecialCharacters() {
        // Source with quoted string values exercises the renderForJsonString
        // escaping roundtrip: the formatted output contains quotes and newlines
        // that must survive being embedded in the response's output string
        val response = handler.execute("""
            {
              operation: format
              source: %kson
                greeting: "hello\nworld"
                %%
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        val expected = Kson.format("greeting: \"hello\\nworld\"")
        assertEquals(expected, output)
    }

    @Test
    fun toJson_roundtrip() {
        val source = "datatype: test"
        val response = handler.execute("""
            { operation: toJson, source: "$source" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        val expected = Kson.toJson(source)
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)
    }

    @Test
    fun toJson_withRetainEmbedTagsFalse() {
        val source = "datatype: test"
        val response = handler.execute("""
            {
              operation: toJson
              source: "$source"
              options: { retainEmbedTags: false }
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        val expected = Kson.toJson(source, TranspileOptions.Json(retainEmbedTags = false))
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)
    }

    @Test
    fun toJson_invalidSource() {
        val response = handler.execute("""
            { operation: toJson, source: "{ invalid: }" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun toYaml_roundtrip() {
        val source = "datatype: test"
        val response = handler.execute("""
            { operation: toYaml, source: "$source" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        val expected = Kson.toYaml(source)
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)
    }

    @Test
    fun toYaml_withRetainEmbedTagsTrue() {
        val source = "datatype: test"
        val response = handler.execute("""
            {
              operation: toYaml
              source: "$source"
              options: { retainEmbedTags: true }
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertStatus(parsed, "success")
        val output = assertOutput(parsed)

        val expected = Kson.toYaml(source, TranspileOptions.Yaml(retainEmbedTags = true))
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)
    }

    @Test
    fun toYaml_invalidSource() {
        val response = handler.execute("""
            { operation: toYaml, source: "{ invalid: }" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun parse_validDocument() {
        val response = handler.execute("""
            {
              operation: parse
              source: '{ "project": "KSON" }'
              schema: '{ "type": "object" }'
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        val messages = assertParseMessages(parsed)
        assertTrue(messages.elements.isEmpty(), "Valid document should produce no messages")
        assertNull(parsed.propertyLookup["output"], "No outputFormat means no output field")
    }

    @Test
    fun parse_invalidDocument() {
        val response = handler.execute("""
            {
              operation: parse
              source: '{ "name": 42 }'
              schema: '{ "type": "object", "properties": { "name": { "type": "string" } } }'
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        val messages = assertParseMessages(parsed)
        assertTrue(messages.elements.isNotEmpty(), "Invalid document should produce validation messages")
    }

    @Test
    fun parse_invalidSchema() {
        val response = handler.execute("""
            {
              operation: parse
              source: '{ "project": "KSON" }'
              schema: "{ this is not valid }"
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun parse_invalidSource() {
        val response = handler.execute("""
            {
              operation: parse
              source: "{ invalid: }"
              schema: '{ "type": "object" }'
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun parse_missingSchema() {
        val response = handler.execute("""
            { operation: parse, source: "project: KSON" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun parse_withEmbedBlockSourceAndSchema() {
        val response = handler.execute("""
            {
              operation: parse
              source: %kson
                datatype: test
                %%
              schema: %kson
                type: object
                %%
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        val messages = assertParseMessages(parsed)
        assertTrue(messages.elements.isEmpty(), "Valid document should produce no messages")
    }

    @Test
    fun parse_unknownOutputFormat() {
        val response = handler.execute("""
            {
              operation: parse
              source: "project: KSON"
              schema: '{ "type": "object" }'
              outputFormat: xml
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun parse_withJsonOutputFormat() {
        val source = """datatype: test"""
        val response = handler.execute("""
            {
              operation: parse
              source: "$source"
              schema: '{ "type": "object" }'
              outputFormat: json
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertParseMessages(parsed)
        val output = assertOutput(parsed)

        // Output should match a direct toJson conversion
        val expected = Kson.toJson(source)
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)
    }

    @Test
    fun parse_withYamlOutputFormat() {
        val source = """datatype: test"""
        val response = handler.execute("""
            {
              operation: parse
              source: "$source"
              schema: '{ "type": "object" }'
              outputFormat: yaml
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertParseMessages(parsed)
        val output = assertOutput(parsed)

        // Output should match a direct toYaml conversion
        val expected = Kson.toYaml(source)
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)
    }

    @Test
    fun parse_withRetainEmbedTagsFalse() {
        val source = "script: %bash\necho hello\n%%"
        val response = handler.execute("""
            {
              operation: parse
              source: "script: %bash\necho hello\n%%"
              schema: '{ "type": "object" }'
              outputFormat: json
              options: { retainEmbedTags: false }
            }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertParseMessages(parsed)
        val output = assertOutput(parsed)

        // Output should match a direct toJson conversion with the same option
        val expected = Kson.toJson(source, TranspileOptions.Json(retainEmbedTags = false))
        assertIs<Result.Success>(expected)
        assertEquals(expected.output, output)

        // And should differ from the default (retainEmbedTags = true)
        val defaultExpected = Kson.toJson(source)
        assertIs<Result.Success>(defaultExpected)
        assertNotEquals(defaultExpected.output, output)
    }

    @Test
    fun error_malformedRequest() {
        val response = handler.execute("{ this is not { valid")

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun error_nonObjectRequest() {
        val response = handler.execute(""""just a string"""")

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun error_missingOperation() {
        val response = handler.execute("""
            { source: "project: KSON" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun error_missingSource() {
        val response = handler.execute("""
            { operation: format }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }

    @Test
    fun error_unknownOperation() {
        val response = handler.execute("""
            { operation: unknownOp, source: "project: KSON" }
        """.trimIndent())

        val parsed = parseResponse(response)
        assertFailureHasMessages(parsed)
    }
}
