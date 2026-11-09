package com.scanlist.app

import com.scanlist.app.domain.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun version(text: String): AppVersion =
        AppVersion.parseOrNull(text) ?: error("version illisible : $text")

    @Test
    fun `la version 1_10 est plus recente que la 1_9`() {
        // Une comparaison de chaines dirait l'inverse : c'est le piege a eviter.
        assertTrue(version("1.10") > version("1.9"))
        assertTrue(version("1.9") < version("1.10"))
    }

    @Test
    fun `le premier segment prime sur les suivants`() {
        assertTrue(version("2.0") > version("1.99"))
    }

    @Test
    fun `l etiquette d une release se lit avec ou sans son v`() {
        assertEquals(version("1.12"), version("v1.12"))
        assertEquals(version("1.12"), version("V1.12"))
        assertEquals(version("1.12"), version("  v1.12 \n"))
    }

    @Test
    fun `un segment absent vaut zero`() {
        assertEquals(version("1.9"), version("1.9.0"))
        assertEquals(version("1.9").hashCode(), version("1.9.0").hashCode())
        assertTrue(version("1.9.1") > version("1.9"))
    }

    @Test
    fun `deux versions differentes ne sont pas egales`() {
        assertNotEquals(version("1.11"), version("1.12"))
    }

    @Test
    fun `une etiquette qui n est pas un numero n est pas une version`() {
        assertNull(AppVersion.parseOrNull(null))
        assertNull(AppVersion.parseOrNull(""))
        assertNull(AppVersion.parseOrNull("v"))
        assertNull(AppVersion.parseOrNull("essai"))
        assertNull(AppVersion.parseOrNull("1.12-beta"))
        assertNull(AppVersion.parseOrNull("1..2"))
        assertNull(AppVersion.parseOrNull("1.+2"))
        assertNull(AppVersion.parseOrNull("1.99999999999"))
    }

    @Test
    fun `une version s ecrit comme elle a ete lue`() {
        assertEquals("1.12", version("v1.12").toString())
    }
}
