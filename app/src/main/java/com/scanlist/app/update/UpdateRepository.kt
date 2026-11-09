package com.scanlist.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.scanlist.app.domain.update.AppVersion
import com.scanlist.app.domain.update.ReleaseAsset
import com.scanlist.app.domain.update.ReleaseParser
import com.scanlist.app.domain.update.UpdateOutcome
import com.scanlist.app.domain.update.UpdateResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Issue d'une interrogation de GitHub. */
sealed interface UpdateCheck {
    data class Done(val outcome: UpdateOutcome) : UpdateCheck

    /** Pas de reseau, GitHub injoignable ou reponse inattendue : on ne sait pas. */
    data object Unreachable : UpdateCheck
}

/** Issue d'un telechargement. */
sealed interface UpdateDownload {
    data class Ready(val apk: File) : UpdateDownload

    /** Coupure reseau ou stockage plein : rien n'est a reprocher au fichier. */
    data object Failed : UpdateDownload

    /** Le fichier recu n'est pas celui annonce, ou n'est pas une mise a jour de cette application. */
    data object Invalid : UpdateDownload
}

/**
 * Mise a jour de l'application par les releases GitHub du depot (decision 34).
 *
 * C'est le seul code de l'application qui touche au reseau, et il ne parle qu'a GitHub :
 * une question a l'API ("quelle est la derniere release ?"), puis le telechargement de
 * l'APK. Le scan, l'OCR, les dossiers et la comparaison restent hors-ligne. Sans reseau,
 * tout ce qui suit echoue en silence.
 *
 * L'installation elle-meme est faite par Android, qui demande toujours confirmation et
 * refuse tout APK signe par une autre cle que celle de l'application installee.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** `versionName` de l'application installee, tel qu'Android le connait. */
    val installedVersionName: String by lazy {
        runCatching { installedPackage(0).versionName }.getOrNull().orEmpty()
    }

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val installed = AppVersion.parseOrNull(installedVersionName) ?: return@withContext UpdateCheck.Unreachable
        var connection: HttpURLConnection? = null
        try {
            connection = open(LATEST_RELEASE_URL, API_READ_TIMEOUT_MS)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.use { it.readBounded(MAX_RESPONSE_BYTES) }
                        ?: return@withContext UpdateCheck.Unreachable
                    val release = ReleaseParser.parse(body, DOWNLOAD_PREFIX)
                    UpdateCheck.Done(UpdateResolver.resolve(installed, release, Build.SUPPORTED_ABIS.toList()))
                }
                // Aucune release publiee : il n'y a rien de plus recent.
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheck.Done(UpdateOutcome.UpToDate)
                else -> UpdateCheck.Unreachable
            }
        } catch (e: IOException) {
            UpdateCheck.Unreachable
        } catch (e: SecurityException) {
            UpdateCheck.Unreachable
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Telecharge [asset] dans le cache, puis le controle avant de le rendre : taille et
     * empreinte annoncees par GitHub, identifiant, version et signature de l'APK.
     *
     * [onProgress] recoit une fraction de 0 a 1, ou `null` si la taille est inconnue.
     * L'annulation de la coroutine arrete le telechargement et efface le fichier.
     */
    suspend fun download(asset: ReleaseAsset, onProgress: (Float?) -> Unit): UpdateDownload =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, UPDATE_DIR)
            // Un seul APK a la fois : celui d'une version precedente n'a plus d'usage.
            directory.deleteRecursively()
            if (!directory.mkdirs()) return@withContext UpdateDownload.Failed

            val target = File(directory, APK_FILE_NAME)
            var connection: HttpURLConnection? = null
            try {
                connection = open(asset.downloadUrl, DOWNLOAD_READ_TIMEOUT_MS)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext UpdateDownload.Failed

                val expected = asset.sizeBytes.takeIf { it > 0 } ?: connection.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            received += read
                            // Un APK plus gros qu'annonce n'est pas celui de la release.
                            if (received > MAX_APK_BYTES || (expected > 0 && received > expected)) {
                                return@withContext invalid(target)
                            }
                            onProgress(if (expected > 0) received.toFloat() / expected else null)
                        }
                    }
                }

                // Flux coupe avant la fin : c'est le reseau, pas le fichier, qui est en cause.
                if (expected > 0 && received < expected) {
                    target.delete()
                    return@withContext UpdateDownload.Failed
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (asset.sha256 != null && asset.sha256 != sha256) return@withContext invalid(target)
                if (!isUpdateOfThisApp(target)) return@withContext invalid(target)

                UpdateDownload.Ready(target)
            } catch (e: CancellationException) {
                // Annule par l'utilisateur : pas de fichier a moitie ecrit dans le cache.
                target.delete()
                throw e
            } catch (e: IOException) {
                target.delete()
                UpdateDownload.Failed
            } catch (e: SecurityException) {
                target.delete()
                UpdateDownload.Failed
            } finally {
                connection?.disconnect()
            }
        }

    /**
     * Intention d'installation : Android affiche sa propre demande de confirmation, et,
     * la premiere fois, celle qui autorise SuivieMAT a installer des applications.
     */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Efface l'APK d'une mise a jour deja faite : le cache ne doit pas garder 20 Mo pour rien. */
    suspend fun purgeDownloads() = withContext(Dispatchers.IO) {
        File(context.cacheDir, UPDATE_DIR).deleteRecursively()
        Unit
    }

    private fun invalid(target: File): UpdateDownload {
        target.delete()
        return UpdateDownload.Invalid
    }

    /**
     * Verifie que le fichier est bien cette application, dans une version qu'Android
     * acceptera, signee par la meme cle.
     *
     * Android refait ces controles a l'installation, et lui seul fait foi. Les faire ici
     * evite d'ouvrir l'installateur sur un fichier qu'il refusera avec un message obscur.
     */
    private fun isUpdateOfThisApp(apk: File): Boolean {
        // Variante a drapeaux entiers, depreciee en API 33 mais la seule qui existe
        // depuis l'API 26.
        @Suppress("DEPRECATION")
        val archive = runCatching { context.packageManager.getPackageArchiveInfo(apk.path, SIGNATURE_FLAGS) }
            .getOrNull() ?: return false
        val installed = runCatching { installedPackage(SIGNATURE_FLAGS) }.getOrNull() ?: return false
        if (archive.packageName != context.packageName) return false
        // Android refuse de revenir a un versionCode inferieur.
        if (PackageInfoCompat.getLongVersionCode(archive) < PackageInfoCompat.getLongVersionCode(installed)) return false

        val archiveSigners = archive.signers()
        // Selon la version d'Android, les signataires d'un fichier non installe ne sont
        // pas toujours rendus : l'installateur tranchera.
        if (archiveSigners.isEmpty()) return true
        return archiveSigners == installed.signers()
    }

    @Suppress("DEPRECATION")
    private fun installedPackage(flags: Int): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName, flags)

    private fun PackageInfo.signers(): Set<String> {
        val modern = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val found = modern ?: signatures
        return found.orEmpty().filterNotNull().map { it.toCharsString() }.toSet()
    }

    private fun open(url: String, readTimeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
            // GitHub renvoie vers son stockage de fichiers pour le telechargement.
            instanceFollowRedirects = true
            // L'API GitHub rejette toute requete sans User-Agent.
            setRequestProperty("User-Agent", USER_AGENT)
        }

    /** Lit une reponse texte, ou rend `null` si elle depasse [limit] : ce n'est pas celle attendue. */
    private fun java.io.InputStream.readBounded(limit: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            bytes.write(buffer, 0, read)
            if (bytes.size() > limit) return null
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val REPOSITORY = "JRRocks/SuivieMAT"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"

        /** Seuls les fichiers des releases de ce depot sont telecharges. */
        const val DOWNLOAD_PREFIX = "https://github.com/$REPOSITORY/releases/download/"

        const val USER_AGENT = "SuivieMAT"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        /** Declare dans res/xml/file_paths.xml : l'installateur lit l'APK par FileProvider. */
        const val UPDATE_DIR = "updates"
        const val APK_FILE_NAME = "SuivieMAT.apk"

        const val CONNECT_TIMEOUT_MS = 10_000
        const val API_READ_TIMEOUT_MS = 15_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024

        /** La description d'une release pese quelques kilo-octets. */
        const val MAX_RESPONSE_BYTES = 512 * 1024

        /** L'APK pese une vingtaine de Mo : au-dela de 200, ce n'est pas lui. */
        const val MAX_APK_BYTES = 200L * 1024 * 1024

        // Les deux drapeaux a la fois : selon la version d'Android, un fichier non
        // installe ne rend ses signataires que par l'un ou par l'autre.
        @Suppress("DEPRECATION")
        val SIGNATURE_FLAGS: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNATURES or PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
    }
}
