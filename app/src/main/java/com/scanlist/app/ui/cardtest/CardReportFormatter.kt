package com.scanlist.app.ui.cardtest

import android.content.res.Resources
import com.scanlist.app.R
import com.scanlist.app.domain.card.ApduResponse
import com.scanlist.app.domain.card.CardReport
import com.scanlist.app.domain.card.CertificateResult
import com.scanlist.app.domain.card.ChallengeResult
import com.scanlist.app.domain.card.DesfireResult
import com.scanlist.app.domain.card.DirectoryResult
import com.scanlist.app.domain.card.GlobalPlatformResult
import com.scanlist.app.domain.card.formatStatusWord
import com.scanlist.app.domain.card.toHex
import com.scanlist.app.scanner.CardTestOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LineStatus { OK, FAILED, INFO }

class ReportLine(val label: String, val value: String, val status: LineStatus = LineStatus.INFO)

class ReportSection(val title: String, val lines: List<ReportLine>)

/**
 * Mise en mots du rapport de test, pour l'ecran comme pour la copie.
 *
 * Une seule source pour les deux : le rapport que l'utilisateur transmet est exactement
 * ce qu'il a vu a l'ecran.
 */
object CardReportFormatter {

    fun sections(res: Resources, outcome: CardTestOutcome): List<ReportSection> {
        val badge = badgeSection(res, outcome)
        val report = outcome.report ?: return listOf(badge)
        return buildList {
            add(badge)
            add(applicationsSection(res, report))
            add(chipSection(res, report))
            add(level2Section(res, report))
            add(level3Section(res, report))
            if (report.pivPresent == true) add(holderSection(res, report))
        }
    }

    fun toText(res: Resources, sections: List<ReportSection>): String = buildString {
        appendLine(res.getString(R.string.card_test_report_title))
        sections.forEach { section ->
            appendLine()
            appendLine("== ${section.title} ==")
            section.lines.forEach { line ->
                val mark = when (line.status) {
                    LineStatus.OK -> res.getString(R.string.card_mark_ok) + " "
                    LineStatus.FAILED -> res.getString(R.string.card_mark_failed) + " "
                    LineStatus.INFO -> ""
                }
                appendLine("$mark${line.label} : ${line.value}")
            }
        }
    }

    private fun badgeSection(res: Resources, outcome: CardTestOutcome): ReportSection {
        val lines = mutableListOf(
            ReportLine(res.getString(R.string.card_label_technologies), outcome.technologies.joinToString(", ")),
            ReportLine(
                res.getString(R.string.card_label_iso_dep),
                yesNo(res, outcome.isoDep),
                if (outcome.isoDep) LineStatus.OK else LineStatus.FAILED,
            ),
        )
        if (outcome.atqa != null && outcome.sak != null) {
            lines += ReportLine(
                res.getString(R.string.card_label_atqa_sak),
                res.getString(R.string.card_atqa_sak_value, outcome.atqa.toHex(), "%02X".format(outcome.sak)),
            )
        }
        if (outcome.isoDep) {
            // Affiche meme vides : un badge sans octets historiques est deja une indication.
            val historical = outcome.historicalBytes?.takeIf { it.isNotEmpty() }
            lines += ReportLine(
                res.getString(R.string.card_label_historical),
                historical?.let { historical(res, it) } ?: res.getString(R.string.card_none),
            )
        }
        outcome.extendedLength?.let {
            lines += ReportLine(res.getString(R.string.card_label_extended_length), yesNo(res, it))
        }
        val result = res.getString(R.string.card_label_result)
        when {
            !outcome.isoDep ->
                lines += ReportLine(result, res.getString(R.string.card_iso_dep_missing), LineStatus.FAILED)
            outcome.connectionFailed ->
                lines += ReportLine(result, res.getString(R.string.card_connection_failed), LineStatus.FAILED)
        }
        outcome.report?.let { report ->
            if (report.interrupted) {
                lines += ReportLine(result, res.getString(R.string.card_interrupted), LineStatus.FAILED)
            }
            lines += ReportLine(
                res.getString(R.string.card_label_duration),
                res.getString(R.string.card_duration_ms, report.durationMs),
            )
        }
        return ReportSection(res.getString(R.string.card_section_badge), lines)
    }

