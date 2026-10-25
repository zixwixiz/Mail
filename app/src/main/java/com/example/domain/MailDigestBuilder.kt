package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.util.Locale

data class MailDigestEntry(
    val message: MailMessageEntity,
    val category: MailCategory,
    val priority: ReadingPriority,
    val summary: MailQuickSummary
)

data class MailDigest(
    val days: Int,
    val messageCount: Int,
    val unreadCount: Int,
    val actionCount: Int,
    val entries: List<MailDigestEntry>
)

/**
 * Builds a transparent, local digest for a rolling day window.
 *
 * This is extractive and rules-based, not a generative AI summary. Keep original messages
 * reachable so readers can verify any dates, amounts, decisions, or possible actions.
 */
object MailDigestBuilder {
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private val excludedFolders = setOf("SENT", "DRAFTS", "TRASH")

    fun build(
        messages: List<MailMessageEntity>,
        days: Int,
        now: Long = System.currentTimeMillis(),
        maxEntries: Int = 6
    ): MailDigest {
        val safeDays = days.coerceIn(1, 30)
        val cutoff = now - safeDays * MILLIS_PER_DAY
        val received = messages
            .asSequence()
            .filter { it.folder.uppercase(Locale.ROOT) !in excludedFolders }
            .filter { it.receivedTimestamp in cutoff..now }
            .distinctBy { it.id }
            .sortedByDescending { it.receivedTimestamp }
            .toList()

        val entries = received.take(maxEntries.coerceAtLeast(0)).map { message ->
            MailDigestEntry(
                message = message,
                category = MailOrganization.categorize(
                    message.subject,
                    message.senderName + " " + message.senderEmail,
                    message.bodyText.ifBlank { message.bodyHtml }
                ),
                priority = ReadingQueueClassifier.classify(message).priority,
                summary = MailQuickSummaryBuilder.build(message)
            )
        }

        return MailDigest(
            days = safeDays,
            messageCount = received.size,
            unreadCount = received.count { !it.isRead },
            actionCount = received.count {
                ReadingQueueClassifier.classify(it).priority == ReadingPriority.ACTION_REQUIRED
            },
            entries = entries
        )
    }
}
