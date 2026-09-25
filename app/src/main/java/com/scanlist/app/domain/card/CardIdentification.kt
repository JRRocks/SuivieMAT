package com.scanlist.app.domain.card

/**
 * Identification d'un badge qui ne suit ni PIV ni PKCS#15.
 *
 * Tout ici est de la lecture de donnees publiques, sans cle ni code : la presence
 * d'applications connues, la version d'une puce MIFARE DESFire, l'identite d'une puce
 * GlobalPlatform. C'est ce que lit n'importe quel outil d'identification NFC, et c'est
 * ce qui permet de savoir quelle norme programmer pour les niveaux 2 et 3.
 */

/** Application connue, sondee par un simple SELECT : sa presence situe la norme du badge. */
class KnownApplication(val name: String, val aid: ByteArray)

object KnownApplications {

    val PIV = KnownApplication("PIV", Piv.AID)

    val ALL: List<KnownApplication> = listOf(
        PIV,
        KnownApplication("PKCS#15", hexBytes("A0 00 00 00 63 50 4B 43 53 2D 31 35")),
        KnownApplication("MIFARE DESFire (ISO)", hexBytes("D2 76 00 00 85 01 00")),
        KnownApplication("NFC Forum Type 4 (NDEF)", hexBytes("D2 76 00 00 85 01 01")),
        KnownApplication("OpenPGP", hexBytes("D2 76 00 01 24 01")),
        KnownApplication("FIDO U2F", hexBytes("A0 00 00 06 47 2F 00 01")),
        // IAS-ECC : norme franco-europeenne (ANSSI) de carte a puce administrative,
        // notamment les "cartes agents" de l'ANTS. AID de l'applet standard Oberthur,
        // largement repris comme AID generique de detection IAS-ECC (source : OpenSC,
        // driver card-iasecc.c, OberthurIASECC_AID).
        KnownApplication("IAS-ECC", hexBytes("A0 00 00 00 77 01 08 00 07 00 00 FE 00 00 01 00")),
    )
}

/** Resultat du SELECT d'une application connue, avec le mot d'etat exact de la carte. */
data class ApplicationProbe(val name: String, val aid: String, val sw: Int) {
    val present: Boolean get() = sw == ApduResponse.SW_SUCCESS
}

/**
 * Version d'une puce MIFARE DESFire (commande native GetVersion).
 *
 * Seules les parties materielle et logicielle sont conservees : la troisieme trame de la
 * reponse porte l'UID et le lot de fabrication, lus pour terminer proprement la commande
 * mais jamais gardes ni affiches.
 */
class DesfireVersion private constructor(private val hardware: ByteArray, private val software: ByteArray) {

    val vendor: Int get() = hardware[0].toInt() and 0xFF
    val type: Int get() = hardware[1].toInt() and 0xFF
    val majorVersion: Int get() = hardware[3].toInt() and 0xFF
    val minorVersion: Int get() = hardware[4].toInt() and 0xFF

    /** Octet de taille : 2^(n/2) octets, le bit 0 signalant une taille intermediaire. */
    private val storageCode: Int get() = hardware[5].toInt() and 0xFF

    val storageBytes: Int get() = 1 shl (storageCode ushr 1)

    val hardwareHex: String get() = hardware.toHex()
    val softwareHex: String get() = software.toHex()

    /** Nom commercial quand il est reconnu, sinon null : les octets bruts restent affiches. */
    val productName: String?
        get() {
            if (vendor != VENDOR_NXP || type != TYPE_DESFIRE) return null
            val generation = when (majorVersion) {
                0x00 -> "D40"
                0x01 -> "EV1"
                0x12 -> "EV2"
                0x33 -> "EV3"
                else -> return "MIFARE DESFire"
            }
            return "MIFARE DESFire $generation"
        }

    companion object {
        private const val VENDOR_NXP = 0x04
        private const val TYPE_DESFIRE = 0x01
        private const val PART_SIZE = 7

        fun parse(hardware: ByteArray, software: ByteArray): DesfireVersion? =
            if (hardware.size >= PART_SIZE && software.size >= PART_SIZE) {
                DesfireVersion(hardware.copyOf(PART_SIZE), software.copyOf(PART_SIZE))
            } else {
                null
            }
    }
}

/**
 * Debut du CPLC (Card Production Life Cycle, GlobalPlatform) : fabricant et type de la
 * puce, systeme d'exploitation. Le numero de serie de la puce, plus loin dans la meme
 * structure, n'est volontairement pas lu.
 */
class Cplc private constructor(
    val icFabricator: Int,
    val icType: Int,
    val osId: Int,
    val osReleaseDate: Int,
) {
    val fabricatorName: String? get() = FABRICATORS[icFabricator]

    companion object {
        private const val TAG_CPLC = 0x9F7F
        private const val CPLC_SIZE = 42
        private const val KEPT_BYTES = 8

        private val FABRICATORS = mapOf(
            0x4790 to "NXP",
            0x4090 to "Infineon",
            0x4180 to "Microchip (Atmel)",
            0x4250 to "Samsung",
        )

        /** Accepte la reponse avec son etiquette 9F7F, ou les 42 octets nus que renvoient certaines cartes. */
        fun parse(response: ByteArray): Cplc? {
            val data = BerTlv.find(response, TAG_CPLC) ?: response.takeIf { it.size == CPLC_SIZE } ?: return null
            if (data.size < KEPT_BYTES) return null
            fun word(index: Int) = ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            return Cplc(word(0), word(2), word(4), word(6))
        }
    }
}

sealed interface DesfireResult {
    data class Found(val version: DesfireVersion) : DesfireResult

    /** La carte ne connait pas la commande (6E00, 6D00...) : ce n'est pas une DESFire. */
    data class Absent(val sw: Int) : DesfireResult

    data object NotTested : DesfireResult
}

sealed interface GlobalPlatformResult {
    /** [isdAid] : domaine de securite de l'emetteur, application par defaut d'une carte GlobalPlatform. */
    data class Found(val isdAid: String?, val cplc: Cplc?) : GlobalPlatformResult

    data class Absent(val sw: Int) : GlobalPlatformResult

    data object NotTested : GlobalPlatformResult
}
