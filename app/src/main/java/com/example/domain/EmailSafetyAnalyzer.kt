package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.util.Locale

enum class EmailSafetyLevel { NONE, CAUTION, HIGH }

data class EmailSafetyAssessment(
    val level: EmailSafetyLevel,
    val reasons: List<String>
)

/**
 * Conservative, transparent local heuristics. This is not a malware scanner and cannot prove
 * that a sender or link is safe; it only surfaces wording that deserves a second look.
 */
object EmailSafetyAnalyzer {
    private val urlPattern = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private val urgencyTerms = listOf(
        "act now", "immediately", "urgent action", "account suspended",
        "verify your account", "password expires", "payment overdue",
        "sofort handeln", "konto gesperrt", "dringend erforderlich", "passwort läuft ab"
    )
    private val sensitiveTerms = listOf(
        "password", "one-time code", "verification code", "bank details",
        "wire transfer", "gift card", "payment information", "social security",
        "passwort", "tan", "bankdaten", "überweisung", "zahlung bestätigen"
    )

    fun assess(message: MailMessageEntity): EmailSafetyAssessment {
        val text = "${message.subject}\n${message.bodyText}"
            .lowercase(Locale.ROOT)
        val urgent = urgencyTerms.any(text::contains)
        val sensitive = sensitiveTerms.any(text::contains)
        val containsLink = urlPattern.containsMatchIn(text)
        val reasons = buildList {
            if (urgent) add("Uses urgent or pressure-based wording.")
            if (sensitive) add("Mentions credentials, financial details, or a sensitive verification step.")
            if (containsLink) add("Contains a link; verify its destination before opening it.")
        }
        val level = when {
            containsLink && (urgent || sensitive) -> EmailSafetyLevel.HIGH
            urgent || sensitive -> EmailSafetyLevel.CAUTION
            else -> EmailSafetyLevel.NONE
        }
        return EmailSafetyAssessment(level, reasons)
    }
}
