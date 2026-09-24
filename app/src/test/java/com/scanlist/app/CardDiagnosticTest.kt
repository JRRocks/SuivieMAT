package com.scanlist.app

import com.scanlist.app.FakePivCard.Companion.INS_GENERAL_AUTHENTICATE
import com.scanlist.app.FakePivCard.Companion.INS_VERIFY
import com.scanlist.app.domain.card.BerTlv
import com.scanlist.app.domain.card.CardApplication
import com.scanlist.app.domain.card.CardDiagnostic
import com.scanlist.app.domain.card.CertificateResult
import com.scanlist.app.domain.card.ChallengeResult
import com.scanlist.app.domain.card.DirectoryResult
import com.scanlist.app.domain.card.PivAlgorithm
import com.scanlist.app.domain.card.hexBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator

class CardDiagnosticTest {

    @Test
    fun `une carte PIV RSA passe les niveaux 2 et 3`() {
        val card = FakePivCard(certificate = CardFixtures.rsaCertificate, privateKey = CardFixtures.rsaPrivateKey)

        val report = CardDiagnostic(card).run()

        assertEquals(true, report.pivPresent)
        val certificate = report.cardAuthCertificate as CertificateResult.Read
        assertTrue(certificate.summary.subject.contains("Badge de test RSA"))
        assertEquals(PivAlgorithm.RSA_2048, (report.challenge as ChallengeResult.Verified).algorithm)
        assertFalse(report.interrupted)
    }

    @Test
    fun `une carte PIV ECC au certificat compresse passe les niveaux 2 et 3`() {
        val card = FakePivCard(
            certificate = CardFixtures.ecCertificate,
            privateKey = CardFixtures.ecPrivateKey,
            compressCertificate = true,
        )

        val report = CardDiagnostic(card).run()

        assertTrue((report.cardAuthCertificate as CertificateResult.Read).compressed)
        assertEquals(PivAlgorithm.ECC_P256, (report.challenge as ChallengeResult.Verified).algorithm)
    }

    @Test
    fun `une carte qui exige un PIN refuse le defi et aucun PIN n est jamais envoye`() {
        val card = FakePivCard(
            certificate = CardFixtures.rsaCertificate,
            privateKey = CardFixtures.rsaPrivateKey,
            challengeStatus = 0x6982,
        )

        val report = CardDiagnostic(card).run()

        assertEquals(ChallengeResult.Refused(0x6982), report.challenge)
        assertFalse("une commande VERIFY a ete envoyee", INS_VERIFY in card.instructions())
        // Un seul defi, pas de nouvelle tentative : le refus est un resultat, pas un echec a contourner.
        val defis = card.commands.filter {
            (it[1].toInt() and 0xFF) == INS_GENERAL_AUTHENTICATE && (it[0].toInt() and 0x10) == 0
        }
        assertEquals(1, defis.size)
    }

    @Test
    fun `une carte qui signe avec une autre cle que celle du certificat est detectee`() {
        val autreCle = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().private
        val card = FakePivCard(certificate = CardFixtures.ecCertificate, privateKey = autreCle)

        val report = CardDiagnostic(card).run()

        assertTrue(report.challenge is ChallengeResult.Rejected)
    }

    @Test
    fun `une carte sans application PIV laisse les niveaux 2 et 3 non testes`() {
        val report = CardDiagnostic(FakePivCard(pivPresent = false)).run()

        assertEquals(false, report.pivPresent)
        assertEquals(false, report.pkcs15Present)
        assertEquals(CertificateResult.NotTested, report.cardAuthCertificate)
        assertEquals(ChallengeResult.NotTested, report.challenge)
        assertFalse(report.interrupted)
    }

    @Test
    fun `une carte PIV sans certificat de carte rend le niveau 3 non testable`() {
        val report = CardDiagnostic(FakePivCard(certificate = null)).run()

        assertEquals(CertificateResult.Refused(0x6A82), report.cardAuthCertificate)
        assertEquals(ChallengeResult.NotTested, report.challenge)
    }

    @Test
    fun `le certificat du titulaire refuse sans contact est rapporte tel quel`() {
        val card = FakePivCard(certificate = CardFixtures.ecCertificate, privateKey = CardFixtures.ecPrivateKey)

        assertEquals(CertificateResult.Refused(0x6982), CardDiagnostic(card).run().pivAuthCertificate)
    }

    @Test
    fun `le repertoire EF_DIR est lu quand la carte en a un`() {
        val record = BerTlv.encode(
            0x61,
            BerTlv.encode(0x4F, hexBytes("A000000063504B43532D3135")) + BerTlv.encode(0x50, "PKCS-15".toByteArray()),
        )

        val report = CardDiagnostic(FakePivCard(pivPresent = false, directoryRecords = listOf(record))).run()

        assertEquals(
            DirectoryResult.Listed(listOf(CardApplication("A000000063504B43532D3135", "PKCS-15"))),
            report.directory,
        )
    }

    @Test
    fun `une carte sans repertoire le signale sans erreur`() {
        val report = CardDiagnostic(FakePivCard(pivPresent = false)).run()

        assertEquals(DirectoryResult.Absent(0x6A82), report.directory)
    }

    @Test
    fun `un badge retire pendant le defi garde le certificat deja lu`() {
        val card = FakePivCard(
            certificate = CardFixtures.rsaCertificate,
            privateKey = CardFixtures.rsaPrivateKey,
            failOnInstruction = INS_GENERAL_AUTHENTICATE,
        )

        val report = CardDiagnostic(card).run()

        assertTrue(report.interrupted)
        assertTrue(report.cardAuthCertificate is CertificateResult.Read)
        assertEquals(ChallengeResult.NotTested, report.challenge)
        assertNull(report.pkcs15Present)
    }
}
