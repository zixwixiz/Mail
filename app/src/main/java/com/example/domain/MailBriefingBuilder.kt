package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.util.Calendar
import java.util.TimeZone

data class MailBriefing(
    val todayCount: Int,
    val todayUnreadCount: Int,
    val todayActionCount: Int,
    val weekCount: Int,
    val weekUnreadCount: Int,
    val weekActionCount: Int,
    val topActionMessages: List<MailMessageEntity> = emptyList()
)

/**
 * Builds deterministic, local-time briefing metrics from cached mail.
 * It does not summarize message content or claim that every action was detected.
 */
object MailBriefingBuilder {
    fun build(
        messages: List<MailMessageEntity>,
        now: Long = System.currentTimeMillis(),
        zoneId: String = TimeZone.getDefault().id
    ): MailBriefing {
        val zone = TimeZone.getTimeZone(zoneId)
        val startOfToday = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfWeek = Calendar.getInstance(zone).apply {
            timeInMillis = startOfToday
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        }.timeInMillis

        val received = messages.filter { it.folder !in setOf("SENT", "DRAFTS", "TRASH") }
        val today = received.filter { it.receivedTimestamp in startOfToday..now }
        val week = received.filter { it.receivedTimestamp in startOfWeek..now }

        val todayActions = today.filter {
            ReadingQueueClassifier.classify(it).priority == ReadingPriority.ACTION_REQUIRED
        }.sortedByDescending { it.receivedTimestamp }

        return MailBriefing(
            todayCount = today.size,
            todayUnreadCount = today.count { !it.isRead },
            todayActionCount = todayActions.size,
            weekCount = week.size,
            weekUnreadCount = week.count { !it.isRead },
            weekActionCount = week.count {
                ReadingQueueClassifier.classify(it).priority == ReadingPriority.ACTION_REQUIRED
            },
            topActionMessages = todayActions.take(3)
        )
    }

    /** Return recent received messages with likely actions first; notes remain linked to source mail. */
    fun notesForPeriod(
        messages: List<MailMessageEntity>,
        days: Int,
        now: Long = System.currentTimeMillis()
    ): List<MailMessageEntity> {
        val start = now - days.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        return messages.asSequence()
            .filter { it.folder !in setOf("SENT", "DRAFTS", "TRASH") }
            .filter { it.receivedTimestamp in start..now }
            .sortedWith(
                compareBy<MailMessageEntity> {
                    when (ReadingQueueClassifier.classify(it).priority) {
                        ReadingPriority.ACTION_REQUIRED -> 0
                        ReadingPriority.IMPORTANT -> 1
                        ReadingPriority.ROUTINE -> 2
                    }
                }.thenByDescending { it.receivedTimestamp }
            )
            .toList()
    }
}
