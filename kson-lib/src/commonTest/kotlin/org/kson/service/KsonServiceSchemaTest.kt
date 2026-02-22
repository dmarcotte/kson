package org.kson.service

import org.kson.Kson
import org.kson.SchemaResult
import org.kson.SchemaValidator
import kotlin.test.*

/**
 * Tests that validate the KSON service protocol schema against example request and response
 * documents --- both valid and invalid. These tests exercise KSON's own schema validation
 * against KSON's own service interface, a satisfying bit of dogfooding.
 */
class KsonServiceSchemaTest {

    private val requestValidator: SchemaValidator
    private val responseValidator: SchemaValidator

    init {
        val requestSchemaResult = Kson.parseSchema(REQUEST_SCHEMA)
        assertIs<SchemaResult.Success>(requestSchemaResult, "Request schema must parse without errors")
        requestValidator = requestSchemaResult.schemaValidator

        val responseSchemaResult = Kson.parseSchema(RESPONSE_SCHEMA)
        assertIs<SchemaResult.Success>(responseSchemaResult, "Response schema must parse without errors")
        responseValidator = responseSchemaResult.schemaValidator
    }

    private fun assertValidRequest(request: String) {
        val errors = requestValidator.validate(request)
        assertTrue(errors.isEmpty(),
            "Expected valid request but got errors:\n${errors.joinToString("\n") { it.message }}")
    }

    private fun assertInvalidRequest(request: String) {
        val errors = requestValidator.validate(request)
        assertFalse(errors.isEmpty(), "Expected invalid request but it validated successfully")
    }

    private fun assertValidResponse(response: String) {
        val errors = responseValidator.validate(response)
        assertTrue(errors.isEmpty(),
            "Expected valid response but got errors:\n${errors.joinToString("\n") { it.message }}")
    }

    private fun assertInvalidResponse(response: String) {
        val errors = responseValidator.validate(response)
        assertFalse(errors.isEmpty(), "Expected invalid response but it validated successfully")
    }

