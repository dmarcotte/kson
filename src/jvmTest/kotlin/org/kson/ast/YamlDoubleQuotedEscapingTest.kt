package org.kson.ast

import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies [renderForYamlDoubleQuotedString] by parsing its output with a real YAML parser: the parsed scalar must be
 * exactly the string we rendered
 */
class YamlDoubleQuotedEscapingTest {
    private fun assertYamlRoundTrips(content: String) {
        val yaml = "\"${renderForYamlDoubleQuotedString(content)}\""
        assertEquals(content, Yaml().load<String>(yaml), "Rendered YAML was: $yaml")
    }

    @Test
    fun testLineBreaksAreNotFolded() {
        assertYamlRoundTrips("a\nb")
        assertYamlRoundTrips("a\r\nb")
        assertYamlRoundTrips("a\rb")
        assertYamlRoundTrips("trailing whitespace \t\n\tleading whitespace")
        assertYamlRoundTrips("\n\n")
    }

    @Test
    fun testYaml11NextLineIsNotFolded() {
        assertYamlRoundTrips("next\u0085line")
    }

    @Test
    fun testQuotesAndBackslashes() {
        assertYamlRoundTrips("\"quoted\" and \\backslashed\\")
        assertYamlRoundTrips("\\n is not a newline")
    }

    @Test
    fun testNonPrintableCharacters() {
        assertYamlRoundTrips("\u0000\u0001\u001F\u007F\u0080\u009F")
        assertYamlRoundTrips("\uFFFE\uFFFF")
    }

    @Test
    fun testCharactersOutsideBasicMultilingualPlane() {
        assertYamlRoundTrips("\uD83D\uDE00")
    }
}
