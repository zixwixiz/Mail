package com.example.domain

import com.example.data.local.entities.MailMessageEntity

data class MailQuickSummary(
    val summary: String,
    val keyPoints: List<String>
)

/**
 * Extractive quick overview that never invents facts. This is a deterministic fallback,
 * not a generative AI summary.
 */
object MailQuickSummaryBuilder {
    private val sentenceBoundary = Regex("""(?<=[.!?])\s+""")
    private val datePattern = Regex(
        """(?iu)\b\d{1,2}[./-]\d{1,2}(?:[./-]\d{2,4})?\b|\b\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December|Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\b"""
    )
    private val timePattern = Regex("""(?iu)\b\d{1,2}:[0-5]\d(?:\s*(?:am|pm|Uhr))?\b""")
    private val amountPattern = Regex("""(?iu)(?:€|EUR|USD|\$|£)\s?\d[\d.,]*|\d[\d.,]*\s?(?:€|EUR|USD|\$|£)\b""")

    fun build(message: MailMessageEntity): MailQuickSummary {
        val plainBody = MailTextFormatter.plainText(message.bodyText.ifBlank { message.bodyHtml })
        val normalized = plainBody.replace(Regex("\\s+"), " ").trim()
        val sentences = sentenceBoundary.split(normalized).filter { it.isNotBlank() }
        val summary = when {
            normalized.isBlank() -> "No readable message body is available."
            sentences.isEmpty() -> normalized.take(420)
            else -> sentences.take(2).joinToString(" ").take(420)
        }

        val keyPoints = buildList {
            val priority = ReadingQueueClassifier.classify(message).priority
            if (priority == ReadingPriority.ACTION_REQUIRED) {
                add("Possible action or deadline wording detected.")
            }
            val dates = datePattern.findAll(plainBody).map { it.value }.distinct().take(3).toList()
            val times = timePattern.findAll(plainBody).map { it.value }.distinct().take(3).toList()
            val amounts = amountPattern.findAll(plainBody).map { it.value }.distinct().take(3).toList()
            if (dates.isNotEmpty()) add("Dates mentioned: ${dates.joinToString(", ")}")
            if (times.isNotEmpty()) add("Times mentioned: ${times.joinToString(", ")}")
            if (amounts.isNotEmpty()) add("Amounts mentioned: ${amounts.joinToString(", ")}")
            if (message.attachments.isNotBlank()) {
                add("Attachments: ${message.attachments.lineSequence().take(3).joinToString(", ")}")
            }
        }
        return MailQuickSummary(summary, keyPoints)
    }
}
