package com.scanlist.app.domain.update

import org.json.JSONException
import org.json.JSONObject

/** Un APK joint a une release. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    /** Taille annoncee par GitHub, ou -1 si elle est absente. */
    val sizeBytes: Long,
    /** Empreinte SHA-256 annoncee par GitHub, en hexadecimal minuscule, ou `null`. */
    val sha256: String?,
)

/** Une release publiee : sa version et ses APK. */
data class ReleaseInfo(
    val version: AppVersion,
    val assets: List<ReleaseAsset>,
)

/**
 * Lecture de la reponse de l'API GitHub `releases/latest`.
 *
 * Regle transverse du projet : sur erreur, ne rien faire. Une reponse illisible,
 * incomplete ou inattendue rend `null`, jamais d'exception ; l'application reste sur sa
 * version.
 */
object ReleaseParser {

    /**
     * [downloadPrefix] est le debut obligatoire de l'adresse d'un APK : la page de
     * telechargement des releases du depot. Une piece jointe qui pointerait ailleurs
     * est ecartee — l'application ne telecharge rien hors de son depot, meme si la
     * reponse le lui demande.
     */
    fun parse(json: String, downloadPrefix: String): ReleaseInfo? {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        // Un brouillon ou une pre-version n'est pas destine aux telephones.
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = AppVersion.parseOrNull(root.stringOrNull("tag_name")) ?: return null

        val array = root.optJSONArray("assets")
        val assets = (0 until (array?.length() ?: 0)).mapNotNull { index ->
            val entry = array?.optJSONObject(index) ?: return@mapNotNull null
            val name = entry.stringOrNull("name") ?: return@mapNotNull null
            val url = entry.stringOrNull("browser_download_url") ?: return@mapNotNull null
            if (!name.endsWith(APK_SUFFIX, ignoreCase = true)) return@mapNotNull null
            if (!url.startsWith(downloadPrefix, ignoreCase = true)) return@mapNotNull null
            ReleaseAsset(
                name = name,
                downloadUrl = url,
                sizeBytes = entry.optLong("size", -1),
                sha256 = sha256Of(entry.stringOrNull("digest")),
            )
        }
        return ReleaseInfo(version, assets)
    }

    /** GitHub ecrit "sha256:<64 chiffres hexadecimaux>" ; tout autre format est ignore. */
    private fun sha256Of(digest: String?): String? {
        if (digest == null || !digest.startsWith(SHA256_PREFIX)) return null
        val hex = digest.removePrefix(SHA256_PREFIX).lowercase()
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /**
     * `optString` ne suffit pas : pour une valeur JSON `null`, la bibliotheque
     * d'Android rend le texte "null".
     */
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private const val APK_SUFFIX = ".apk"
    private const val SHA256_PREFIX = "sha256:"
}

/** Ce que l'application doit faire d'une release, compte tenu de sa propre version. */
sealed interface UpdateOutcome {

    /** Rien a proposer : deja a jour, aucune release, ou aucun APK pour cet appareil. */
    data object UpToDate : UpdateOutcome

    data class Available(val version: AppVersion, val asset: ReleaseAsset) : UpdateOutcome
}

object UpdateResolver {

    /**
     * [supportedAbis] dans l'ordre de preference de l'appareil (`Build.SUPPORTED_ABIS`).
     *
     * Une release plus recente mais sans APK installable ici n'est pas une mise a
     * jour : la proposer menerait a un echec d'installation.
     */
    fun resolve(installed: AppVersion, release: ReleaseInfo?, supportedAbis: List<String>): UpdateOutcome {
        if (release == null || release.version <= installed) return UpdateOutcome.UpToDate
        val asset = pickAsset(release.assets, supportedAbis) ?: return UpdateOutcome.UpToDate
        return UpdateOutcome.Available(release.version, asset)
    }

    /**
     * L'APK de l'architecture preferee de l'appareil, reconnu a la fin de son nom
     * ("...-arm64-v8a.apk") ; a defaut, un APK qui ne nomme aucune architecture, donc
     * universel.
     */
    fun pickAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? =
        supportedAbis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.targets(abi) } }
            ?: assets.firstOrNull { asset -> KNOWN_ABIS.none { asset.targets(it) } }

    // Fin de nom, et non simple presence : "x86" figure dans "x86_64".
    private fun ReleaseAsset.targets(abi: String): Boolean = name.endsWith("-$abi.apk", ignoreCase = true)

    private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
}

/** Cadence de la verification automatique, au lancement de l'application. */
object UpdatePolicy {

    /**
     * Six heures : assez pour qu'une version publiee le matin soit proposee dans la
     * journee, sans interroger GitHub a chaque ouverture de l'application.
     */
    const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /**
     * [lastCheckAt] a 0 : jamais verifie. Une date dans le futur (horloge recalee)
     * compte comme une verification perimee, sinon elle bloquerait tout jusqu'a ce que
     * l'horloge la rattrape.
     */
    fun isAutoCheckDue(lastCheckAt: Long, now: Long): Boolean =
        lastCheckAt <= 0 || lastCheckAt > now || now - lastCheckAt >= AUTO_CHECK_INTERVAL_MS
}
