package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MailPeriodNotesBuilderTest {
    private val now = 1_700_000_000_000L

    private fun mail(
        id: String,
        timestamp: Long,
        body: String = "",
        folder: String = "INBOX",
        isRead: Boolean = false,
        subject: String = "Academic update"
    ) = MailMessageEntity(
        id = id,
        mailboxId = "mailbox-1",
        folder = folder,
        threadId = "",
        subject = subject,
        senderName = "JLU Office",
        senderEmail = "office@example.org",
        recipients = "",
        receivedTimestamp = timestamp,
        bodyText = body,
        bodyHtml = "",
        isRead = isRead,
        isFlagged = false,
        hasAttachments = false
    )

    @Test
    fun filtersBySelectedRollingWindowAndExcludesSentMail() {
        val notes = MailPeriodNotesBuilder.build(
            messages = listOf(
                mail("recent", now - 24L * 60 * 60 * 1000, isRead = false),
                mail("old", now - 4L * 24 * 60 * 60 * 1000),
                mail("sent", now - 60_000, folder = "SENT")
            ),
            days = 3,
            now = now
        )

        assertEquals(1, notes.messageCount)
        assertEquals(1, notes.unreadCount)
        assertEquals("recent", notes.items.single().sourceId)
        assertEquals(0, notes.omittedItemCount)
    }

    @Test
    fun buildsLongerNotesAndExtractsDatesTimesAndPossibleActions() {
        val background = (1..8).map {
            "This paragraph explains the academic process and provides useful context for step $it."
        }
        val important = listOf(
            "Please submit your registration before 30.10.2026 at 09:30 Uhr.",
            "The deadline is binding and late submissions cannot be accepted.",
            "The appointment takes place in room 204.",
            "Please confirm your attendance before the stated date."
        )
        val body = (background + important).joinToString(" ")
        val notes = MailPeriodNotesBuilder.build(
            listOf(mail("action", now - 60_000, body = body)),
            days = 2,
            now = now
        )

        val item = notes.items.single()
        assertTrue("Expected more context than the old 420-character quick read", item.summary.length > 420)
        assertTrue("Detailed notes should separate context from key details", item.summary.contains("Key details from the message:"))
        assertTrue("Detailed notes should preserve a readable line break", item.summary.contains("\n"))
        assertTrue(item.keyPoints.any { it.contains("Dates mentioned") })
        assertTrue(item.keyPoints.any { it.contains("Times mentioned") })
        assertEquals(1, notes.possibleActionCount)
        assertEquals(ReadingPriority.ACTION_REQUIRED, item.priority)
    }

    @Test
    fun prioritizesPossibleActionsAndPreservesReadableBodyParagraphs() {
        val routine = mail("routine", now - 10_000, body = "A routine notice.")
        val action = mail(
            "action",
            now - 30_000,
            body = "Please submit the form before 31.10.2026.",
            subject = "Action required"
        )
        val notes = MailPeriodNotesBuilder.build(listOf(routine, action), days = 1, now = now)
        assertEquals("action", notes.items.first().sourceId)

        val readable = MailTextFormatter.readableBody(
            "<p>First paragraph.</p><p>Second paragraph.</p><ul><li>One item</li><li>Two items</li></ul>"
        )
        assertTrue(readable.contains("First paragraph."))
        assertTrue(readable.contains("Second paragraph."))
        assertTrue(readable.contains("One item"))
        assertTrue(readable.contains("Two items"))
        assertFalse(readable.contains("<p>"))
        assertTrue(readable.contains("\n"))
    }
}