    private fun applicationsSection(res: Resources, report: CardReport): ReportSection {
        val directoryLabel = res.getString(R.string.card_label_directory)
        val lines = mutableListOf<ReportLine>()
        when (val directory = report.directory) {
            is DirectoryResult.Listed ->
                if (directory.applications.isEmpty()) {
                    lines += ReportLine(directoryLabel, res.getString(R.string.card_directory_empty))
                } else {
                    directory.applications.forEach { application ->
                        lines += ReportLine(application.aid, application.label ?: res.getString(R.string.card_no_label))
                    }
                }
            is DirectoryResult.Absent ->
                lines += ReportLine(
                    directoryLabel,
                    res.getString(R.string.card_directory_absent, formatStatusWord(directory.sw)),
                )
            DirectoryResult.NotTested ->
                lines += ReportLine(directoryLabel, res.getString(R.string.card_not_tested))
        }
        report.applications.forEach { probe ->
            val value = if (probe.present) {
                res.getString(R.string.card_present)
            } else {
                res.getString(R.string.card_absent_with_sw, formatStatusWord(probe.sw))
            }
            lines += ReportLine(
                res.getString(R.string.card_application_label, probe.name, probe.aid),
                value,
                if (probe.present) LineStatus.OK else LineStatus.INFO,
            )
        }
        return ReportSection(res.getString(R.string.card_section_applications), lines)
    }

    private fun chipSection(res: Resources, report: CardReport): ReportSection {
        val lines = mutableListOf<ReportLine>()
        val desfireLabel = res.getString(R.string.card_label_desfire)
        when (val desfire = report.desfire) {
            is DesfireResult.Found -> {
                val version = desfire.version
                val product = version.productName ?: res.getString(R.string.card_desfire_unknown_product)
                lines += ReportLine(
                    desfireLabel,
                    res.getString(R.string.card_desfire_value, product, version.storageBytes / 1024.0),
                    LineStatus.OK,
                )
                lines += ReportLine(
                    res.getString(R.string.card_label_desfire_version),
                    res.getString(R.string.card_desfire_version_value, version.hardwareHex, version.softwareHex),
                )
                if (report.pivPresent != true) {
                    lines += ReportLine(
                        res.getString(R.string.card_label_interpretation),
                        res.getString(R.string.card_desfire_interpretation),
                    )
                }
            }
            is DesfireResult.Absent ->
                lines += ReportLine(desfireLabel, res.getString(R.string.card_no_answer, formatStatusWord(desfire.sw)))
            DesfireResult.NotTested ->
                lines += ReportLine(desfireLabel, res.getString(R.string.card_not_tested))
        }

        val globalPlatformLabel = res.getString(R.string.card_label_global_platform)
        when (val globalPlatform = report.globalPlatform) {
            is GlobalPlatformResult.Found -> {
                lines += ReportLine(
                    globalPlatformLabel,
                    globalPlatform.isdAid?.let { res.getString(R.string.card_global_platform_isd, it) }
                        ?: res.getString(R.string.card_yes),
                    LineStatus.OK,
                )
                globalPlatform.cplc?.let { cplc ->
                    val fabricator = "%04X".format(cplc.icFabricator)
                    lines += ReportLine(
                        res.getString(R.string.card_label_ic_fabricator),
                        cplc.fabricatorName?.let { "$it ($fabricator)" } ?: fabricator,
                    )
                    lines += ReportLine(res.getString(R.string.card_label_ic_type), "%04X".format(cplc.icType))
                    lines += ReportLine(
                        res.getString(R.string.card_label_os),
                        res.getString(R.string.card_os_value, "%04X".format(cplc.osId), "%04X".format(cplc.osReleaseDate)),
                    )
                }
            }
            is GlobalPlatformResult.Absent ->
                lines += ReportLine(globalPlatformLabel, res.getString(R.string.card_no_answer, formatStatusWord(globalPlatform.sw)))
            GlobalPlatformResult.NotTested ->
                lines += ReportLine(globalPlatformLabel, res.getString(R.string.card_not_tested))
        }
        return ReportSection(res.getString(R.string.card_section_chip), lines)
    }

    private fun level2Section(res: Resources, report: CardReport): ReportSection {
        val result = res.getString(R.string.card_label_result)
        val lines = when (val certificate = report.cardAuthCertificate) {
            is CertificateResult.Read ->
                listOf(ReportLine(result, res.getString(R.string.card_certificate_read), LineStatus.OK)) +
                    certificateLines(res, certificate)
            is CertificateResult.Refused ->
                listOf(ReportLine(result, refused(res, certificate.sw), LineStatus.FAILED))
            CertificateResult.Unreadable ->
                listOf(ReportLine(result, res.getString(R.string.card_unreadable), LineStatus.FAILED))
            CertificateResult.NotTested -> {
                val why = if (report.pivPresent == false) R.string.card_level2_no_piv else R.string.card_not_tested
                listOf(ReportLine(result, res.getString(why)))
            }
        }
        return ReportSection(res.getString(R.string.card_section_level2), lines)
    }

