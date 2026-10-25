package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailDigestBuilderTest {
    private fun mail(
        id: String,
        receivedAt: Long,
        folder: String = "INBOX",
        unread: Boolean = true,
        subject: String = "Seminar update",
        body: String = "The seminar is on 12 November 2026 at 10:30. Please reply by the deadline."
    ) = MailMessageEntity(
        id = id,
        mailboxId = "mailbox",
        folder = folder,
        threadId = "",
        subject = subject,
        senderName = "University",
        senderEmail = "university@example.org",
        recipients = "",
        receivedTimestamp = receivedAt,
        bodyText = body,
        bodyHtml = "",
        isRead = !unread,
        isFlagged = false,
        hasAttachments = false
    )

    @Test
    fun rollingWindowExcludesOlderAndFutureMessagesAndSentFolders() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val digest = MailDigestBuilder.build(
            listOf(
                mail("recent", now - day),
                mail("old", now - 5 * day),
                mail("future", now + 60_000),
                mail("sent", now - 60_000, folder = "SENT")
            ),
            days = 3,
            now = now
        )

        assertEquals(1, digest.messageCount)
        assertEquals("recent", digest.entries.single().message.id)
    }

    @Test
    fun digestCountsUnreadAndPossibleActionsAcrossAllMatchingMessages() {
        val now = 1_800_000_000_000L
        val digest = MailDigestBuilder.build(
            listOf(
                mail("action", now - 1_000, subject = "Deadline", unread = true),
                mail("routine", now - 2_000, subject = "FYI", unread = false)
            ),
            days = 1,
            now = now
        )

        assertEquals(2, digest.messageCount)
        assertEquals(1, digest.unreadCount)
        assertTrue(digest.actionCount >= 1)
    }

    @Test
    fun digestLimitsVisibleNotesButKeepsFullMessageCount() {
        val now = 1_800_000_000_000L
        val messages = (1..8).map { index ->
            mail("m$index", now - index * 1_000L)
        }
        val digest = MailDigestBuilder.build(messages, days = 2, now = now, maxEntries = 4)

        assertEquals(8, digest.messageCount)
        assertEquals(4, digest.entries.size)
    }

    @Test
    fun daysAreClampedToSupportedRange() {
        val now = 1_800_000_000_000L
        assertEquals(1, MailDigestBuilder.build(emptyList(), 0, now).days)
        assertEquals(30, MailDigestBuilder.build(emptyList(), 500, now).days)
        assertFalse(MailDigestBuilder.build(emptyList(), 3, now).entries.isNotEmpty())
    }
}
