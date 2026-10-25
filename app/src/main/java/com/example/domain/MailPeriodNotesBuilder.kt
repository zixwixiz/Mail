package com.example.domain

import com.example.data.local.entities.MailMessageEntity

data class MailPeriodNoteItem(
    val sourceId: String,
    val subject: String,
    val sender: String,
    val receivedTimestamp: Long,
    val priority: ReadingPriority,
    val summary: String,
    val keyPoints: List<String>
)

data class MailPeriodCategoryCount(
    val label: String,
    val count: Int
)

data class MailPeriodNotes(
    val days: Int,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val possibleActionCount: Int,
    val categoryCounts: List<MailPeriodCategoryCount>,
    val items: List<MailPeriodNoteItem>,
    val omittedItemCount: Int
)

/**
 * Creates a traceable digest from cached messages. This is deterministic, on-device
 * extraction and aggregation; it is not a generative AI summary.
 */
object MailPeriodNotesBuilder {
    private val excludedFolders = setOf("SENT", "DRAFTS", "TRASH")
    private const val MAX_INDIVIDUAL_NOTES = 8

    fun build(
        messages: List<MailMessageEntity>,
        days: Int,
        now: Long = System.currentTimeMillis()
    ): MailPeriodNotes {
        val safeDays = days.coerceAtLeast(1)
        val start = now - safeDays.toLong() * 24L * 60L * 60L * 1000L
        val received = messages
            .filter { it.folder.uppercase(java.util.Locale.ROOT) !in excludedFolders }
            .filter { it.receivedTimestamp in start..now }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<MailMessageEntity> {
                    when (ReadingQueueClassifier.classify(it).priority) {
                        ReadingPriority.ACTION_REQUIRED -> 0
                        ReadingPriority.IMPORTANT -> 1
                        ReadingPriority.ROUTINE -> 2
                    }
                }.thenByDescending { it.receivedTimestamp }
            )

        val categories = received
            .groupingBy {
                MailOrganization.categorize(
                    it.subject,
                    "${it.senderName} ${it.senderEmail}",
                    it.bodyText.ifBlank { it.bodyHtml }
                ).label
            }
            .eachCount()
            .map { MailPeriodCategoryCount(it.key, it.value) }
            .sortedWith(compareByDescending<MailPeriodCategoryCount> { it.count }.thenBy { it.label })

        val notes = received.take(MAX_INDIVIDUAL_NOTES).map { message ->
            val summary = MailQuickSummaryBuilder.build(message)
            MailPeriodNoteItem(
                sourceId = message.id,
                subject = MailTextFormatter.plainText(message.subject).ifBlank { "(No subject)" },
                sender = message.senderName.ifBlank { message.senderEmail.ifBlank { "Unknown sender" } },
                receivedTimestamp = message.receivedTimestamp,
                priority = ReadingQueueClassifier.classify(message).priority,
                summary = summary.summary,
                keyPoints = summary.keyPoints
            )
        }

        return MailPeriodNotes(
            days = safeDays,
            startTimestamp = start,
            endTimestamp = now,
            messageCount = received.size,
            unreadCount = received.count { !it.isRead },
            possibleActionCount = received.count {
                ReadingQueueClassifier.classify(it).priority == ReadingPriority.ACTION_REQUIRED
            },
            categoryCounts = categories,
            items = notes,
            omittedItemCount = (received.size - notes.size).coerceAtLeast(0)
        )
    }
}
