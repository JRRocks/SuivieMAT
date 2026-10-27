package com.scanlist.app.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scanlist.app.R
import com.scanlist.app.ui.common.PrimaryButton

/**
 * Affiche [content] une fois la permission camera accordee.
 *
 * La demande part automatiquement au premier affichage : l'utilisateur a deja choisi
 * son mode de scan, lui imposer un bouton supplementaire serait un clic de trop. Le
 * bouton n'apparait qu'apres un refus.
 *
 * Apres deux refus, Android n'affiche plus sa boite de dialogue : relancer la demande
 * ne ferait rien. Le bouton envoie alors vers les reglages de l'application, et la
 * permission est relue au retour (ON_RESUME), sans quoi l'ecran resterait bloque sur
 * le refus alors que l'acces vient d'etre accorde.
 */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var requested by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        requested = true
        // Refus sans justification a montrer : c'est le systeme qui ne demande plus.
        blocked = !result && (context as? Activity)?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    LifecycleResumeEffect(Unit) {
        if (!granted && context.hasCameraPermission()) granted = true
        onPauseOrDispose { }
    }

    when {
        granted -> content()
        // Tant que la boite systeme n'a pas repondu, on n'affiche rien de definitif.
        !requested -> Unit
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            // Couleur du texte et non une teinte du theme : l'ecran est pose sur le noir
            // de la camera, ou un gris du theme ne se lirait pas.
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = LocalContentColor.current,
            )
            Text(
                stringResource(
                    if (blocked) {
                        R.string.scan_permission_blocked
                    } else {
                        R.string.scan_permission_required
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (blocked) {
                PrimaryButton(
                    text = stringResource(R.string.scan_permission_settings),
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.scan_permission_grant),
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}

private fun android.content.Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
