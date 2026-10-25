package com.example.domain

import com.example.data.local.entities.MailMessageEntity
data class MailQuickSummary(
    val summary: String,
    val keyPoints: List<String>
)

/**
 * Produces a longer, traceable note from cached message text using deterministic extraction.
 * This is not a generative AI summary: it selects source sentences and extracts explicit signals.
 */
object MailQuickSummaryBuilder {
    private val sentenceBoundary = Regex("""(?<=[.!?])\s+""")
    private val datePattern = Regex(
        """(?iu)\b\d{1,2}[./-]\d{1,2}(?:[./-]\d{2,4})?\b|\b\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December|Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\b"""
    )
    private val timePattern = Regex("""(?iu)\b\d{1,2}:[0-5]\d(?:\s*(?:am|pm|Uhr))?\b""")
    private val amountPattern = Regex("""(?iu)(?:€|EUR|USD|\$|£)\s?\d[\d.,]*|\d[\d.,]*\s?(?:€|EUR|USD|\$|£)\b""")
    private val actionTerms = listOf(
        "deadline", "due by", "due date", "action required", "please reply", "please submit",
        "please register", "register by", "submit by", "confirm", "must", "required",
        "frist", "fristende", "ausschlussfrist", "bitte", "anmeldung", "einreichen",
        "rückmeldung", "antrag", "dringend", "urgent", "appointment", "meeting",
        "wartungsarbeiten", "maintenance", "abgesagt", "cancelled", "verschoben", "rescheduled"
    )
    private const val MAX_SUMMARY_CHARS = 2000
    private const val MAX_SUMMARY_SENTENCES = 8

    fun build(message: MailMessageEntity): MailQuickSummary {
        val readableBody = MailTextFormatter.readableBody(
            message.bodyText.ifBlank { message.bodyHtml }
        )
        val searchable = readableBody.replace(Regex("[\\t ]+"), " ").trim()
        val normalizedForSentences = searchable.replace(Regex("\\s+"), " ")
        val sentences = sentenceBoundary.split(normalizedForSentences).filter { it.isNotBlank() }

        // Preserve the opening context, then add later sentences that contain likely decisions,
        // dates, times or other concrete details so long notices are not reduced to the first two lines.
        val selectedSentences = buildList {
            addAll(sentences.take(4))
            sentences.drop(4)
                .filter { sentence ->
                    actionTerms.any { sentence.contains(it, ignoreCase = true) } ||
                        datePattern.containsMatchIn(sentence) ||
                        timePattern.containsMatchIn(sentence) ||
                        amountPattern.containsMatchIn(sentence)
                }
                .take(MAX_SUMMARY_SENTENCES)
                .forEach { if (it !in this) add(it) }
        }.take(MAX_SUMMARY_SENTENCES)

        val summary = when {
            searchable.isBlank() -> "No readable message body is available."
            sentences.isEmpty() -> searchable.take(MAX_SUMMARY_CHARS)
            else -> {
                val opening = selectedSentences.take(3).joinToString(" ")
                val details = selectedSentences.drop(3)
                buildString {
                    append(opening)
                    if (details.isNotEmpty()) {
                        append("\n\nKey details from the message:\n")
                        details.forEach { append("• ").append(it).append('\n') }
                    }
                }.trim().take(MAX_SUMMARY_CHARS)
                    .ifBlank { searchable.take(MAX_SUMMARY_CHARS) }
            }
        }

        val classification = ReadingQueueClassifier.classify(message)
        val keyPoints = buildList {
            if (classification.priority == ReadingPriority.ACTION_REQUIRED) {
                add("Possible action: ${classification.reason}")
            } else if (classification.priority == ReadingPriority.IMPORTANT) {
                add("Follow-up signal: ${classification.reason}")
            }

            // Extract verifiable facts from normalized plain text as well as the reader view.
            // The reader formatter may insert paragraph markers into HTML-derived content.
            val factText = MailTextFormatter.plainText(message.bodyText.ifBlank { message.bodyHtml })
            val dates = datePattern.findAll(factText).map { it.value }.distinct().take(5).toList()
            val times = timePattern.findAll(factText).map { it.value }.distinct().take(5).toList()
            val amounts = amountPattern.findAll(factText).map { it.value }.distinct().take(5).toList()
            if (dates.isNotEmpty()) add("Dates mentioned (verify in source): ${dates.joinToString(", ")}")
            if (times.isNotEmpty()) add("Times mentioned (verify time zone): ${times.joinToString(", ")}")
            if (amounts.isNotEmpty()) add("Amounts mentioned: ${amounts.joinToString(", ")}")
            if (message.attachments.isNotBlank()) {
                val attachments = message.attachments.lineSequence().filter { it.isNotBlank() }.take(5).toList()
                add("Attachment references: ${attachments.joinToString(", ")}")
            }
        }

        return MailQuickSummary(summary, keyPoints)
    }
}
