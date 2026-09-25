package com.scanlist.app

import com.scanlist.app.domain.card.BerTlv
import com.scanlist.app.domain.card.CardTransport
import com.scanlist.app.domain.card.Piv
import com.scanlist.app.domain.card.hexBytes
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher

/**
 * Certificats et cles jetables, generes pour ces seuls tests : un couple RSA 2048 et un
 * couple ECC P-256, autosignes. Ils ne protegent rien et n'ont aucun usage hors d'ici.
 */
object CardFixtures {

    val rsaCertificate: ByteArray get() = decode(RSA_CERTIFICATE)

    val rsaPrivateKey: PrivateKey
        get() = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decode(RSA_PRIVATE_KEY)))

    val ecCertificate: ByteArray get() = decode(EC_CERTIFICATE)

    val ecPrivateKey: PrivateKey
        get() = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decode(EC_PRIVATE_KEY)))

    fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)

    private const val RSA_CERTIFICATE =
        "MIIDSzCCAjOgAwIBAgIUMJ9xORaF5AxeCMqGc0f96ECIkOIwDQYJKoZIhvcNAQELBQAwNTEXMBUGA1UECgwOU3VpdmllTUFUIFRl" +
        "c3QxGjAYBgNVBAMMEUJhZGdlIGRlIHRlc3QgUlNBMB4XDTI2MDkyNDIwNDYwNVoXDTM2MDkyMTIwNDYwNVowNTEXMBUGA1UECgwO" +
        "U3VpdmllTUFUIFRlc3QxGjAYBgNVBAMMEUJhZGdlIGRlIHRlc3QgUlNBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA" +
        "mtMSF/C6aHQILcwPJNXJBrh6D/VlMYdcPz9XoiH1hVXM5IUODxySpENm7VBfcUqBM1rnZu6OanHJOMLz+lph3HNw5Y4aQdsW6knZ" +
        "xScVB9lGfcbyoYfwJIaa+Fq4jcPsUnFtRZTEzOvmc+CnAQdO0VxFH0V4XL7mUdb/qdpfMeyOxxic/r8B2+xDhbJTIdEFmuuiHoTx" +
        "Bif76X6WUabwFLtzuun2Nd5sPL5q3K+3cjLlOPrdvuCXZRFn3h1xAYIkzMsJGvY/ZTu3E+pbLJOd05krwR0GXkbqKHYmjE8Awrdu" +
        "LgOON6KiHtlNCbJDfL0PmgQCvaNTgPY8PZ6+CQo5wwIDAQABo1MwUTAdBgNVHQ4EFgQUf6FAxghU90/V7UEiJ11mfPhOeFYwHwYD" +
        "VR0jBBgwFoAUf6FAxghU90/V7UEiJ11mfPhOeFYwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAa53On57wDPoe" +
        "YMGat2pQ1QP71MAzSD9CqFo6BkOXM3iSaAN6eEmfdmC2PxfuiVQtHiP722IOyZFXENIjAuWzJuUbpX5lIMQfw4DCRVHiEB0G7U3b" +
        "G9saOTflEbF6nSkMyqeJxg8i/3LvGwcWDqXucCCJX21PvSimRuvnpDDLd6qIS75gW0CxpM4vulSBQvd5pAngDQhp6CwlDYGxDXXp" +
        "fgY+1zYG8kHuxXj7kRpYMiM95jITKRTogMFPw60Qbm1FdH1z5aOPr+2OdTVZkjla1hAr0f5IBTF/EWh7QqhgLhFeUkSHkvShIsQF" +
        "XCZWHgqS3Wi2YROiia85776tb/qgVQ=="

    private const val RSA_PRIVATE_KEY =
        "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCa0xIX8LpodAgtzA8k1ckGuHoP9WUxh1w/P1eiIfWFVczkhQ4P" +
        "HJKkQ2btUF9xSoEzWudm7o5qcck4wvP6WmHcc3DljhpB2xbqSdnFJxUH2UZ9xvKhh/Akhpr4WriNw+xScW1FlMTM6+Zz4KcBB07R" +
        "XEUfRXhcvuZR1v+p2l8x7I7HGJz+vwHb7EOFslMh0QWa66IehPEGJ/vpfpZRpvAUu3O66fY13mw8vmrcr7dyMuU4+t2+4JdlEWfe" +
        "HXEBgiTMywka9j9lO7cT6lssk53TmSvBHQZeRuoodiaMTwDCt24uA443oqIe2U0JskN8vQ+aBAK9o1OA9jw9nr4JCjnDAgMBAAEC" +
        "ggEAAlKNo24ZH1oz1mgIuoWgdqk+xecckphUUgVdVBFmJI7+1b2I0lsCq7pWtjmhIBejhev+Egu2RDK0kSIcVQWVETo3Swim+S6H" +
        "RfimO4n62HuYZ87O5fOeYeKvbjyCJzioq1+IAzKyAkO82PRZdpXJYP4DpW5X2ca27F31+nJlpvlYL2l3c5hRsOWnwd/fiSHe+ucZ" +
        "Z2vp3C51QdEV8sW/+SfsHeICgPaPqMdy+etddiCCz6iV6TalS1Jt4Xk9gUvBCutbvmWFtK1D69H22k2D1ssjth1EpBiNtlli+v+5" +
        "aZC+fybTsK0SpB6OeM0TRd25a9z5qwi9xEsIET2smG8guQKBgQDPYE1O7ufvEFZtrO3kMl9I25WWBt99NxMj64GiVHikiYnJxbPA" +
        "intK6mAdTBBP6IuiUi40EVNv6QB/c4QjkLMDGkqt9jR0/NLW3akDSpGLmlRmOGyPUZ/P/DtOSvdKTI1Yz3zZwkk7bVuvMVLEphtv" +
        "jXR7KitWpAi5cDpTqeK5awKBgQC/IF5I3SXSf4KahQpf1KW1xodQNTJ9f0OMsNii4pF+8gBZ4h4S8GcObqVMxBEttGsZTCX2UGrU" +
        "14M/x7ZyaHynFNrAMNkzOCo5UyncuMuISYXy2w814WbOoTprnqtFvJ03bkY4AxhSkP5IazNj7aAePhb7X7LWjJGMNcAD2YlfCQKB" +
        "gQDHgoP9+7Th99re229nitrHEpMAao5bPkBI34uxbAlBax1IJM+B3vktIv3jUcmjxzuHp/7RkxWt5gHXnIA1Y0IGKyZL5UGkh5Qk" +
        "qc6v3u7V2111lrVC0SmqhKVd9puhPLMEDWK1ccjYxqZdj1ts284rbXDyCaj2SwwltAY5dYkuDwKBgF6yE+T4JWA5KyWSG0TF4GDC" +
        "/5Ie+WgyoO5ZRMzXgw0prEDchtgNYpJFAgOZ9YT2qkW7c+epinTwXqhANEZVPMWv0H5+9W6qOklyygABU16wRvy/yraAemaQfZiK" +
        "4gNIY4DuUz43Ulq+Wi7ZZnnxZ270hoB/tuUUepzEmOGPa7+5AoGAUmEPRQHLCp8HwwbFCBqm6hheIWS/hJoXSkCV6SG6PewAcqmq" +
        "4PCGgBaa3Cuj8zhz2L4Dmidl+BZ7OhEF6hgbmBaT/XEYAdR5WjLGuvnaNaaNuyP1nXiXi8diDjVwNudrlVXqSajWNGSzUiiwtmAa" +
        "VDKy+InA908GpD/3edxzdgA="

    private const val EC_CERTIFICATE =
        "MIIBvzCCAWWgAwIBAgIUSGTrQZCVkfIxtBJq82kvrhpadMUwCgYIKoZIzj0EAwIwNTEXMBUGA1UECgwOU3VpdmllTUFUIFRlc3Qx" +
        "GjAYBgNVBAMMEUJhZGdlIGRlIHRlc3QgRUNDMB4XDTI2MDkyNDIwNDYwNVoXDTM2MDkyMTIwNDYwNVowNTEXMBUGA1UECgwOU3Vp" +
        "dmllTUFUIFRlc3QxGjAYBgNVBAMMEUJhZGdlIGRlIHRlc3QgRUNDMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEOuZDYRzNaJZU" +
        "xW47RIBKkTNWy0nL8bbdqD6CO9xB4k97VtxKPCk0K3LXwCSDPUo4iQnu5tGUFqcliBf9H3dGtqNTMFEwHQYDVR0OBBYEFEHoH7qS" +
        "cSh6Kmf+zRxr1dBNwyAIMB8GA1UdIwQYMBaAFEHoH7qScSh6Kmf+zRxr1dBNwyAIMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0E" +
        "AwIDSAAwRQIhAM2TX0maWScvPPmAz9lvp0PE8nT5U1uqK1itc5VQoSvMAiB/xpcOZgSMyTvnRG0YKk+dT5iiFqo+jpdCFrNFzXCP" +
        "+w=="

    private const val EC_PRIVATE_KEY =
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgv2KssdaBsTKZi+3L9A6UuiXB0Q65LSc2Yf11QQTbJXqhRANCAAQ6" +
        "5kNhHM1ollTFbjtEgEqRM1bLScvxtt2oPoI73EHiT3tW3Eo8KTQrctfAJIM9SjiJCe7m0ZQWpyWIF/0fd0a2"
}

