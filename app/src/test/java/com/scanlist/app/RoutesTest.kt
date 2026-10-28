package com.scanlist.app

import com.scanlist.app.ui.navigation.Routes
import org.junit.Assert.assertFalse
import org.junit.Test

class RoutesTest {

    @Test
    fun `l'ecran d'ouverture ne peut pas etre pris pour l'ecran de scan`() {
        val scanPrefix = Routes.SCAN.substringBefore("{")
        assertFalse(Routes.NEW_SCAN_MODE.startsWith(scanPrefix))
    }
}
