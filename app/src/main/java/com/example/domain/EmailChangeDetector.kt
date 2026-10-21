package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.util.Locale

data class DetectedEmailChange(
    val label: String,
    val previousValue: String,
    val currentValue: String,
    val previousMessageId: String
)

/**
 * Compares a few explicit, structured facts across messages in the same thread.
 * It deliberately does not claim that a missing fact means a value was removed.
 */
object EmailChangeDetector {
    private data class FactPattern(val label: String, val regex: Regex)

    private val patterns = listOf(
        FactPattern("Date", Regex("""(?iu)\b\d{1,2}[./-]\d{1,2}[./-](?:\d{2}|\d{4})\b""")),
        FactPattern("Time", Regex("""(?iu)\b\d{1,2}:[0-5]\d(?:\s*(?:am|pm|Uhr))?\b""")),
        FactPattern("Amount", Regex("""(?iu)(?:€|EUR|USD|\$|£)\s?\d[\d.,]*|\d[\d.,]*\s?(?:€|EUR|USD|\$|£)\b""")),
        FactPattern("Room", Regex("""(?iu)\b(?:room|raum|seminarraum)\s*[A-Z0-9][A-Z0-9 ._-]{0,20}""")),
        FactPattern("Location", Regex("""(?im)^\s*(?:location|ort)\s*:\s*([^\n]+)"""))
    )

    fun detect(
        current: MailMessageEntity,
        mailboxMessages: List<MailMessageEntity>
    ): List<DetectedEmailChange> {
        if (current.threadId.isBlank()) return emptyList()
        val previous = mailboxMessages
            .asSequence()
            .filter {
                it.id != current.id &&
                    it.threadId == current.threadId &&
                    it.receivedTimestamp < current.receivedTimestamp
            }
            .maxByOrNull { it.receivedTimestamp }
            ?: return emptyList()

        val previousText = "${previous.subject}\n${previous.bodyText}"
        val currentText = "${current.subject}\n${current.bodyText}"
        return patterns.mapNotNull { pattern ->
            val oldValue = pattern.regex.find(previousText)?.groupValues?.lastOrNull()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val newValue = pattern.regex.find(currentText)?.groupValues?.lastOrNull()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (oldValue.equals(newValue, ignoreCase = true)) null
            else DetectedEmailChange(pattern.label, oldValue, newValue, previous.id)
        }
    }
}
