package org.kson.service

import org.kson.*
import org.kson.ast.renderForJsonString
import org.kson.parser.NumberParser
import org.kson.value.EmbedBlock
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString

/**
 * Parses KSON request documents, dispatches to a [KsonService], and returns
 * KSON response documents.
 *
 * This is the bridge between the document protocol (defined by
 * `kson-service.schema.kson`) and the typed [KsonService] API. It is a *client*
 * of [KsonService], not part of it --- keeping them separate means a future
 * `RemoteKsonService` can implement [KsonService] by building request documents
 * and sending them to a remote handler.
 */
class KsonProtocolHandler(private val service: KsonService) {

    /**
     * Executes a KSON service request and returns a KSON response document.
     */
    fun execute(request: String): String {
        val parseResult = KsonCore.parseToAst(request)
        if (parseResult.hasErrors()) {
            val messages = publishMessages(parseResult.messages)
            return failureResponse(messages)
        }

        val root = parseResult.ksonValue
        if (root !is KsonObject) {
            return failureResponse("Request must be a KSON object")
        }

        val operation = extractString(root, "operation")
            ?: return failureResponse("Missing required field: operation")

        val source = extractStringOrEmbed(root, "source")
            ?: return failureResponse("Missing required field: source")

        return when (operation) {
            "format" -> executeFormat(source, root)
            "toJson" -> executeToJson(source, root)
            "toYaml" -> executeToYaml(source, root)
            "parse" -> executeParse(source, root)
            else -> failureResponse("Unknown operation: $operation")
        }
    }

    private fun executeFormat(source: String, root: KsonObject): String {
        val formatOptions = extractFormatOptions(root)
        val output = service.format(source, formatOptions)
        return successResponse(output)
    }

    private fun executeToJson(source: String, root: KsonObject): String {
        val retainEmbedTags = extractRetainEmbedTags(root)
        return when (val result = service.toJson(source, TranspileOptions.Json(retainEmbedTags = retainEmbedTags))) {
            is Result.Success -> successResponse(result.output)
            is Result.Failure -> failureResponse(result.errors)
        }
    }

    private fun executeToYaml(source: String, root: KsonObject): String {
        val retainEmbedTags = extractRetainEmbedTags(root)
        return when (val result = service.toYaml(source, TranspileOptions.Yaml(retainEmbedTags = retainEmbedTags))) {
            is Result.Success -> successResponse(result.output)
            is Result.Failure -> failureResponse(result.errors)
        }
    }

    private fun executeParse(source: String, root: KsonObject): String {
        val schema = extractStringOrEmbed(root, "schema")
            ?: return failureResponse("Missing required field: schema")
        val extracted = extractOutputFormat(root)
        if (extracted.error != null) return extracted.error
        val retainEmbedTags = extractRetainEmbedTags(root)
        val filepath = extractString(root, "filepath")

        return when (val result = service.parse(source, schema, extracted.format, retainEmbedTags, filepath)) {
            is ParseResult.Success -> parseResponse(result.messages, result.output)
            is ParseResult.Failure -> failureResponse(result.errors)
        }
    }

    /**
     * Extracts a string value from a [KsonObject] property.
     * Returns null if the property is missing or not a string.
     */
    private fun extractString(obj: KsonObject, key: String): String? {
        val value = obj.propertyLookup[key] ?: return null
        return (value as? KsonString)?.value
    }

    /**
     * Extracts a field that may be a [KsonString] or an [EmbedBlock], returning
     * its string content. Used for `source` and `schema` fields in the protocol.
     */
    private fun extractStringOrEmbed(obj: KsonObject, key: String): String? {
        return when (val value = obj.propertyLookup[key]) {
            is KsonString -> value.value
            is EmbedBlock -> value.embedContent.value
            else -> null
        }
    }

    private fun extractFormatOptions(root: KsonObject): FormatOptions {
        val optionsObj = root.propertyLookup["options"] as? KsonObject
            ?: return FormatOptions()

        val indentSize = extractInteger(optionsObj, "indentSize")
        val indentTypeStr = extractString(optionsObj, "indentType")
        val formattingStyleStr = extractString(optionsObj, "formattingStyle")
        val embedBlockRules = extractEmbedBlockRules(optionsObj)

        val indentType = when (indentTypeStr) {
            "tabs" -> IndentType.Tabs
            else -> IndentType.Spaces(indentSize?.toInt() ?: 2)
        }

        val formattingStyle = when (formattingStyleStr) {
            "plain" -> FormattingStyle.PLAIN
            "delimited" -> FormattingStyle.DELIMITED
            "compact" -> FormattingStyle.COMPACT
            "classic" -> FormattingStyle.CLASSIC
            else -> FormattingStyle.PLAIN
        }

        return FormatOptions(
            indentType = indentType,
            formattingStyle = formattingStyle,
            embedBlockRules = embedBlockRules
        )
    }

