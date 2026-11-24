package org.kson

import org.kson.parser.messages.MessageType.*
import kotlin.test.Test

class KsonCoreTestEmbedBlockError : KsonCoreTestError {
    /**
     * Regression test for a parsing problem we had at the boundary:
     * this was blowing up with an index out of bounds rather than
     * parsing with an [EMBED_BLOCK_NO_CLOSE] error as it should
     */
    @Test
    fun testUnclosedEmbedWithEscape() {
        assertParserRejectsSource(
            """
                %%
                %\%
            """.trimIndent(),
            listOf(EMBED_BLOCK_NO_CLOSE)
        )
    }

    @Test
    fun testUnclosedEmbedDelimiterError() {
        assertParserRejectsSource("%%\n", listOf(EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testUnclosedEmbedAlternateDelimiterError() {
        assertParserRejectsSource("$\n", listOf(EMBED_BLOCK_NO_CLOSE))
    }

    @Test
    fun testEmbedTagBadEscape() {
        assertParserRejectsSource(
            """
                %my\xtag
                content%%
            """.trimIndent(),
            listOf(STRING_BAD_ESCAPE)
        )
    }

    @Test
    fun testEmbedTagBadQuoteEscape() {
        // An embed tag is not delimited by quotes, so it may not escape either quote
        assertParserRejectsSource(
            """
                %my\'tag
                content%%
            """.trimIndent(),
            listOf(STRING_BAD_QUOTE_ESCAPE)
        )
        assertParserRejectsSource(
            """
                %my\"tag
                content%%
            """.trimIndent(),
            listOf(STRING_BAD_QUOTE_ESCAPE)
        )
    }

    @Test
    fun testEmbedTagBadUnicodeEscape() {
        assertParserRejectsSource(
            """
                %\u12
                content%%
            """.trimIndent(),
            listOf(STRING_BAD_UNICODE_ESCAPE)
        )
    }

    @Test
    fun testEmbedTagControlCharacter() {
        // Control character (0x01) in embed tag
        assertParserRejectsSource(
            "%my\u0001tag\ncontent%%",
            listOf(STRING_CONTROL_CHARACTER)
        )

        // An embed tag is a KSON string, so a raw tab must be escaped there too
        assertParserRejectsSource(
            "%my\ttag\ncontent%%",
            listOf(STRING_CONTROL_CHARACTER)
        )
    }

    @Test
    fun testEmbedBlockObjectValidatesItsStrings() {
        assertParserRejectsSource("embedTag: x\nembedContent: 'a\\xb'", listOf(STRING_BAD_ESCAPE))
        assertParserRejectsSource("embedTag: x\nembedContent: 'a\u0001b'", listOf(STRING_CONTROL_CHARACTER))
        assertParserRejectsSource("embedTag: x\nembedContent: 'line1\nline2'", listOf(STRING_CONTROL_CHARACTER))
        assertParserRejectsSource("embedTag: x\nembedContent: 'a\tb'", listOf(STRING_CONTROL_CHARACTER))
        assertParserRejectsSource("embedTag: 'my\ttag'\nembedContent: content", listOf(STRING_CONTROL_CHARACTER))
    }

    @Test
    fun testEmbedBlockPartialDelim() {
        assertParserRejectsSource(
            """
                %
            """,
            listOf(EMBED_BLOCK_NO_CLOSE)
        )

        assertParserRejectsSource(
            """
                $
            """,
            listOf(EMBED_BLOCK_NO_CLOSE)
        )
    }
}
