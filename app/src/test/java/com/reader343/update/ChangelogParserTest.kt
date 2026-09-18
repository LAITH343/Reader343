package com.reader343.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogParserTest {

    @Test
    fun `parses typed entries`() {
        val body = """
            ## What's new
            - New: Weekly goals — Set a weekly target alongside the daily one.
            - Improved: Faster page rendering — Large PDFs open about 40% quicker.
            - Fixed: Streak rollover at midnight — Sessions crossing midnight no longer count twice.
        """.trimIndent()
        assertEquals(
            listOf(
                ChangeEntry(ChangeKind.New, "Weekly goals", "Set a weekly target alongside the daily one."),
                ChangeEntry(ChangeKind.Improved, "Faster page rendering", "Large PDFs open about 40% quicker."),
                ChangeEntry(ChangeKind.Fixed, "Streak rollover at midnight", "Sessions crossing midnight no longer count twice."),
            ),
            ChangelogParser.parse(body),
        )
    }

    @Test
    fun `other bullets become untyped entries`() {
        val body = "## What's new\n- Smaller download size\n- Changed: Something — else\n- New: No separator here"
        assertEquals(
            listOf(
                ChangeEntry(null, null, "Smaller download size"),
                ChangeEntry(null, null, "Changed: Something — else"),
                ChangeEntry(null, null, "New: No separator here"),
            ),
            ChangelogParser.parse(body),
        )
    }

    @Test
    fun `missing heading gives no changelog`() {
        assertNull(ChangelogParser.parse("- New: Weekly goals — Set a weekly target."))
        assertNull(ChangelogParser.parse(""))
        assertNull(ChangelogParser.parse("### What's new\n- New: A — B"))
    }

    @Test
    fun `ignores content outside the heading`() {
        val body = """
            Intro text
            - Not part of it

            ## What's new
            - New: Search — Find text in a book.

            ## Checksums
            - sha256: abc
        """.trimIndent()
        assertEquals(listOf(ChangeEntry(ChangeKind.New, "Search", "Find text in a book.")), ChangelogParser.parse(body))
    }

    @Test
    fun `tolerates odd dashes and spacing`() {
        val body = "##   What’s New  \r\n" +
            "-   new:Weekly goals   –   Set a target.\r\n" +
            "* Improved :  Rendering -- Faster.\r\n" +
            "- Fixed: Pinch-zoom crash - No more crashes.\r\n" +
            "-Not a bullet\r\n"
        assertEquals(
            listOf(
                ChangeEntry(ChangeKind.New, "Weekly goals", "Set a target."),
                ChangeEntry(ChangeKind.Improved, "Rendering", "Faster."),
                ChangeEntry(ChangeKind.Fixed, "Pinch-zoom crash", "No more crashes."),
            ),
            ChangelogParser.parse(body),
        )
    }

    @Test
    fun `joins continuation lines`() {
        val body = "## What's new\n- New: Search — Find text\n  across the whole book."
        assertEquals(
            listOf(ChangeEntry(ChangeKind.New, "Search", "Find text across the whole book.")),
            ChangelogParser.parse(body),
        )
    }

    @Test
    fun `heading without bullets gives empty list`() {
        assertEquals(emptyList<ChangeEntry>(), ChangelogParser.parse("## What's new\n\nNothing listed."))
    }
}