/**
 * Carte PIV simulee, au plus pres d'une vraie : reponses limitees a 256 octets avec la
 * suite par GET RESPONSE, commandes chainees, cle de carte qui signe le defi recu.
 */
class FakePivCard(
    private val certificate: ByteArray? = null,
    private val privateKey: PrivateKey? = null,
    private val pivPresent: Boolean = true,
    private val directoryRecords: List<ByteArray>? = null,
    private val challengeStatus: Int = 0x9000,
    private val compressCertificate: Boolean = false,
    private val failOnInstruction: Int? = null,
    /** 28 octets de GetVersion DESFire (7 materiel, 7 logiciel, 14 fabrication) ; null = pas une DESFire. */
    private val desfireVersion: ByteArray? = null,
    /** Domaine de securite GlobalPlatform selectionne par defaut ; null = pas de GlobalPlatform. */
    private val isdAid: ByteArray? = null,
    private val cplc: ByteArray? = null,
    /** true : la carte refuse la selection implicite (AID vide), comme une carte durcie. */
    private val hardenedSelection: Boolean = false,
) : CardTransport {

    val commands = mutableListOf<ByteArray>()

    private var pending = ByteArray(0)
    private val chain = ByteArrayOutputStream()
    private var pivSelected = false
    private var directorySelected = false
    private var desfireFrame = 0

    /** Instructions recues, dans l'ordre (octet INS de chaque commande). */
    fun instructions(): List<Int> = commands.map { it[1].toInt() and 0xFF }

    override fun transceive(command: ByteArray): ByteArray {
        commands += command
        val cla = command[0].toInt() and 0xFF
        val ins = command[1].toInt() and 0xFF
        val p1 = command[2].toInt() and 0xFF
        if (ins == failOnInstruction) throw IOException("badge retire")
        val data = if (command.size > 5) {
            command.copyOfRange(5, 5 + (command[4].toInt() and 0xFF))
        } else {
            ByteArray(0)
        }
        if (cla == CLA_DESFIRE) return desfire(ins)
        return when (ins) {
            INS_SELECT -> select(p1, data)
            INS_GP_GET_DATA -> if (cla == 0x80 && cplc != null) respond(BerTlv.encode(0x9F7F, cplc)) else status(0x6D00)
            INS_READ_RECORD -> readRecord(p1)
            INS_GET_DATA -> getData(data)
            INS_GENERAL_AUTHENTICATE -> generalAuthenticate(cla, data)
            INS_GET_RESPONSE -> getResponse()
            else -> status(0x6D00)
        }
    }

    private fun select(p1: Int, data: ByteArray): ByteArray {
        pivSelected = false
        directorySelected = false
        return when {
            p1 == 0x04 && pivPresent && data.contentEquals(Piv.AID) -> {
                pivSelected = true
                respond(BerTlv.encode(0x61, BerTlv.encode(0x4F, hexBytes("000010000100"))))
            }
            p1 == 0x00 && data.contentEquals(hexBytes("2F00")) && directoryRecords != null -> {
                directorySelected = true
                status(0x9000)
            }
            p1 == 0x04 && data.isEmpty() && isdAid != null && !hardenedSelection ->
                respond(BerTlv.encode(0x6F, BerTlv.encode(0x84, isdAid) + BerTlv.encode(0xA5, ByteArray(0))))
            // Carte durcie : seule la selection par AID explicite du domaine de securite
            // par defaut (A000000151000000, essaye par CardDiagnostic) aboutit.
            p1 == 0x04 && hardenedSelection && isdAid != null && data.contentEquals(hexBytes("A000000151000000")) ->
                respond(BerTlv.encode(0x6F, BerTlv.encode(0x84, isdAid) + BerTlv.encode(0xA5, ByteArray(0))))
            else -> status(0x6A82)
        }
    }

    /** GetVersion natif en trois trames, comme une vraie DESFire ; 6E00 sinon (CLA inconnue). */
    private fun desfire(ins: Int): ByteArray {
        val version = desfireVersion ?: return status(0x6E00)
        return when {
            ins == 0x60 -> {
                desfireFrame = 1
                version.copyOfRange(0, 7) + status(0x91AF)
            }
            ins == 0xAF && desfireFrame == 1 -> {
                desfireFrame = 2
                version.copyOfRange(7, 14) + status(0x91AF)
            }
            ins == 0xAF && desfireFrame == 2 -> {
                desfireFrame = 0
                version.copyOfRange(14, 28) + status(0x9100)
            }
            else -> status(0x911C)
        }
    }

    private fun readRecord(number: Int): ByteArray {
        val record = directoryRecords?.getOrNull(number - 1)
        return if (directorySelected && record != null) respond(record) else status(0x6A83)
    }

    private fun getData(data: ByteArray): ByteArray {
        if (!pivSelected) return status(0x6D00)
        val tag = BerTlv.find(data, 0x5C)?.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        return when {
            tag == Piv.OBJECT_CARD_AUTH_CERT && certificate != null -> respond(certificateObject(certificate))
            // Comme sur une vraie carte PIV : le certificat du titulaire n'est pas servi sans contact.
            tag == Piv.OBJECT_PIV_AUTH_CERT -> status(0x6982)
            else -> status(0x6A82)
        }
    }

    private fun certificateObject(der: ByteArray): ByteArray {
        val stored = if (compressCertificate) CardFixtures.gzip(der) else der
        val info = byteArrayOf((if (compressCertificate) 0x01 else 0x00).toByte())
        return BerTlv.encode(
            0x53,
            BerTlv.encode(0x70, stored) + BerTlv.encode(0x71, info) + BerTlv.encode(0xFE, ByteArray(0)),
        )
    }

    private fun generalAuthenticate(cla: Int, data: ByteArray): ByteArray {
        chain.write(data)
        if (cla and 0x10 != 0) return status(0x9000)
        val template = chain.toByteArray().also { chain.reset() }
        if (challengeStatus != 0x9000) return status(challengeStatus)
        val toSign = BerTlv.find(template, 0x7C)?.let { BerTlv.find(it, 0x81) } ?: return status(0x6A80)
        val key = privateKey ?: return status(0x6A82)
        val signature = if (key.algorithm == "RSA") {
            // Une carte PIV applique la cle privee brute : aucun bourrage ajoute par elle.
            Cipher.getInstance("RSA/ECB/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key)
                doFinal(toSign)
            }
        } else {
            Signature.getInstance("NONEwithECDSA").run {
                initSign(key)
                update(toSign)
                sign()
            }
        }
        return respond(BerTlv.encode(0x7C, BerTlv.encode(0x82, signature)))
    }

    private fun respond(payload: ByteArray): ByteArray {
        if (payload.size <= MAX_RESPONSE) return payload + status(0x9000)
        pending = payload.copyOfRange(MAX_RESPONSE, payload.size)
        return payload.copyOfRange(0, MAX_RESPONSE) + moreData()
    }

    private fun getResponse(): ByteArray {
        val size = minOf(MAX_RESPONSE, pending.size)
        val chunk = pending.copyOfRange(0, size)
        pending = pending.copyOfRange(size, pending.size)
        return chunk + if (pending.isEmpty()) status(0x9000) else moreData()
    }

    private fun moreData(): ByteArray =
        byteArrayOf(0x61, (if (pending.size >= MAX_RESPONSE) 0 else pending.size).toByte())

    private fun status(sw: Int): ByteArray = byteArrayOf((sw ushr 8).toByte(), sw.toByte())

    companion object {
        const val INS_SELECT = 0xA4
        const val INS_READ_RECORD = 0xB2
        const val INS_GET_DATA = 0xCB
        const val INS_GENERAL_AUTHENTICATE = 0x87
        const val INS_GET_RESPONSE = 0xC0
        const val INS_VERIFY = 0x20
        const val INS_GP_GET_DATA = 0xCA
        const val CLA_DESFIRE = 0x90
        const val MAX_RESPONSE = 256

        /** GetVersion d'une DESFire EV2 8 Ko : NXP (04), type 01, version 12 00, taille 1A. */
        val DESFIRE_EV2_VERSION: ByteArray = hexBytes(
            "04 01 01 12 00 1A 05" + "04 01 01 02 00 1A 05" + "04 11 22 33 44 55 66 BA 7C 11 22 33 18 20",
        )
    }
}
