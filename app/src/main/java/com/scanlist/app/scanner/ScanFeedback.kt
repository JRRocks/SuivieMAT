package com.scanlist.app.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Retour haptique et sonore du scan a la chaine.
 *
 * En usage terrain l'utilisateur ne regarde pas l'ecran entre deux codes : le signal
 * doit passer par la main et l'oreille. Deux signatures nettement distinctes, pour
 * qu'un doublon ne soit jamais confondu avec une capture reussie.
 */
class ScanFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Cree a la demande : un ToneGenerator retient une piste audio, inutile de la
     * reserver tant qu'aucun scan n'a eu lieu.
     */
    private var toneGenerator: ToneGenerator? = null

    private fun tones(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
            }.getOrNull()
        }
        return toneGenerator
    }

    /** Nouvelle valeur enregistree : une impulsion courte et un bip bref. */
    fun success() {
        vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        tones()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    /** Deja present dans ce dossier : double impulsion et bip double. */
    fun duplicate() {
        vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1),
        )
        tones()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
    }

    /**
     * Valeur lue hors de la liste de reference, en mode Comparer.
     *
     * Ni reussite ni doublon : une impulsion longue et un bip grave, pour que
     * l'utilisateur sache sans regarder qu'il vient de viser autre chose que ce qu'il
     * controle. Rien n'est enregistre : ce signal est la seule trace de la lecture.
     */
    fun outOfList() {
        vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
        tones()?.startTone(ToneGenerator.TONE_SUP_ERROR, 250)
    }

    private fun vibrate(effect: VibrationEffect) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching { device.vibrate(effect) }
    }

    /** A appeler quand l'ecran de scan est quitte. */
    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private companion object {
        /** Volume relatif du bip, sur 100. Audible en atelier sans etre agressif. */
        const val TONE_VOLUME = 85
    }
}
