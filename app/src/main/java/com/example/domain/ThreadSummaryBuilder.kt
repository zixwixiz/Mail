package com.example.domain

import com.example.data.local.entities.MailMessageEntity

data class ThreadSummary(
    val messageCount: Int,
    val participants: List<String>,
    val latestSubject: String,
    val latestSummary: String
)

/**
 * Builds compact conversation context from cached messages without inventing decisions.
 */
object ThreadSummaryBuilder {
    fun build(messages: List<MailMessageEntity>): ThreadSummary? {
        val ordered = messages
            .filter { it.threadId.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.receivedTimestamp }
        if (ordered.size < 2) return null

        val latest = ordered.last()
        val participants = ordered.map { it.senderName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
        return ThreadSummary(
            messageCount = ordered.size,
            participants = participants,
            latestSubject = latest.subject.ifBlank { "(no subject)" },
            latestSummary = MailQuickSummaryBuilder.build(latest).summary
        )
    }
}
