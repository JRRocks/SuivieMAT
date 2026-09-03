package com.scanlist.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entree Hilt de l'application.
 *
 * ScanList fonctionne integralement hors-ligne : base Room locale, modeles ML Kit
 * embarques dans l'APK, aucune permission reseau declaree.
 */
@HiltAndroidApp
class ScanListApplication : Application()
