package com.cleberleonheart.hyperglasskeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun detectsNewerVersions() {
        assertTrue(VersionComparator.isNewer("1.2", "1.1"))
        assertTrue(VersionComparator.isNewer("v1.1.1", "1.1"))
        assertTrue(VersionComparator.isNewer("2.0", "1.99"))
    }

    @Test
    fun rejectsOlderEqualAndInvalidVersions() {
        assertFalse(VersionComparator.isNewer("1.0", "1.1"))
        assertFalse(VersionComparator.isNewer("1.1", "1.1.0"))
        assertFalse(VersionComparator.isNewer("latest", "1.1"))
    }
}
