package com.zuoguan.bilimusickmp.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [formatReleaseNotes] 的规整规则。
 *
 * 重点是 CRLF：GitHub 的 Release body 用的是 `\r\n`，Markdown 解析器会把 `\r` 当正文、
 * `\n\r` 当行尾，空行因此不再被识别成段落分隔，末尾的 HTML 脚注会被并进上一条列表项那一行。
 */
class ReleaseNotesFormatTest {

    @Test
    fun crlfIsNormalizedToLf() {
        assertEquals("a\n\n<b>x</b>", formatReleaseNotes("a\r\n\r\n<b>x</b>"))
    }

    @Test
    fun loneCarriageReturnIsNormalized() {
        assertEquals("a\nb", formatReleaseNotes("a\rb"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("a\nb", formatReleaseNotes("\r\n  a\r\nb  \r\n"))
    }

    @Test
    fun blankBodyBecomesNull() {
        assertNull(formatReleaseNotes(null))
        assertNull(formatReleaseNotes(""))
        assertNull(formatReleaseNotes("   \r\n\r\n "))
    }

    @Test
    fun longNotesAreNotTruncated() {
        val long = buildString { repeat(200) { append("第 $it 行\n") } }
        assertEquals(long.trim(), formatReleaseNotes(long))
    }
}
