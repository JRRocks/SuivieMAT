package com.scanlist.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.scanlist.app.data.prefs.EmailPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Fichier .csv genere, pret a etre partage. */
data class ExportedFile(val uri: Uri, val fileName: String, val lineCount: Int)

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Ecrit le CSV dans le cache et renvoie son URI de partage.
     *
     * Le contenu arrive deja serialise : ce gestionnaire s'occupe du fichier et de
     * l'intention, pas de la forme des colonnes. C'est ce qui permet a un export de
     * dossier (ScanCsvBuilder) et a un export de comparaison (CompareCsvBuilder) de
     * partager le meme chemin d'envoi.
     *
     * Le cache, et non le stockage externe : le fichier n'a de valeur que le temps de
     * l'envoi, l'application ne demande donc aucune permission de stockage. Le chemin
     * `cache/exports/` est celui declare dans `res/xml/file_paths.xml`.
     */
    fun write(csv: String, rawFileName: String, lineCount: Int): Result<ExportedFile> = runCatching {
        val fileName = ExportFileName.sanitize(rawFileName)
        val directory = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        purgeOldExports(directory)

        val file = File(directory, fileName)
        file.writeText(csv, Charsets.UTF_8)

        ExportedFile(
            uri = FileProvider.getUriForFile(context, authority(), file),
            fileName = fileName,
            lineCount = lineCount,
        )
    }

    /**
     * Intention d'envoi, destinataire et objet pre-remplis.
     *
     * `ACTION_SEND` plutot que `ACTION_SENDTO` : seul le premier transporte une piece
     * jointe. La permission de lecture est accordee a l'application choisie, et a elle
     * seule, pour la duree de l'intention.
     */
    fun shareIntent(exported: ExportedFile, prefs: EmailPrefs): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, exported.uri)
            putExtra(Intent.EXTRA_SUBJECT, prefs.subject)
            putExtra(Intent.EXTRA_TEXT, prefs.body)
            if (prefs.recipient.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(prefs.recipient))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun suggestFileName(folderName: String): String = ExportFileName.suggest(folderName)

    /**
     * Supprime les exports de plus de 24 h.
     *
     * Sans cela chaque envoi laisserait un fichier derriere lui : le cache d'une
     * application de terrain ne doit pas grossir a chaque inventaire.
     */
    private fun purgeOldExports(directory: File) {
        val threshold = System.currentTimeMillis() - RETENTION_MS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < threshold) file.delete()
        }
    }

    private fun authority(): String = "${context.packageName}.fileprovider"

    private companion object {
        const val EXPORT_DIR = "exports"
        const val MIME_TYPE = "text/csv"
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
