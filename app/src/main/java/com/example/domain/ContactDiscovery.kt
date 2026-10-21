package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.util.Locale

data class ContactCandidate(
    val email: String,
    val displayName: String,
    val sourceMessageId: String,
    val sourceSubject: String,
    val phone: String = "",
    val organization: String = ""
)

/**
 * Proposes contacts from message headers and clearly labelled signature fields.
 * Candidates are review-only until the user explicitly saves them.
 */
object ContactDiscovery {
    private val emailPattern = Regex(
        """(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+"""
    )
    private val phoneLabelPattern = Regex("""(?i)\b(phone|tel\.?|mobile|telefon|handy)\s*[:：]""")
    private val organizationLabelPattern = Regex("""(?i)\b(company|organization|organisation|firma|organization)\s*[:：]\s*(.+)""")
    private val excludedLocalParts = setOf(
        "no-reply", "noreply", "do-not-reply", "donotreply", "mailer-daemon",
        "notifications", "notification", "newsletter", "news", "support-bot"
    )

    fun discover(
        messages: List<MailMessageEntity>,
        existingEmails: Set<String>,
        ownEmail: String
    ): List<ContactCandidate> {
        val excluded = existingEmails.map { it.trim().lowercase(Locale.ROOT) }.toMutableSet()
        if (ownEmail.isNotBlank()) excluded += ownEmail.trim().lowercase(Locale.ROOT)
        val candidates = linkedMapOf<String, ContactCandidate>()

        messages.sortedByDescending { it.receivedTimestamp }.forEach { message ->
            val senderAddress = emailPattern.find(message.senderEmail)?.value?.lowercase(Locale.ROOT)
            val recipients = emailPattern.findAll(message.recipients).map { it.value.lowercase(Locale.ROOT) }.toList()
            val phone = message.bodyText.lineSequence()
                .firstOrNull { phoneLabelPattern.containsMatchIn(it) }
                ?.let { line ->
                    line.substringAfter(':', line.substringAfter('：', "")).trim()
                }
                ?.take(40)
                .orEmpty()
            val organization = message.bodyText.lineSequence()
                .firstNotNullOfOrNull { line -> organizationLabelPattern.find(line)?.groupValues?.getOrNull(2)?.trim() }
                ?.take(100)
                .orEmpty()

            val addresses = buildList {
                if (!senderAddress.isNullOrBlank()) add(senderAddress to true)
                recipients.forEach { add(it to false) }
            }

            addresses.forEach addressLoop@ { (address, isSender) ->
                val key = address.lowercase(Locale.ROOT)
                if (key in excluded || key in candidates) return@addressLoop
                val localPart = key.substringBefore('@')
                if (localPart in excludedLocalParts || localPart.startsWith("noreply") || localPart.startsWith("no-reply")) {
                    return@addressLoop
                }
                val nameFromHeader = if (isSender) message.senderName.trim().trim('"', '\'') else ""
                val name = nameFromHeader
                    .takeIf { it.isNotBlank() && !it.equals(address, ignoreCase = true) }
                    ?: localPart.split(Regex("[._+-]+"))
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { it.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }

                candidates[key] = ContactCandidate(
                    email = address,
                    displayName = name,
                    sourceMessageId = message.id,
                    sourceSubject = message.subject,
                    phone = if (isSender) phone else "",
                    organization = if (isSender) organization else ""
                )
            }
        }
        return candidates.values.toList()
    }
}