    /**
     * Extracts the `retainEmbedTags` boolean from transpile options, defaulting to `true`.
     */
    private fun extractRetainEmbedTags(root: KsonObject): Boolean {
        val optionsObj = root.propertyLookup["options"] as? KsonObject
            ?: return true
        return extractBoolean(optionsObj, "retainEmbedTags") ?: true
    }

    /**
     * Extracts the optional `outputFormat` field from a parse request.
     * Returns null if absent, or the corresponding [OutputFormat] enum value.
     * Returns a failure response string if the value is present but unrecognized.
     */
    private fun extractOutputFormat(root: KsonObject): ExtractedOutputFormat {
        val formatStr = extractString(root, "outputFormat")
            ?: return ExtractedOutputFormat(null)
        return when (formatStr) {
            "json" -> ExtractedOutputFormat(OutputFormat.JSON)
            "yaml" -> ExtractedOutputFormat(OutputFormat.YAML)
            else -> ExtractedOutputFormat(null, failureResponse("Unknown outputFormat: $formatStr"))
        }
    }

    /**
     * Result of extracting an [OutputFormat] from a request. If [error] is non-null,
     * the extraction failed and [error] is the response to return.
     */
    private class ExtractedOutputFormat(val format: OutputFormat?, val error: String? = null)

    private fun extractBoolean(obj: KsonObject, key: String): Boolean? {
        val value = obj.propertyLookup[key] ?: return null
        return (value as? KsonBoolean)?.value
    }

    private fun extractEmbedBlockRules(optionsObj: KsonObject): List<EmbedRule> {
        val rulesValue = optionsObj.propertyLookup["embedBlockRules"] as? KsonList
            ?: return emptyList()

        return rulesValue.elements.mapNotNull { element ->
            val ruleObj = element as? KsonObject ?: return@mapNotNull null
            val pathPattern = extractString(ruleObj, "pathPattern") ?: return@mapNotNull null
            val tag = extractString(ruleObj, "tag")
            val result = EmbedRule.fromPathPattern(pathPattern, tag)
            (result as? EmbedRuleResult.Success)?.embedRule
        }
    }

    /**
     * Extracts an integer value from a [KsonObject] property.
     * Returns null if the property is missing or not an integer.
     */
    private fun extractInteger(obj: KsonObject, key: String): Long? {
        val value = obj.propertyLookup[key] ?: return null
        val number = value as? KsonNumber ?: return null
        val parsed = number.value
        return (parsed as? NumberParser.ParsedNumber.Integer)?.value
    }

    /**
     * Builds a success response with output content.
     */
    private fun successResponse(output: String): String {
        return """{ status: success, output: "${renderForJsonString(output)}" }"""
    }

    /**
     * Builds a parse response with a messages array and optional output.
     */
    private fun parseResponse(messages: List<Message>, output: String?): String {
        val messagesField = "messages: [${renderMessages(messages)}]"
        val outputField = if (output != null) """, output: "${renderForJsonString(output)}"""" else ""
        return "{ status: success, $messagesField$outputField }"
    }

    /**
     * Builds a failure response from a list of [Message]s.
     */
    private fun failureResponse(messages: List<Message>): String {
        return "{ status: failure, messages: [${renderMessages(messages)}] }"
    }

    /**
     * Builds a failure response from a single error message.
     */
    private fun failureResponse(errorMessage: String): String {
        return """{ status: failure, messages: [{ message: "${renderForJsonString(errorMessage)}", severity: error, start: { line: 0, column: 0 }, end: { line: 0, column: 0 } }] }"""
    }

    private fun renderMessages(messages: List<Message>): String {
        return messages.joinToString(", ") { message ->
            val severity = when (message.severity) {
                MessageSeverity.ERROR -> "error"
                MessageSeverity.WARNING -> "warning"
            }
            "{ message: \"${renderForJsonString(message.message)}\", severity: $severity, start: { line: ${message.start.line}, column: ${message.start.column} }, end: { line: ${message.end.line}, column: ${message.end.column} } }"
        }
    }
}
