package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MailOrganizationTest {
    @Test fun categorizesCommonMailTypes() {
        assertEquals(MailCategory.WEBINAR, MailOrganization.categorize("Invitation to webinar"))
        assertEquals(MailCategory.WORKSHOP, MailOrganization.categorize("Practical workshop"))
        assertEquals(MailCategory.ADMINISTRATION, MailOrganization.categorize("Mitteilung vom Prüfungsamt"))
        assertEquals(MailCategory.ADVERTISEMENT, MailOrganization.categorize("Special offer - unsubscribe"))
        assertEquals(MailCategory.SCHEDULES, MailOrganization.categorize("Appointment deadline"))
    }

    @Test fun dateFilterUsesLocalCalendarDayForTodayAndRollingWindowOtherwise() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 25, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val todayMorning = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 8) }.timeInMillis
        val yesterday = now - 25L * 60 * 60 * 1000
        assertTrue(MailOrganization.receivedWithinDays(todayMorning, 0, now))
        assertFalse(MailOrganization.receivedWithinDays(yesterday, 0, now))
        assertTrue(MailOrganization.receivedWithinDays(yesterday, 2, now))
    }

    @Test fun csvEscapesQuotesAndMarkdownKeepsBodyParagraphs() {
        val item = MailExportItem("id1", "Hello, \"World\"", "Sender", "sender@example.org", 0L, "First paragraph\n\nSecond paragraph")
        assertTrue(MailExportFormatter.toCsv(listOf(item)).contains("\"\"World\"\""))
        val markdown = MailExportFormatter.toMarkdown(listOf(item))
        assertTrue(markdown.contains("First paragraph\n\nSecond paragraph"))
        assertEquals("", MailExportFormatter.format(emptyList(), "txt"))
    }
}
