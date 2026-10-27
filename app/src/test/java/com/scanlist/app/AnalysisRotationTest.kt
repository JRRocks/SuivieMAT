package com.scanlist.app

import android.view.Surface
import com.scanlist.app.scanner.AnalysisRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisRotationTest {

    @Test
    fun `telephone tenu droit l analyse reste en portrait`() {
        assertEquals(Surface.ROTATION_0, AnalysisRotation.fromOrientation(0))
        assertEquals(Surface.ROTATION_0, AnalysisRotation.fromOrientation(30))
        assertEquals(Surface.ROTATION_0, AnalysisRotation.fromOrientation(340))
    }

    @Test
    fun `telephone couche vers la droite l analyse tourne d un quart`() {
        assertEquals(Surface.ROTATION_270, AnalysisRotation.fromOrientation(90))
    }

    @Test
    fun `telephone couche vers la gauche l analyse tourne dans l autre sens`() {
        assertEquals(Surface.ROTATION_90, AnalysisRotation.fromOrientation(270))
    }

    @Test
    fun `telephone tete en bas l analyse se retourne`() {
        assertEquals(Surface.ROTATION_180, AnalysisRotation.fromOrientation(180))
    }

    @Test
    fun `les bascules se font a 45 degres de chaque position`() {
        assertEquals(Surface.ROTATION_0, AnalysisRotation.fromOrientation(44))
        assertEquals(Surface.ROTATION_270, AnalysisRotation.fromOrientation(45))
        assertEquals(Surface.ROTATION_270, AnalysisRotation.fromOrientation(134))
        assertEquals(Surface.ROTATION_180, AnalysisRotation.fromOrientation(135))
        assertEquals(Surface.ROTATION_90, AnalysisRotation.fromOrientation(225))
        assertEquals(Surface.ROTATION_90, AnalysisRotation.fromOrientation(314))
        assertEquals(Surface.ROTATION_0, AnalysisRotation.fromOrientation(315))
    }
}
