package com.cargenome.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parseStandardVersions() {
        val v1 = AppVersion.parse("1.0.0")
        assertEquals(1, v1.major)
        assertEquals(0, v1.minor)
        assertEquals(0, v1.patch)

        val v2 = AppVersion.parse("v2.3.4")
        assertEquals(2, v2.major)
        assertEquals(3, v2.minor)
        assertEquals(4, v2.patch)

        val v3 = AppVersion.parse("1.5")
        assertEquals(1, v3.major)
        assertEquals(5, v3.minor)
        assertEquals(0, v3.patch)
    }

    @Test
    fun parseVersionWithSuffix() {
        val ver = AppVersion.parse("1.2.0-debug")
        assertEquals(1, ver.major)
        assertEquals(2, ver.minor)
        assertEquals(0, ver.patch)
        assertEquals("debug", ver.suffix)
    }

    @Test
    fun versionComparison() {
        val v100 = AppVersion.parse("1.0.0")
        val v101 = AppVersion.parse("1.0.1")
        val v110 = AppVersion.parse("1.1.0")
        val v200 = AppVersion.parse("2.0.0")

        assertTrue(v101.isNewerThan(v100))
        assertTrue(v110.isNewerThan(v101))
        assertTrue(v200.isNewerThan(v110))
        assertFalse(v100.isNewerThan(v100))
        assertFalse(v100.isNewerThan(v200))
    }

    @Test
    fun determineMajorUpdate() {
        val current = AppVersion.parse("1.0.0")
        val remote = AppVersion.parse("v2.0.0")
        assertEquals(UpdateType.Major, remote.determineUpdateType(current))
    }

    @Test
    fun determineMinorUpdate() {
        val current = AppVersion.parse("1.0.0")
        val remote = AppVersion.parse("v1.1.0")
        assertEquals(UpdateType.Minor, remote.determineUpdateType(current))

        val current2 = AppVersion.parse("1.0.0-debug")
        val remote2 = AppVersion.parse("1.2.0")
        assertEquals(UpdateType.Minor, remote2.determineUpdateType(current2))
    }

    @Test
    fun determinePatchUpdate() {
        val current = AppVersion.parse("1.0.0")
        val remote = AppVersion.parse("1.0.1")
        assertEquals(UpdateType.Patch, remote.determineUpdateType(current))
    }

    @Test
    fun noUpdateForEqualOrOlderVersion() {
        val current = AppVersion.parse("1.1.0")
        val same = AppVersion.parse("v1.1.0")
        val older = AppVersion.parse("1.0.5")

        assertNull(same.determineUpdateType(current))
        assertNull(older.determineUpdateType(current))
    }
}
