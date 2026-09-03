package com.scanlist.app.ui.scan

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scanlist.app.R
import com.scanlist.app.scanner.DetectedNfcTag
import com.scanlist.app.scanner.NfcTagDecoder

/**
 * Surface d'attente d'un tag NFC/RFID.
 *
 * Pas de flux camera ici : contrairement aux trois autres modes, la lecture NFC est
 * evenementielle et pilotee par [NfcAdapter.enableReaderMode], pas par une analyse
 * d'images. L'ecran reste statique, un pictogramme rappelle ou approcher le tag.
 *
 * Le mode lecteur est arme et desarme sur le cycle de vie de l'ecran (ON_RESUME /
 * ON_PAUSE), comme le recommande la documentation Android — et non une seule fois a la
 * composition, sinon un retour depuis les reglages systeme laisserait le lecteur inactif.
 */
@Composable
fun NfcScanSurface(
    onTagRead: (DetectedNfcTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }

    // rememberUpdatedState : le callback systeme n'est enregistre qu'a chaque
    // ON_RESUME, mais doit toujours invoquer la derniere lambda recomposee.
    val currentOnTagRead by rememberUpdatedState(onTagRead)

    var enabled by remember { mutableStateOf(nfcAdapter?.isEnabled == true) }

    DisposableEffect(lifecycleOwner, activity, nfcAdapter) {
        if (activity == null || nfcAdapter == null) {
            return@DisposableEffect onDispose {}
        }

        val callback = NfcAdapter.ReaderCallback { tag ->
            NfcTagDecoder.decode(tag)?.let(currentOnTagRead)
        }
        // Tous les types de tag : FLAG_READER_NFC_A/B couvre l'essentiel des puces
        // Mifare utilisees pour l'etiquetage materiel, FLAG_READER_NFC_BARCODE les
        // etiquettes Kovio parfois utilisees en logistique. SKIP_NDEF_CHECK evite une
        // lecture NDEF automatique par le systeme avant que le callback ne recoive
        // le tag : NfcTagDecoder s'en charge lui-meme, avec sa propre protection.
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    nfcAdapter.enableReaderMode(activity, callback, flags, null)
                    enabled = nfcAdapter.isEnabled
                }
                Lifecycle.Event.ON_PAUSE -> runCatching { nfcAdapter.disableReaderMode(activity) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { nfcAdapter.disableReaderMode(activity) }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = when {
                    nfcAdapter == null -> stringResource(R.string.nfc_unavailable)
                    !enabled -> stringResource(R.string.nfc_disabled)
                    else -> stringResource(R.string.nfc_instruction)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (nfcAdapter != null && !enabled) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                    Text(stringResource(R.string.nfc_enable_settings))
                }
            }
        }
    }
}
