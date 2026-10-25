package com.example.domain

import com.example.data.local.entities.MailMessageEntity

data class MailQuickSummary(
    val summary: String,
    val keyPoints: List<String>
)

/**
 * Produces source-grounded, extractive mail notes without inventing facts.
 *
 * This is deterministic local text extraction, not a generative AI model. It ranks original
 * sentences by relevance (deadlines/actions, dates, times, amounts) and keeps their source order.
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
        "bitte", "frist", "fristende", "ausschlussfrist", "erforderlich", "antrag", "müssen",
        "must", "required", "submit", "confirm", "response", "rückmeldung", "abgabe"
    )

    fun build(message: MailMessageEntity): MailQuickSummary {
        val plainBody = MailTextFormatter.readableBody(message.bodyText.ifBlank { message.bodyHtml })
        val paragraphs = plainBody
            .split(Regex("\\n\\s*\\n"))
            .map { it.replace(Regex("\\s*\\n\\s*"), " ").trim() }
            .filter { it.isNotBlank() }

        val sentences = paragraphs
            .flatMap { paragraph -> sentenceBoundary.split(paragraph).map(String::trim).filter(String::isNotBlank) }
            .distinct()
        val selectedSentences = sentences.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<String>> { relevanceScore(it.value, it.index) }
                    .thenBy { it.index }
            )
            .take(5)
            .sortedBy { it.index }
            .map { it.value }

        val summary = when {
            plainBody.isBlank() -> "No readable message body is available."
            selectedSentences.isEmpty() -> plainBody.take(MAX_SUMMARY_CHARS)
            else -> selectedSentences.joinToString("\n\n").let { selected ->
                if (selected.length <= MAX_SUMMARY_CHARS) selected
                else selected.take(MAX_SUMMARY_CHARS - 1).trimEnd() + "…"
            }
        }

        val classification = ReadingQueueClassifier.classify(message)
        val keyPoints = buildList {
            if (classification.priority == ReadingPriority.ACTION_REQUIRED) {
                add("Possible action: ${classification.reason}")
            }
            val dates = datePattern.findAll(plainBody).map { it.value }.distinct().take(4).toList()
            val times = timePattern.findAll(plainBody).map { it.value }.distinct().take(4).toList()
            val amounts = amountPattern.findAll(plainBody).map { it.value }.distinct().take(4).toList()
            if (dates.isNotEmpty()) add("Dates mentioned: ${dates.joinToString(", ")}")
            if (times.isNotEmpty()) add("Times mentioned: ${times.joinToString(", ")}")
            if (amounts.isNotEmpty()) add("Amounts mentioned: ${amounts.joinToString(", ")}")
            if (message.attachments.isNotBlank()) {
                add("Attachments: ${message.attachments.lineSequence().filter { it.isNotBlank() }.take(4).joinToString(", ")}")
            }
        }

        return MailQuickSummary(summary, keyPoints)
    }

    private fun relevanceScore(sentence: String, index: Int): Int {
        val lower = sentence.lowercase()
        var score = if (index == 0) 3 else (2 - index / 3).coerceAtLeast(0)
        if (datePattern.containsMatchIn(sentence)) score += 4
        if (timePattern.containsMatchIn(sentence)) score += 3
        if (amountPattern.containsMatchIn(sentence)) score += 2
        if (actionTerms.any(lower::contains)) score += 4
        return score
    }

    private const val MAX_SUMMARY_CHARS = 1400
}
