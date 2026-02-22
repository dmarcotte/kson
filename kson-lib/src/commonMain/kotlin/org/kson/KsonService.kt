package org.kson

/**
 * Typed interface for the core KSON operations: format, transpile, and parse.
 *
 * This interface defines the operations available through the KSON service protocol.
 * [Kson] provides the direct, in-process implementation. Future implementations
 * (e.g. RemoteKsonService) will implement this interface by serializing operations
 * as KSON protocol documents.
 *
 * @see Kson
 */
interface KsonService {
    /**
     * Formats KSON source with the specified formatting options.
     *
     * @param kson The KSON source to format
     * @param formatOptions The formatting options to apply
     * @return The formatted KSON source
     */
    fun format(kson: String, formatOptions: FormatOptions = FormatOptions()): String

    /**
     * Converts KSON source to JSON.
     *
     * @param kson The KSON source to convert
     * @param options Options for the JSON transpilation
     * @return A [Result] containing either the JSON output or error messages
     */
    fun toJson(kson: String, options: TranspileOptions.Json = TranspileOptions.Json()): Result

    /**
     * Converts KSON source to YAML.
     *
     * @param kson The KSON source to convert
     * @param options Options for the YAML transpilation
     * @return A [Result] containing either the YAML output or error messages
     */
    fun toYaml(kson: String, options: TranspileOptions.Yaml = TranspileOptions.Yaml()): Result

    /**
     * Parses KSON source and validates it against a JSON Schema, optionally
     * producing JSON or YAML output from the same parse.
     *
     * The source is parsed once; the AST is reused for both schema validation
     * and optional transpilation. Output is always produced when requested,
     * even if there are validation findings (findings are about schema conformance,
     * not parsability).
     *
     * @param kson The KSON source to parse and validate
     * @param schema The JSON Schema source (as KSON or JSON) to validate against
     * @param outputFormat If specified, the parsed kson is also rendered to this format
     * @param retainEmbedTags Whether to retain embed tag/content structure in transpiled output (default true)
     * @param filepath Optional filepath context for the document being validated
     * @return A [ParseResult] distinguishing successful parsing (which may include
     *   validation findings and optional output) from failures (schema or source parse errors)
     */
    fun parse(kson: String, schema: String, outputFormat: OutputFormat? = null, retainEmbedTags: Boolean = true, filepath: String? = null): ParseResult
}
