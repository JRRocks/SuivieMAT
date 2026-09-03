package com.scanlist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.scanlist.app.ui.common.LocalSnackbarHostState
import com.scanlist.app.ui.navigation.ScanListNavHost
import com.scanlist.app.ui.theme.ScanListTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity unique de l'application : toute la navigation se fait en Compose.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ScanListTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    ScanListNavHost()
                }
            }
        }
    }
}
