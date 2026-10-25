package com.example.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entities.MailMessageEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MailQuickSummaryBuilderTest {
    private fun message(body: String) = MailMessageEntity(
        id = "mail-1",
        mailboxId = "mailbox",
        folder = "INBOX",
        threadId = "",
        subject = "Registration deadline",
        senderName = "University",
        senderEmail = "university@example.org",
        recipients = "",
        receivedTimestamp = 1_800_000_000_000L,
        bodyText = body,
        bodyHtml = "",
        isRead = false,
        isFlagged = false,
        hasAttachments = false
    )

    @Test
    fun notesPreferImportantSentencesAndIncludeDetectedFacts() {
        val body = "Dear students.\n\nA general introduction with no deadline.\n\nRegistration closes on 12 November 2026 at 10:30. Please submit the form by the deadline.\n\nThank you."
        val notes = MailQuickSummaryBuilder.build(message(body))

        assertTrue(notes.summary.contains("Registration closes on 12 November 2026 at 10:30."))
        assertTrue(notes.summary.contains("Please submit the form by the deadline."))
        assertTrue(notes.keyPoints.any { it.contains("12 November 2026") })
        assertTrue(notes.keyPoints.any { it.contains("10:30") })
    }

    @Test
    fun readableBodyKeepsParagraphsAndRendersListItemsAsBullets() {
        val body = "<p>First paragraph.</p><p>Second paragraph.</p><ul><li>First item</li><li>Second item</li></ul>"
        val readable = MailTextFormatter.readableBody(body)

        assertTrue(readable.contains("First paragraph."))
        assertTrue(readable.contains("Second paragraph."))
        assertTrue(readable.contains("• First item"))
        assertTrue(readable.contains("• Second item"))
        assertTrue(readable.contains("\n"))
    }
}
