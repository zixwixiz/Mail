package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale

data class ParsedMailSearchQuery(
    val text: String,
    val from: String? = null,
    val subject: String? = null,
    val hasAttachment: Boolean = false,
    val unreadOnly: Boolean = false,
    val starredOnly: Boolean = false,
    val folder: String? = null,
    val afterTimestamp: Long? = null,
    val beforeTimestamp: Long? = null
) {
    fun matches(message: MailMessageEntity): Boolean {
        if (!from.isNullOrBlank() &&
            !message.senderName.contains(from, ignoreCase = true) &&
            !message.senderEmail.contains(from, ignoreCase = true)
        ) return false
        if (!subject.isNullOrBlank() && !message.subject.contains(subject, ignoreCase = true)) return false
        if (hasAttachment && !message.hasAttachments) return false
        if (unreadOnly && message.isRead) return false
        if (starredOnly && !message.isFlagged) return false
        if (folder == "ATTACHMENTS" && !message.hasAttachments) return false
        if (folder != null && folder != "ATTACHMENTS" && message.folder.uppercase(Locale.ROOT) != folder) return false
        if (afterTimestamp != null && message.receivedTimestamp < afterTimestamp) return false
        if (beforeTimestamp != null && message.receivedTimestamp >= beforeTimestamp) return false
        return true
    }
}

/**
 * Parses a small Gmail-style search syntax locally. Unknown operators remain ordinary text.
 * Dates are interpreted in the device's current timezone.
 */
object MailSearchQueryParser {
    private val operatorPattern = Regex("""(?i)\b(from|subject|has|is|in|after|before):("[^"]+"|\S+)""")
    private val whitespace = Regex("""\s+""")
    private val folders = mapOf(
        "inbox" to "INBOX",
        "sent" to "SENT",
        "drafts" to "DRAFTS",
        "trash" to "TRASH",
        "archive" to "ARCHIVE",
        "attachments" to "ATTACHMENTS"
    )

    fun parse(query: String): ParsedMailSearchQuery {
        var from: String? = null
        var subject: String? = null
        var hasAttachment = false
        var unreadOnly = false
        var starredOnly = false
        var folder: String? = null
        var afterTimestamp: Long? = null
        var beforeTimestamp: Long? = null
        val recognized = mutableListOf<MatchResult>()

        operatorPattern.findAll(query).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            val value = match.groupValues[2].removeSurrounding("\"").trim()
            val isRecognized = when (key) {
                "from" -> value.isNotBlank().also { if (it && from == null) from = value }
                "subject" -> value.isNotBlank().also { if (it && subject == null) subject = value }
                "has" -> value.equals("attachment", true).also { if (it) hasAttachment = true }
                "is" -> when (value.lowercase(Locale.ROOT)) {
                    "unread" -> true.also { unreadOnly = true }
                    "starred" -> true.also { starredOnly = true }
                    else -> false
                }
                "in" -> folders[value.lowercase(Locale.ROOT)]?.let {
                    if (folder == null) folder = it
                    true
                } ?: false
                "after" -> parseDateBoundary(value, startOfDay = true)?.let {
                    if (afterTimestamp == null) afterTimestamp = it
                    true
                } ?: false
                "before" -> parseDateBoundary(value, startOfDay = true)?.let {
                    if (beforeTimestamp == null) beforeTimestamp = it
                    true
                } ?: false
                else -> false
            }
            if (isRecognized) recognized += match
        }

        var text = query
        recognized.sortedByDescending { it.range.first }.forEach { match ->
            text = text.removeRange(match.range)
        }
        return ParsedMailSearchQuery(
            text = whitespace.replace(text, " ").trim(),
            from = from,
            subject = subject,
            hasAttachment = hasAttachment,
            unreadOnly = unreadOnly,
            starredOnly = starredOnly,
            folder = folder,
            afterTimestamp = afterTimestamp,
            beforeTimestamp = beforeTimestamp
        )
    }

    private fun parseDateBoundary(value: String, startOfDay: Boolean): Long? {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val parsedDate = runCatching { formatter.parse(value) }.getOrNull() ?: return null
        return runCatching {
            Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = parsedDate.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }
}
