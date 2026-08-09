package org.kson.validation

import org.kson.AstParseResult
import org.kson.KsonCore
import org.kson.parser.messages.MessageSeverity
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [IndentValidator].
 *
 * Deliberately kept as a single class, large as it is: [IndentValidator] and this file are meant to be readable
 * as a pair, so that everything the indentation rules promise can be understood from this single class/test pair
 */
@Suppress("LargeClass")
class IndentValidatorTest {

    @Test
    fun testValidObjectIndentation() {
        val source = """
            key1: value1
            key2: value2
            key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for aligned properties")
    }

    @Test
    fun testMisalignedObjectProperties() {
        val source = """
            key1: value1
              key2: value2
            key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned property")

        val error = result.messages.first()
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testMultipleMisalignedObjectProperties() {
        val source = """
            key1: value1
              key2: value2
                key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(2, result.messages.size, "Should have two errors for misaligned properties")
    }

    @Test
    fun testValidDashListIndentation() {
        val source = """
            - item1
            - item2
            - item3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for aligned list items")
    }

    @Test
    fun testMisalignedDashListItems() {
        val source = """
            - item1
              - item2
            - item3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned list item")

        val error = result.messages.first()
        assertEquals(PLAIN_LIST_ELEMENTS_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedObjectsWithValidIndentation() {
        val source = """
            outer1:
              inner1: value1
              inner2: value2
              .
            outer2:
              inner3: value3
              inner4: value4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for properly nested objects")
    }

    @Test
    fun testNestedObjectsWithMisalignedFirstInnerProperties() {
        val source = """
            outer1:
              inner1: value1
                inner2: value2
              .
            outer2: value2
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for the misaligned first inner property")

        val error = result.messages.first()
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedListWithMisalignedFirstInnerProperties() {
        val source = """
            -
              - inner1
                - inner2
              =
            - outer1
            - outer2
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned first inner list")

        val error = result.messages.first()
        assertEquals(PLAIN_LIST_ELEMENTS_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedListsWithValidIndentation() {
        val source = """
            - 
              - inner1
              - inner2
              =
            - 
              - inner3
              - inner4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for properly nested lists")
    }

    @Test
    fun testMixedObjectAndListNesting() {
        val source = """
            key1:
              - item1
              - item2
            key2:
              nested:
                - item3
                - item4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for properly mixed nesting")

        val badSource = """
            - key:
                - x
                 # deceptive indentation: sibling of `key:`
                 key_sibling: y
        """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, badResult.messages[0].message.type)
    }

    @Test
    fun testValidateSameLineConstructs() {
        val message = "Should have no errors for one-line constructs," +
                "provided leading items are aligned"

        assertTrue(
            KsonCore.parseToAst(
                """
                    key1: value1 key2: value2
                    key3: value3 key4: value4
                """.trimIndent()
            )
                .messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - - 1
                      - 2
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        val misalignedSource = """
              key1: value1 key2: value2
            key3: value3 key4: value4
        """.trimIndent()

        val misalignedResult = KsonCore.parseToAst(misalignedSource)
        assertEquals(
            1, misalignedResult.messages.size, "Should have an error the mis-aligned " +
                    "one-line constructs"
        )

        val error = misalignedResult.messages.first()
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testValidateLeadingAlignment1() {
        val source = """
            key1: - 1 - 2
                  - 3
                  - 4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(
            result.messages.isEmpty(), "Should have no errors provided leading alignment is correct " +
                    "for all entries"
        )

        val misalignedSource = """
            key1: - 1 - 2
                  - 3
                    - 4
        """.trimIndent()

        val misalignedResult = KsonCore.parseToAst(misalignedSource)
        assertEquals(
            1, misalignedResult.messages.size, "Should have an error the mis-aligned " +
                    "end of this list"
        )

        val error = misalignedResult.messages.first()
        assertEquals(PLAIN_LIST_ELEMENTS_MISALIGNED, error.message.type)
    }

    @Test
    fun testValidateLeadingAlignment2() {
        val source = """
            key1: - 1 - 2
                  - 3
                  - 4 key2: w key3: x
            key4: y
            key5: z
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(
            result.messages.isEmpty(), "Should have no errors provided leading alignment is correct " +
                    "for all entries"
        )

        val misalignedSource = """
           - 1 - 2
           - 3
           - key2: w key3: x
                key4: y
                    key5: z
                key6: a
        """.trimIndent()

        val misalignedResult = KsonCore.parseToAst(misalignedSource)
        assertEquals(
            3, misalignedResult.messages.size, "Should have an error the mis-aligned " +
                    "end of this object"
        )

        val errors = misalignedResult.messages
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, errors[0].message.type)
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, errors[1].message.type)
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, errors[2].message.type)
    }

    @Test
    fun testEmptyObjectAndList() {
        val source = """
            empty_object: {}
            empty_list: []
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for empty structures")
    }

    @Test
    fun testSinglePropertyObject() {
        val source = """
            single: value
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for single property object")
    }

    @Test
    fun testDelimitedObjectsAlsoChecked() {
        // Delimited objects should also be checked for alignment
        val source = """
            {
              key1: value1
                key2: value2
              key3: value3
            }
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned property in delimited object")

        val error = result.messages.first()
        assertEquals(DELIMITED_OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedInSingleElementObject() {
        val source = """
            person:
              favorite_books: 10
                    test: ex
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size)

        val error = result.messages.first()
        assertEquals(PLAIN_OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedDashedList() {
        val badSource = """
                - -
                  - 1
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)

        val error = badResult.messages.first()
        assertEquals(PLAIN_LIST_ELEMENT_NESTING_ISSUE, error.message.type)
    }

    /**
     * Regression test for a special case where delimited items hanging off a list dash were tripping up
     * our alignment detection
     */
    @Test
    fun testAlignmentWithDelimiters() {
        val message = "Should have no errors for things misaligned by their opening delimiter"

        assertTrue(
            KsonCore.parseToAst(
                """
                    {one:1
                     two:2}
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    {one:1
                     two:2}
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    <- 1
                     - 2>
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - # object hanging off dash
                     {key1: x
                      # this should not be considered mis-aligned
                      key2: y}
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - # list hanging off dash
                     [x
                      # this should not be considered mis-aligned
                      y]
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - # list hanging off dash
                     < - x
                       # this should not be considered mis-aligned
                       - y>
                """.trimIndent()
            ).messages.isEmpty(), message
        )
    }

    @Test
    fun testObjectNestedInBracketList() {
        assertTrue(
            KsonCore.parseToAst(
                "[key: 1]"
            ).messages.isEmpty()
        )
    }

    @Test
    fun testNonLeadingMultilineStrutures() {
        /**
         * Regression test for a bug in the validator where alignment tracking was incorrect in the case that
         * a non-leading item spanned multiple lines, like the `non_leading:` property in this test.  In this
         * case, we were incorrectly marking `should_not_error:` as a deceptive indent
         */
        assertTrue(
            KsonCore.parseToAst(
                """
                    x:y non_leading:{
                    } should_not_error: 0
                """.trimIndent()
            ).messages.isEmpty(),
            "should never consider a non-leading item on a line to mis-aligned"
        )
    }

    @Test
    fun testDeceptivelyAlignedSubObject() {
        val badSource = """
                key:
                   nested1: 80
                   nested2: 80000 nested3: 10000
                   nested4: 12000 nested5:
                   doubleNested: 14000
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)

        val error = badResult.messages.first()
        assertEquals(PLAIN_OBJECT_PROPERTY_NESTING_ISSUE, error.message.type)

        val goodSource = """
                key:
                   nested1: 80
                   nested2: 80000 nested3: 10000
                   nested4: 12000 nested5:
                                    doubleNested: 14000
            """.trimIndent()
        val goodResult = KsonCore.parseToAst(goodSource)
        assertEquals(0, goodResult.messages.size)
    }

    @Test
    fun testDeceptivelyAlignedSubList() {
        val badSource = """
                ports:
                   - 80
                   - 8000 - 10000
                   - 12000 -
                   - 14000
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)

        val error = badResult.messages.first()
        assertEquals(PLAIN_LIST_ELEMENT_NESTING_ISSUE, error.message.type)

        val goodSource = """
                ports:
                   - 80
                   - 8000 - 10000
                   - 12000 -
                             - 14000
            """.trimIndent()
        val goodResult = KsonCore.parseToAst(goodSource)
        assertEquals(0, goodResult.messages.size)
    }

    @Test
    fun testMixedSubListsAndObjects() {
        val badSource = """
                deceptive_list:
                  - 1
                  - key:
                  # deceptive indent: nested under `key:`
                  - 9
                # deceptive indent: must be aligned `key:`
                # deceptive indent: nested under list with `key:`
                deceptive_object:
                    key: x
                    list: - 
                    # deceptive indent: nested under `list: - ` list
                    key: x
            """.trimIndent()

        // pinned with the (zero-based) line each message lands on, since a document deceptive in several ways
        // at once is exactly where it matters that every deception is named against the right entry: each of
        // these four answers one of the "deceptive indent" comments in the source above
        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(
            listOf(
                PLAIN_OBJECT_PROPERTIES_MISALIGNED to 7,
                PLAIN_LIST_ELEMENT_NESTING_ISSUE to 7,
                PLAIN_OBJECT_PROPERTY_NESTING_ISSUE to 4,
                PLAIN_LIST_ELEMENT_NESTING_ISSUE to 11
            ),
            badResult.messages.map { it.message.type to it.location.start.line }
        )

        val goodSource = """
                honest_list:
                  - 1
                  - key:
                      - 9
                    honest_object:
                      key: x
                      list: - 
                             key: x
            """.trimIndent()
        val goodResult = KsonCore.parseToAst(goodSource)
        assertEquals(0, goodResult.messages.size)
    }

    @Test
    fun testSimpleListValueNesting() {
        val badSource = """
                    -
                bad_nest
                    -
                   also_bad
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(2, badResult.messages.size)

        val errors = badResult.messages
        assertEquals(PLAIN_LIST_ELEMENT_NESTING_ISSUE, errors[0].message.type)
        assertEquals(PLAIN_LIST_ELEMENT_NESTING_ISSUE, errors[1].message.type)
    }

    @Test
    fun testMisalignedObjectPropertySeverity() {
        assertDeceptiveIndentIsError(
            """
                key1: value1
                  key2: value2
            """.trimIndent(),
            PLAIN_OBJECT_PROPERTIES_MISALIGNED
        )

        assertDeceptiveIndentIsWarning(
            """
                {key1: value1
                   key2: value2}
            """.trimIndent(),
            DELIMITED_OBJECT_PROPERTIES_MISALIGNED
        )
    }

    @Test
    fun testMisalignedListElementSeverity() {
        assertDeceptiveIndentIsError(
            """
                - item1
                  - item2
            """.trimIndent(),
            PLAIN_LIST_ELEMENTS_MISALIGNED
        )

        assertDeceptiveIndentIsWarning(
            """
                [item1
                   item2]
            """.trimIndent(),
            DELIMITED_LIST_ELEMENTS_MISALIGNED
        )
    }

    @Test
    fun testUnderNestedObjectPropertySeverity() {
        assertDeceptiveIndentIsError(
            """
                key: nested:
                value: 1
            """.trimIndent(),
            PLAIN_OBJECT_PROPERTY_NESTING_ISSUE
        )

        assertDeceptiveIndentIsWarning(
            """
                {key:
                {value: 1}}
            """.trimIndent(),
            DELIMITED_OBJECT_PROPERTY_NESTING_ISSUE
        )
    }

    /**
     * A delimited container's delimiters mark its bounds, so it is the container itself that must sit deep
     * enough.  Where its entries land between those delimiters cannot mislead a reader about what holds them.
     */
    @Test
    fun testDelimitersAnswerForTheirOwnBounds() {
        assertTrue(
            KsonCore.parseToAst(
                """
                    key: {
                    value: 1}
                """.trimIndent()
            ).messages.isEmpty(),
            "the `{` sits deeper than `key`, and the braces show which key this property hangs off"
        )

        assertDeceptiveIndentIsError(
            """
                key:
                {value: 1}
            """.trimIndent(),
            PLAIN_OBJECT_PROPERTY_NESTING_ISSUE,
            "here the object itself is the thing indented as though it were not `key`'s value"
        )
    }

    @Test
    fun testUnderNestedListElementSeverity() {
        assertDeceptiveIndentIsError(
            """
                  -
                value
            """.trimIndent(),
            PLAIN_LIST_ELEMENT_NESTING_ISSUE
        )

        assertDeceptiveIndentIsWarning(
            """
                <  -
                value>
            """.trimIndent(),
            DELIMITED_LIST_ELEMENT_NESTING_ISSUE
        )
    }

    /**
     * A nesting requirement always comes from the container the under-nested value hangs off, so that container
     * decides the message we report---both what it says and how severely.  The style of whatever the value
     * happens to be made of does not enter into it.
     */
    @Test
    fun testUnderNestedValueIsJudgedByTheContainerItHangsOff() {
        assertDeceptiveIndentIsWarning(
            """
                <-
                x: 1>
            """.trimIndent(),
            DELIMITED_LIST_ELEMENT_NESTING_ISSUE,
            "the `<>` delimiters show a reader where the list this plain object hangs off ends"
        )

        assertDeceptiveIndentIsError(
            """
                -
                {
                x: 1}
            """.trimIndent(),
            PLAIN_LIST_ELEMENT_NESTING_ISSUE,
            "nothing marks where the dash list this delimited object hangs off ends"
        )
    }

    @Test
    fun testJsonIndentationIsNeverAnError() {
        val result = KsonCore.parseToAst(
            """
                {
                  "a": 1,
                    "b": [2,
                  3]
                }
            """.trimIndent()
        )

        assertTrue(
            result.messages.isNotEmpty(),
            "this Json is untidily indented, and we should say so"
        )
        assertFalse(
            result.hasErrors(),
            "Kson accepts every Json document, and Json is free to be indented however it likes"
        )
    }

    /**
     * Assert [source] logs exactly the [expectedMessageType] deceptive indent, and that it stops [source]
     * describing a value at all: in plain syntax the indentation is the only picture of the structure a reader
     * gets, so a document indented to say something other than what it means is not a document we can compile
     */
    private fun assertDeceptiveIndentIsError(
        source: String,
        expectedMessageType: MessageType,
        message: String? = null
    ) {
        val result = assertSingleDeceptiveIndent(source, expectedMessageType, message)
        assertEquals(MessageSeverity.ERROR, expectedMessageType.severity)
        assertTrue(result.hasErrors(), "a deceptive indent in plain syntax must stop this document compiling")
    }

    /**
     * Assert [source] logs exactly the [expectedMessageType] deceptive indent, and that it still describes a
     * value: the delimiters here describe the structure whatever the indentation does, so this is untidiness
     * rather than deception
     */
    private fun assertDeceptiveIndentIsWarning(
        source: String,
        expectedMessageType: MessageType,
        message: String? = null
    ) {
        val result = assertSingleDeceptiveIndent(source, expectedMessageType, message)
        assertEquals(MessageSeverity.WARNING, expectedMessageType.severity)
        assertFalse(result.hasErrors(), "a deceptive indent inside delimiters must leave this document compiling")
    }

    private fun assertSingleDeceptiveIndent(
        source: String,
        expectedMessageType: MessageType,
        message: String? = null
    ): AstParseResult {
        val result = KsonCore.parseToAst(source)
        assertEquals(
            listOf(expectedMessageType), result.messages.map { it.message.type },
            (message?.let { "$it\n" } ?: "") + "expected exactly one deceptive indent message from:\n$source"
        )
        return result
    }
}
