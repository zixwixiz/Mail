package com.example.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

sealed class SmartActionSuggestion {
    data class CalendarSuggestion(
        val title: String,
        val description: String,
        val startInstant: Long,
        val endInstant: Long,
        val location: String,
        val confidenceReason: String
    ) : SmartActionSuggestion()

    data class TaskSuggestion(
        val title: String,
        val description: String,
        val dueInstant: Long?,
        val priority: String,
        val confidenceReason: String
    ) : SmartActionSuggestion()
}

object SmartExtractor {
    private val monthNumbers = mapOf(
        "januar" to Calendar.JANUARY,
        "februar" to Calendar.FEBRUARY,
        "märz" to Calendar.MARCH,
        "maerz" to Calendar.MARCH,
        "april" to Calendar.APRIL,
        "mai" to Calendar.MAY,
        "juni" to Calendar.JUNE,
        "juli" to Calendar.JULY,
        "august" to Calendar.AUGUST,
        "september" to Calendar.SEPTEMBER,
        "oktober" to Calendar.OCTOBER,
        "november" to Calendar.NOVEMBER,
        "dezember" to Calendar.DECEMBER
    )

    private val explicitDatePattern = Regex(
        """(?iu)\b(?:am\s+)?(?:\w+\s*,\s*)?(\d{1,2})\.\s*(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)(?:\s+(\d{4}))?(?:\s*,)?(?:\s+(?:um\s+)?)?(\d{1,2}):([0-5]\d)\s*Uhr\b"""
    )

    private val dateOnlyPattern = Regex(
        """(?iu)\b(?:am\s+)?(?:\w+\s*,\s*)?(\d{1,2})\.\s*(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)(?:\s+(\d{4}))?\b"""
    )

    private val locationPattern = Regex(
        """(?im)\b(?:Ort|Location)\s*:\s*([^\n]+)"""
    )

    private val roomPattern = Regex(
        """(?iu)\b(?:Seminarraum|Raum|Room)\s+([A-Za-z0-9][A-Za-z0-9 ._-]{0,39})"""
    )

    private data class ParsedDate(
        val instant: Long,
        val hasTime: Boolean
    )

    fun extractSuggestions(subject: String, body: String, emailId: String): List<SmartActionSuggestion> {
        val text = listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) return emptyList()

        val suggestions = mutableListOf<SmartActionSuggestion>()
        val parsedDateTime = parseDate(text, requireTime = true)
        val parsedDateOnly = parseDate(text, requireTime = false)

        val isMeeting = listOf("Kolloquium", "Sprechstunde", "Meeting", "Sitzung", "Besprechung", "Termin")
            .any { text.contains(it, ignoreCase = true) }
        val location = locationPattern.find(body)?.groupValues?.getOrNull(1)?.trim()
            ?.removeSuffix(".")
            ?: roomPattern.find(body)?.value?.trim()
            ?: ""

        if (isMeeting && parsedDateTime != null) {
            suggestions += SmartActionSuggestion.CalendarSuggestion(
                title = subject.trim().ifBlank { "Calendar event from email" },
                description = "Review the extracted date and time before adding this event.",
                startInstant = parsedDateTime.instant,
                endInstant = parsedDateTime.instant + 60 * 60 * 1000L,
                location = location,
                confidenceReason = "Explicit German date and time found in the email."
            )
        }

        val hasActionKeyword = listOf(
            "Frist", "Fristende", "Ausschlussfrist", "Prüfungsanmeldung",
            "Antrag", "bitte", "erforderlich", "Ticket", "Deadline"
        ).any { text.contains(it, ignoreCase = true) }

        if (hasActionKeyword) {
            val priority = if (
                listOf("Frist", "Fristende", "Ausschlussfrist", "Deadline")
                    .any { text.contains(it, ignoreCase = true) }
            ) "HIGH" else "NORMAL"

            suggestions += SmartActionSuggestion.TaskSuggestion(
                title = subject.trim().ifBlank { "Follow up on email" },
                description = "Generated from this email; review the task details before applying.",
                dueInstant = parsedDateOnly?.instant,
                priority = priority,
                confidenceReason = when {
                    parsedDateTime != null -> "Action keyword plus explicit deadline date and time found."
                    parsedDateOnly != null -> "Action keyword plus explicit deadline date found; time was not specified."
                    else -> "Action keyword found, but no explicit deadline date was detected."
                }
            )
        }

        return suggestions
    }

    private fun parseDate(text: String, requireTime: Boolean): ParsedDate? {
        val match = (if (requireTime) explicitDatePattern.find(text) else dateOnlyPattern.find(text))
            ?: return null

        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = monthNumbers[match.groupValues[2].lowercase(Locale.GERMANY)] ?: return null
        val explicitYear = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()

        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val now = Calendar.getInstance(zone)

        fun candidateFor(year: Int, hour: Int, minute: Int): Long? {
            val calendar = Calendar.getInstance(zone).apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return if (calendar.get(Calendar.YEAR) == year &&
                calendar.get(Calendar.MONTH) == month &&
                calendar.get(Calendar.DAY_OF_MONTH) == day
            ) calendar.timeInMillis else null
        }

        val hasTime = match.groupValues.size > 5 && match.groupValues[4].isNotBlank()
        if (requireTime && !hasTime) return null

        val hour = if (hasTime) match.groupValues[4].toInt() else 23
        val minute = if (hasTime) match.groupValues[5].toInt() else 59

        var year = explicitYear ?: now.get(Calendar.YEAR)
        var instant = candidateFor(year, hour, minute) ?: return null

        if (explicitYear == null && instant < now.timeInMillis) {
            year += 1
            instant = candidateFor(year, hour, minute) ?: return null
        }

        return ParsedDate(instant, hasTime)
    }
}