    @Test
    fun formatRequest_minimal() {
        assertValidRequest("""
            { operation: format, source: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun formatRequest_withOptions() {
        assertValidRequest("""
            {
              operation: format
              source: "datatype: test"
              options: { indentSize: 4, formattingStyle: delimited }
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_withEmbedSource() {
        assertValidRequest("""
            {
              operation: format
              source: %kson
                datatype: test
                project: KSON
                %%
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_withEmbedBlockRules() {
        assertValidRequest("""
            {
              operation: format
              source: "datatype: test"
              options: {
                embedBlockRules: [
                  { pathPattern: '/scripts/*', tag: bash }
                  { pathPattern: '/config/description' }
                ]
              }
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_withTabsIndent() {
        assertValidRequest("""
            {
              operation: format
              source: "key: value"
              options: { indentType: tabs }
            }
        """.trimIndent())
    }

    @Test
    fun toJsonRequest_minimal() {
        assertValidRequest("""
            { operation: toJson, source: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun toJsonRequest_withOptions() {
        assertValidRequest("""
            {
              operation: toJson
              source: "datatype: test"
              options: { retainEmbedTags: false }
            }
        """.trimIndent())
    }

    @Test
    fun toYamlRequest_minimal() {
        assertValidRequest("""
            { operation: toYaml, source: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun toYamlRequest_withOptions() {
        assertValidRequest("""
            {
              operation: toYaml
              source: "key: value"
              options: { retainEmbedTags: true }
            }
        """.trimIndent())
    }

    @Test
    fun parseRequest_minimal() {
        assertValidRequest("""
            {
              operation: parse
              source: "name: 42"
              schema: '{"type": "object"}'
            }
        """.trimIndent())
    }

    @Test
    fun parseRequest_withFilepath() {
        assertValidRequest("""
            {
              operation: parse
              source: "datatype: test"
              schema: '{"type": "object"}'
              filepath: '/path/to/document.kson'
            }
        """.trimIndent())
    }

    @Test
    fun parseRequest_withOutputFormat() {
        assertValidRequest("""
            {
              operation: parse
              source: "datatype: test"
              schema: '{"type": "object"}'
              outputFormat: json
            }
        """.trimIndent())
    }

    @Test
    fun parseRequest_withYamlOutputFormat() {
        assertValidRequest("""
            {
              operation: parse
              source: "datatype: test"
              schema: '{"type": "object"}'
              outputFormat: yaml
            }
        """.trimIndent())
    }

    @Test
    fun parseRequest_withOptions() {
        assertValidRequest("""
            {
              operation: parse
              source: "datatype: test"
              schema: '{"type": "object"}'
              outputFormat: json
              options: { retainEmbedTags: false }
            }
        """.trimIndent())
    }

    @Test
    fun parseRequest_withEmbedBlocks() {
        assertValidRequest("""
            {
              operation: parse
              source: %kson
                name: 42
                %%
              schema: %kson
                type: object
                properties:
                  name:
                    type: string
                %%
            }
        """.trimIndent())
    }

    @Test
    fun request_missingOperation() {
        assertInvalidRequest("""
            { source: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun request_missingSource() {
        assertInvalidRequest("""
            { operation: format }
        """.trimIndent())
    }

    @Test
    fun request_unknownOperation() {
        assertInvalidRequest("""
            { operation: unknownOp, source: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun parseRequest_missingSchema() {
        assertInvalidRequest("""
            { operation: parse, source: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun parseRequest_invalidOutputFormat() {
        assertInvalidRequest("""
            {
              operation: parse
              source: "datatype: test"
              schema: '{"type": "object"}'
              outputFormat: xml
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_invalidIndentType() {
        assertInvalidRequest("""
            {
              operation: format
              source: "key: value"
              options: { indentType: invalid }
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_invalidFormattingStyle() {
        assertInvalidRequest("""
            {
              operation: format
              source: "key: value"
              options: { formattingStyle: fancy }
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_indentSizeTooSmall() {
        assertInvalidRequest("""
            {
              operation: format
              source: "key: value"
              options: { indentSize: 0 }
            }
        """.trimIndent())
    }

    @Test
    fun formatRequest_extraProperty() {
        assertInvalidRequest("""
            { operation: format, source: "key: value", unexpected: true }
        """.trimIndent())
    }

    @Test
    fun formatRequest_formatOptionsExtraProperty() {
        assertInvalidRequest("""
            {
              operation: format
              source: "key: value"
              options: { indentSize: 2, color: true }
            }
        """.trimIndent())
    }

    @Test
    fun toJsonRequest_formatOptionNotAccepted() {
        assertInvalidRequest("""
            {
              operation: toJson
              source: "key: value"
              options: { indentSize: 4 }
            }
        """.trimIndent())
    }

    @Test
    fun successResponse_withOutput() {
        assertValidResponse("""
            { status: success, output: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun successResponse_withEmbedOutput() {
        assertValidResponse("""
            {
              status: success
              output: %kson
                datatype: test
                project: KSON
                %%
            }
        """.trimIndent())
    }

    @Test
    fun parseResponse_noMessages() {
        assertValidResponse("""
            { status: success, messages: [] }
        """.trimIndent())
    }

    @Test
    fun parseResponse_withMessages() {
        assertValidResponse("""
            {
              status: success
              messages: [
                {
                  message: 'Value does not match type "string"'
                  severity: warning
                  start: { line: 0, column: 6 }
                  end: { line: 0, column: 8 }
                }
              ]
            }
        """.trimIndent())
    }

    @Test
    fun parseResponse_withOutput() {
        assertValidResponse("""
            { status: success, messages: [], output: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun parseResponse_withMessagesAndOutput() {
        assertValidResponse("""
            {
              status: success
              messages: [
                {
                  message: 'Value does not match type "string"'
                  severity: warning
                  start: { line: 0, column: 6 }
                  end: { line: 0, column: 8 }
                }
              ]
              output: '{"name": 42}'
            }
        """.trimIndent())
    }

    @Test
    fun failureResponse() {
        assertValidResponse("""
            {
              status: failure
              messages: [
                {
                  message: 'Unexpected token'
                  severity: error
                  start: { line: 0, column: 5 }
                  end: { line: 0, column: 6 }
                }
              ]
            }
        """.trimIndent())
    }

    @Test
    fun response_missingStatus() {
        assertInvalidResponse("""
            { output: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun failureResponse_emptyMessages() {
        assertInvalidResponse("""
            { status: failure, messages: [] }
        """.trimIndent())
    }

    @Test
    fun response_unknownStatus() {
        assertInvalidResponse("""
            { status: pending, output: "datatype: test" }
        """.trimIndent())
    }

    @Test
    fun successResponse_extraProperty() {
        assertInvalidResponse("""
            { status: success, output: "datatype: test", metadata: {} }
        """.trimIndent())
    }

    @Test
    fun parseResponse_messageMissingFields() {
        assertInvalidResponse("""
            {
              status: success
              messages: [
                { message: 'Missing fields' }
              ]
            }
        """.trimIndent())
    }

    @Test
    fun response_withBothOutputAndMessages() {
        // A parse response can have both messages and output (when outputFormat is requested
        // alongside schema validation). This is now a valid parseResponse shape.
        assertValidResponse("""
            { status: success, output: "datatype: test", messages: [] }
        """.trimIndent())
    }

    @Test
    fun response_successWithNeitherOutputNorMessages() {
        assertInvalidResponse("""
            { status: success }
        """.trimIndent())
    }

    companion object {
        // These inline schemas mirror the definitions in kson-service.schema.kson
        // (the canonical protocol schema in resources/). They are split into separate
        // request and response schemas because these serve different validation contexts:
        // servers validate requests, clients validate responses. The resource file's
        // combined schema will be loaded at runtime by the protocol handler.

        /**
         * Schema for KSON service requests. Uses oneOf with const on the operation field
         * to discriminate between request types.
         *
         * Source fields are typed as oneOf string or embed block object (with embedTag
         * constrained to "kson") via the embed block object isomorphism.
         */
        val REQUEST_SCHEMA = """
            {
              '${"$"}schema': 'http://json-schema.org/draft-07/schema#'
              title: 'KSON Service Request'
              description: 'A request to perform an operation on KSON source'
              oneOf: [
                { '${"$"}ref': '#/definitions/formatRequest' }
                { '${"$"}ref': '#/definitions/toJsonRequest' }
                { '${"$"}ref': '#/definitions/toYamlRequest' }
                { '${"$"}ref': '#/definitions/parseRequest' }
              ]
              definitions: {
                formatRequest: {
                  title: 'Format Request'
                  type: object
                  properties: {
                    operation: { const: format }
                    source: { '${"$"}ref': '#/definitions/ksonSource' }
                    options: { '${"$"}ref': '#/definitions/formatOptions' }
                  }
                  required: [ operation, source ]
                  additionalProperties: false
                }
                toJsonRequest: {
                  title: 'Convert to JSON Request'
                  type: object
                  properties: {
                    operation: { const: toJson }
                    source: { '${"$"}ref': '#/definitions/ksonSource' }
                    options: { '${"$"}ref': '#/definitions/transpileOptions' }
                  }
                  required: [ operation, source ]
                  additionalProperties: false
                }
                toYamlRequest: {
                  title: 'Convert to YAML Request'
                  type: object
                  properties: {
                    operation: { const: toYaml }
                    source: { '${"$"}ref': '#/definitions/ksonSource' }
                    options: { '${"$"}ref': '#/definitions/transpileOptions' }
                  }
                  required: [ operation, source ]
                  additionalProperties: false
                }
                parseRequest: {
                  title: 'Parse Request'
                  type: object
                  properties: {
                    operation: { const: parse }
                    source: { '${"$"}ref': '#/definitions/ksonSource' }
                    schema: { '${"$"}ref': '#/definitions/ksonSource' }
                    outputFormat: { type: string, enum: [ json, yaml ] }
                    options: { '${"$"}ref': '#/definitions/transpileOptions' }
                    filepath: { type: string }
                  }
                  required: [ operation, source, schema ]
                  additionalProperties: false
                }
                ksonSource: {
                  title: 'KSON Source'
                  description: 'KSON content as a string or a %kson embed block'
                  oneOf: [
                    { type: string }
                    {
                      type: object
                      description: 'A %kson embed block (embed block object isomorphism)'
                      properties: {
                        embedTag: { const: kson }
                        embedContent: { type: string }
                      }
                      required: [ embedTag, embedContent ]
                      additionalProperties: false
                    }
                  ]
                }
                formatOptions: {
                  title: 'Format Options'
                  type: object
                  properties: {
                    indentSize: { type: integer, minimum: 1, default: 2 }
                    indentType: { type: string, enum: [ spaces, tabs ], default: spaces }
                    formattingStyle: { type: string, enum: [ plain, delimited, compact, classic ], default: plain }
                    embedBlockRules: {
                      type: array
                      items: { '${"$"}ref': '#/definitions/embedBlockRule' }
                    }
                  }
                  additionalProperties: false
                }
                transpileOptions: {
                  title: 'Transpile Options'
                  type: object
                  properties: {
                    retainEmbedTags: { type: boolean, default: true }
                  }
                  additionalProperties: false
                }
                embedBlockRule: {
                  title: 'Embed Block Rule'
                  type: object
                  properties: {
                    pathPattern: { type: string }
                    tag: { type: string }
                  }
                  required: [ pathPattern ]
                  additionalProperties: false
                }
              }
            }
        """.trimIndent()

        /**
         * Schema for KSON service responses. Uses oneOf to discriminate between
         * success (with output), parse result (with messages and optional output),
         * and failure.
         *
         * Output is typed as oneOf string or embed block object (with embedTag as
         * any string, since the tag varies by operation: kson, json, yaml).
         */
        val RESPONSE_SCHEMA = """
            {
              '${"$"}schema': 'http://json-schema.org/draft-07/schema#'
              title: 'KSON Service Response'
              description: 'A response from the KSON service'
              oneOf: [
                { '${"$"}ref': '#/definitions/successResponse' }
                { '${"$"}ref': '#/definitions/parseResponse' }
                { '${"$"}ref': '#/definitions/failureResponse' }
              ]
              definitions: {
                successResponse: {
                  title: 'Success Response'
                  type: object
                  properties: {
                    status: { const: success }
                    output: { '${"$"}ref': '#/definitions/outputContent' }
                  }
                  required: [ status, output ]
                  additionalProperties: false
                }
                parseResponse: {
                  title: 'Parse Response'
                  type: object
                  properties: {
                    status: { const: success }
                    messages: {
                      type: array
                      items: { '${"$"}ref': '#/definitions/message' }
                    }
                    output: { '${"$"}ref': '#/definitions/outputContent' }
                  }
                  required: [ status, messages ]
                  additionalProperties: false
                }
                failureResponse: {
                  title: 'Failure Response'
                  type: object
                  properties: {
                    status: { const: failure }
                    messages: {
                      type: array
                      items: { '${"$"}ref': '#/definitions/message' }
                      minItems: 1
                    }
                  }
                  required: [ status, messages ]
                  additionalProperties: false
                }
                outputContent: {
                  title: 'Output Content'
                  description: 'Operation output as a string or an embed block'
                  oneOf: [
                    { type: string }
                    {
                      type: object
                      description: 'An embed block (embed block object isomorphism)'
                      properties: {
                        embedTag: { type: string }
                        embedContent: { type: string }
                      }
                      required: [ embedContent ]
                      additionalProperties: false
                    }
                  ]
                }
                message: {
                  title: Message
                  type: object
                  properties: {
                    message: { type: string }
                    severity: { type: string, enum: [ error, warning ] }
                    start: { '${"$"}ref': '#/definitions/position' }
                    end: { '${"$"}ref': '#/definitions/position' }
                  }
                  required: [ message, severity, start, end ]
                  additionalProperties: false
                }
                position: {
                  title: Position
                  type: object
                  properties: {
                    line: { type: integer, minimum: 0 }
                    column: { type: integer, minimum: 0 }
                  }
                  required: [ line, column ]
                  additionalProperties: false
                }
              }
            }
        """.trimIndent()
    }
}
