package com.zuoguan.bilimusickmp.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [isNewerVersion] 的数值比较、tag 前缀与预发布后缀行为。 */
class VersionComparatorTest {

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(isNewerVersion("1.4.0", "1.4.0"))
        assertFalse(isNewerVersion("v1.4.0", "1.4.0"))
    }

    @Test
    fun tagPrefixIsIgnored() {
        assertTrue(isNewerVersion("v1.5.0", "1.4.0"))
        assertTrue(isNewerVersion("V1.5.0", "1.4.0"))
        assertFalse(isNewerVersion("v1.4.0", "1.4.0"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertFalse(isNewerVersion("1.4", "1.4.0"))
        assertTrue(isNewerVersion("1.4.1", "1.4"))
        assertFalse(isNewerVersion("1.4", "1.4.1"))
    }

    @Test
    fun segmentsCompareAsIntegersNotText() {
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
        assertTrue(isNewerVersion("1.4.10", "1.4.9"))
    }

    @Test
    fun releaseOutranksPrerelease() {
        assertTrue(isNewerVersion("1.5.0", "1.5.0-beta.1"))
        assertFalse(isNewerVersion("1.5.0-beta.1", "1.5.0"))
        assertTrue(isNewerVersion("1.5.0-beta.2", "1.5.0-beta.1"))
    }

    @Test
    fun malformedInputFallsBackToNotNewer() {
        assertFalse(isNewerVersion("", "1.4.0"))
        assertFalse(isNewerVersion("abc", "1.4.0"))
        assertTrue(isNewerVersion("1.5.0", ""))
    }

    @Test
    fun compareResultSignMatchesOrdering() {
        assertTrue(compareVersions("1.5.0", "1.4.0") > 0)
        assertEquals(0, compareVersions("v1.4.0", "1.4.0"))
        assertTrue(compareVersions("1.4.0", "1.5.0") < 0)
    }
}
