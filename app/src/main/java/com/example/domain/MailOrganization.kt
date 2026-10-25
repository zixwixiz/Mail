package com.example.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Deterministic, on-device organization helpers for mail list filters and exports. */
enum class MailCategory(val label: String) {
    SCHEDULES("Schedules"),
    WORKSHOP("Workshop"),
    WEBINAR("Webinar"),
    ADMINISTRATION("Administration notice"),
    ADVERTISEMENT("Advertisement"),
    ACADEMIC("Academic"),
    OTHER("Other")
}

object MailOrganization {
    fun categorize(subject: String, sender: String = "", body: String = ""): MailCategory {
        val text = "$subject $sender $body".lowercase(Locale.ROOT)
        return when {
            listOf("webinar", "online seminar", "online-vortrag", "webkonferenz").any(text::contains) -> MailCategory.WEBINAR
            listOf("workshop", "hands-on", "hands on", "praktikumstag").any(text::contains) -> MailCategory.WORKSHOP
            listOf("werbung", "advertisement", "unsubscribe", "rabatt", "angebot", "sponsored").any(text::contains) -> MailCategory.ADVERTISEMENT
            listOf("verwaltung", "administration", "prüfungsamt", "pruefungsamt", "studierendensekretariat", "amtliche mitteilung", "official notice").any(text::contains) -> MailCategory.ADMINISTRATION
            listOf("termin", "deadline", "frist", "appointment", "schedule", "veranstaltung", "einladung", "meeting").any(text::contains) -> MailCategory.SCHEDULES
            listOf("vorlesung", "lecture", "seminar", "research", "forschung", "university", "universität").any(text::contains) -> MailCategory.ACADEMIC
            else -> MailCategory.OTHER
        }
    }

    /** Inclusive age filter: 0=today, 1=last 24 hours, N=last N days. */
    fun receivedWithinDays(receivedTimestamp: Long, days: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (days < 0) return false
        val cutoff = now - days.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        return if (days == 0) {
            val startOfToday = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            receivedTimestamp in startOfToday..now
        } else receivedTimestamp in cutoff..now
    }
}

data class MailExportItem(
    val id: String,
    val subject: String,
    val senderName: String,
    val senderEmail: String,
    val receivedTimestamp: Long,
    val body: String,
    val attachments: List<String> = emptyList(),
    val mediaIncluded: Boolean = false,
    val mediaFileNames: List<String> = emptyList()
)

object MailExportFormatter {
    fun format(items: List<MailExportItem>, format: String, includeMedia: Boolean = false): String =
        when (format.lowercase(Locale.ROOT)) {
            "csv" -> toCsv(items, includeMedia)
            "txt", "text" -> toText(items, includeMedia)
            else -> toMarkdown(items, includeMedia)
        }

    fun toMarkdown(items: List<MailExportItem>, includeMedia: Boolean = false): String =
        items.joinToString("\n\n---\n\n") { item ->
            buildString {
                append("# ").append(item.subject.ifBlank { "(No subject)" }).append("\n\n")
                append("- **From:** ").append(item.senderName).append(" <").append(item.senderEmail).append(">\n")
                append("- **Date:** ").append(date(item.receivedTimestamp)).append("\n")
                append("- **Message ID:** ").append(item.id).append("\n\n")
                append(item.body.trim())
                if (item.attachments.isNotEmpty()) append("\n\n**Attachments:** ").append(item.attachments.joinToString(", "))
                if (includeMedia && item.mediaFileNames.isNotEmpty()) append("\n\n**Media files:** ").append(item.mediaFileNames.joinToString(", "))
            }
        }

    fun toText(items: List<MailExportItem>, includeMedia: Boolean = false): String =
        items.joinToString("\n\n========================================================================\n\n") { item ->
            buildString {
                append("Subject: ").append(item.subject).append("\n")
                append("From: ").append(item.senderName).append(" <").append(item.senderEmail).append(">\n")
                append("Date: ").append(date(item.receivedTimestamp)).append("\n\n")
                append(item.body.trim())
                if (item.attachments.isNotEmpty()) append("\n\nAttachments: ").append(item.attachments.joinToString(", "))
                if (includeMedia && item.mediaFileNames.isNotEmpty()) append("\nMedia: ").append(item.mediaFileNames.joinToString(", "))
            }
        }

    fun toCsv(items: List<MailExportItem>, includeMedia: Boolean = false): String {
        val headers = listOf("id", "subject", "sender_name", "sender_email", "received_date", "body", "attachments") +
            if (includeMedia) listOf("media_files") else emptyList()
        return (listOf(headers) + items.map { item ->
            listOf(item.id, item.subject, item.senderName, item.senderEmail, date(item.receivedTimestamp), item.body, item.attachments.joinToString("; ")) +
                if (includeMedia) listOf(item.mediaFileNames.joinToString("; ")) else emptyList()
        }).joinToString("\n") { row -> row.joinToString(",") { csvEscape(it) } }
    }

    private fun csvEscape(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    private fun date(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(timestamp))
}
