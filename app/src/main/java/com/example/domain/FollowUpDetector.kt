package com.example.domain

import com.example.data.local.entities.MailMessageEntity

/**
 * Detects explicit promises in an incoming message that may warrant a follow-up task.
 * It does not infer a promise from generic polite language.
 */
object FollowUpDetector {
    private val promisePatterns = listOf(
        Regex("""(?iu)\b(?:i|we)(?:'|’)ll\s+(?:get back to you|send|confirm|update you|let you know)\b"""),
        Regex("""(?iu)\b(?:i|we)\s+will\s+(?:get back to you|send|confirm|update you|let you know)\b"""),
        Regex("""(?iu)\bich\s+(?:melde mich|schicke|sende|bestätige|gebe Ihnen Rückmeldung)\b"""),
        Regex("""(?iu)\bwir\s+(?:melden uns|schicken|senden|bestätigen)\b""")
    )

    fun detect(message: MailMessageEntity): String? {
        val text = "${message.subject}\n${MailTextFormatter.plainText(message.bodyText.ifBlank { message.bodyHtml })}"
        return promisePatterns.firstNotNullOfOrNull { pattern -> pattern.find(text)?.value?.trim() }
    }
}