    private fun level3Section(res: Resources, report: CardReport): ReportSection {
        val result = res.getString(R.string.card_label_result)
        val lines = mutableListOf<ReportLine>()
        when (val challenge = report.challenge) {
            is ChallengeResult.Verified -> {
                lines += ReportLine(result, res.getString(R.string.card_level3_verified), LineStatus.OK)
                lines += ReportLine(res.getString(R.string.card_label_algorithm), challenge.algorithm.label)
                lines += ReportLine(
                    res.getString(R.string.card_label_signature_duration),
                    res.getString(R.string.card_duration_ms, challenge.durationMs),
                )
            }
            is ChallengeResult.Rejected -> {
                lines += ReportLine(result, res.getString(R.string.card_level3_rejected), LineStatus.FAILED)
                lines += ReportLine(res.getString(R.string.card_label_algorithm), challenge.algorithm.label)
            }
            is ChallengeResult.Refused -> {
                lines += ReportLine(result, refused(res, challenge.sw), LineStatus.FAILED)
                if (challenge.sw == ApduResponse.SW_SECURITY_STATUS_NOT_SATISFIED) {
                    lines += ReportLine(result, res.getString(R.string.card_level3_pin_required))
                }
            }
            ChallengeResult.UnsupportedKey ->
                lines += ReportLine(result, res.getString(R.string.card_level3_unsupported_key), LineStatus.FAILED)
            ChallengeResult.Unreadable ->
                lines += ReportLine(result, res.getString(R.string.card_unreadable), LineStatus.FAILED)
            ChallengeResult.NotTested ->
                lines += ReportLine(result, res.getString(R.string.card_level3_needs_certificate))
        }
        if (report.cardAuthCertificate is CertificateResult.Read) {
            lines += ReportLine(
                res.getString(R.string.card_label_trust_chain),
                res.getString(R.string.card_trust_chain_unchecked),
            )
        }
        return ReportSection(res.getString(R.string.card_section_level3), lines)
    }

    private fun holderSection(res: Resources, report: CardReport): ReportSection {
        val result = res.getString(R.string.card_label_result)
        val lines = when (val certificate = report.pivAuthCertificate) {
            is CertificateResult.Read -> listOf(
                ReportLine(result, res.getString(R.string.card_certificate_read), LineStatus.OK),
                ReportLine(res.getString(R.string.card_label_subject), certificate.summary.subject),
            )
            is CertificateResult.Refused ->
                listOf(ReportLine(result, res.getString(R.string.card_holder_certificate_refused, formatStatusWord(certificate.sw))))
            CertificateResult.Unreadable ->
                listOf(ReportLine(result, res.getString(R.string.card_unreadable), LineStatus.FAILED))
            CertificateResult.NotTested ->
                listOf(ReportLine(result, res.getString(R.string.card_not_tested)))
        }
        return ReportSection(res.getString(R.string.card_section_holder_certificate), lines)
    }

    private fun certificateLines(res: Resources, read: CertificateResult.Read): List<ReportLine> {
        val summary = read.summary
        return listOf(
            ReportLine(res.getString(R.string.card_label_subject), summary.subject),
            ReportLine(res.getString(R.string.card_label_issuer), summary.issuer),
            ReportLine(res.getString(R.string.card_label_serial), summary.serialNumber),
            ReportLine(
                res.getString(R.string.card_label_validity),
                res.getString(R.string.card_validity_range, date(summary.notBefore), date(summary.notAfter)),
            ),
            ReportLine(
                res.getString(R.string.card_label_key),
                res.getString(R.string.card_key_description, summary.keyAlgorithm, summary.keySizeBits),
            ),
            ReportLine(res.getString(R.string.card_label_self_signed), yesNo(res, summary.selfSigned)),
            ReportLine(res.getString(R.string.card_label_compressed), yesNo(res, read.compressed)),
        )
    }

    private fun refused(res: Resources, sw: Int): String =
        res.getString(R.string.card_refused, formatStatusWord(sw))

    private fun yesNo(res: Resources, value: Boolean): String =
        res.getString(if (value) R.string.card_yes else R.string.card_no)

    /** Hexadecimal, plus le texte imprimable : les fabricants y ecrivent souvent le nom du produit. */
    private fun historical(res: Resources, bytes: ByteArray): String {
        val text = String(bytes, Charsets.ISO_8859_1).map { if (it in ' '..'~') it else '.' }.joinToString("")
        return res.getString(R.string.card_historical_value, bytes.toHex(), text)
    }

    private fun date(epochMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(epochMs))
}
