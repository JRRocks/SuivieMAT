package com.scanlist.app

import com.scanlist.app.domain.update.AppVersion
import com.scanlist.app.domain.update.ReleaseAsset
import com.scanlist.app.domain.update.ReleaseInfo
import com.scanlist.app.domain.update.UpdateOutcome
import com.scanlist.app.domain.update.UpdatePolicy
import com.scanlist.app.domain.update.UpdateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateResolverTest {

    private fun version(text: String) = AppVersion.parseOrNull(text)!!

    private fun apk(name: String) = ReleaseAsset(name, "https://github.com/x/y/releases/download/v/$name", 1L, null)

    private val arm64 = apk("SuivieMAT-1.12-arm64-v8a.apk")
    private val x8664 = apk("SuivieMAT-1.12-x86_64.apk")
    private val universal = apk("SuivieMAT-1.12-universel.apk")

    private val phone = listOf("arm64-v8a", "armeabi-v7a", "armeabi")
    private val emulator = listOf("x86_64", "arm64-v8a")

    private fun release(name: String, vararg assets: ReleaseAsset) = ReleaseInfo(version(name), assets.toList())

    // --------------------------------------------------------------- Proposer ou non

    @Test
    fun `une release plus recente est proposee avec son APK`() {
        val outcome = UpdateResolver.resolve(version("1.11"), release("1.12", arm64), phone)

        assertEquals(UpdateOutcome.Available(version("1.12"), arm64), outcome)
    }

    @Test
    fun `la version deja installee n est pas proposee`() {
        assertEquals(UpdateOutcome.UpToDate, UpdateResolver.resolve(version("1.12"), release("1.12", arm64), phone))
    }

    @Test
    fun `une release plus ancienne que l application n est pas proposee`() {
        // Cas d'un APK installe a la main, en avance sur la derniere release publiee.
        assertEquals(UpdateOutcome.UpToDate, UpdateResolver.resolve(version("1.13"), release("1.12", arm64), phone))
    }

    @Test
    fun `sans release publiee l application est a jour`() {
        assertEquals(UpdateOutcome.UpToDate, UpdateResolver.resolve(version("1.11"), null, phone))
    }

    @Test
    fun `une release sans APK pour cet appareil n est pas proposee`() {
        val intelOnly = listOf("x86_64", "x86")

        assertEquals(UpdateOutcome.UpToDate, UpdateResolver.resolve(version("1.11"), release("1.12", arm64), intelOnly))
        assertEquals(UpdateOutcome.UpToDate, UpdateResolver.resolve(version("1.11"), release("1.12"), phone))
    }

    // ------------------------------------------------------------------ Choix de l'APK

    @Test
    fun `l APK de l architecture de l appareil est prefere a l universel`() {
        assertEquals(arm64, UpdateResolver.pickAsset(listOf(universal, x8664, arm64), phone))
    }

    @Test
    fun `l ordre de preference de l appareil decide entre deux architectures`() {
        assertEquals(x8664, UpdateResolver.pickAsset(listOf(arm64, x8664), emulator))
        // Emulateur capable d'executer de l'arm64 : a defaut de x86_64, il le prend.
        assertEquals(arm64, UpdateResolver.pickAsset(listOf(arm64), emulator))
    }

    @Test
    fun `a defaut d architecture exacte l APK universel est retenu`() {
        assertEquals(universal, UpdateResolver.pickAsset(listOf(universal), phone))
        assertEquals(apk("SuivieMAT-1.12.apk"), UpdateResolver.pickAsset(listOf(apk("SuivieMAT-1.12.apk")), phone))
    }

    @Test
    fun `x86 ne prend pas l APK x86_64`() {
        // "x86" figure dans "x86_64" : seule la fin du nom fait foi.
        assertNull(UpdateResolver.pickAsset(listOf(x8664), listOf("x86")))
        assertEquals(apk("SuivieMAT-1.12-x86.apk"), UpdateResolver.pickAsset(listOf(x8664, apk("SuivieMAT-1.12-x86.apk")), listOf("x86")))
    }

    @Test
    fun `aucun APK ne donne aucun choix`() {
        assertNull(UpdateResolver.pickAsset(emptyList(), phone))
    }

    // ------------------------------------------------------ Cadence de la verification

    @Test
    fun `la premiere verification automatique est due tout de suite`() {
        assertTrue(UpdatePolicy.isAutoCheckDue(lastCheckAt = 0, now = 1_000))
    }

    @Test
    fun `une verification recente n est pas refaite`() {
        val now = 100 * UpdatePolicy.AUTO_CHECK_INTERVAL_MS

        assertFalse(UpdatePolicy.isAutoCheckDue(now - 1, now))
        assertFalse(UpdatePolicy.isAutoCheckDue(now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS + 1, now))
    }

    @Test
    fun `la verification redevient due apres l intervalle`() {
        val now = 100 * UpdatePolicy.AUTO_CHECK_INTERVAL_MS

        assertTrue(UpdatePolicy.isAutoCheckDue(now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS, now))
    }

    @Test
    fun `une derniere verification dans le futur ne bloque pas la suivante`() {
        // Horloge du telephone recalee en arriere entre deux lancements.
        assertTrue(UpdatePolicy.isAutoCheckDue(lastCheckAt = 5_000, now = 1_000))
    }
}
