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
        val confidenceReason: String,
        val sourceTimeZoneId: String = "Europe/Berlin"
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
        "dezember" to Calendar.DECEMBER,
        "january" to Calendar.JANUARY,
        "february" to Calendar.FEBRUARY,
        "march" to Calendar.MARCH,
        "may" to Calendar.MAY,
        "june" to Calendar.JUNE,
        "july" to Calendar.JULY,
        "august" to Calendar.AUGUST,
        "september" to Calendar.SEPTEMBER,
        "october" to Calendar.OCTOBER,
        "november" to Calendar.NOVEMBER,
        "december" to Calendar.DECEMBER
    )

    private val explicitDatePattern = Regex(
        """(?iu)\b(?:am\s+)?(?:\w+\s*,\s*)?(\d{1,2})\.\s*(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)(?:\s+(\d{4}))?(?:\s*,)?(?:\s+(?:um\s+)?)?(\d{1,2}):([0-5]\d)\s*Uhr\b"""
    )

    private val dateOnlyPattern = Regex(
        """(?iu)\b(?:am\s+)?(?:\w+\s*,\s*)?(\d{1,2})\.\s*(Januar|Februar|März|Maerz|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)(?:\s+(\d{4}))?\b"""
    )

    private val englishDayFirstDateTimePattern = Regex(
        """(?iu)\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(January|February|March|April|May|June|July|August|September|October|November|December)(?:\s+(\d{4}))?\s+(?:at\s+)?(\d{1,2}):([0-5]\d)\s*(am|pm)?\b"""
    )

    private val englishMonthFirstDateTimePattern = Regex(
        """(?iu)\b(?:on\s+)?(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\s+(?:at\s+)?(\d{1,2}):([0-5]\d)\s*(am|pm)?\b"""
    )

    private val englishDayFirstDatePattern = Regex(
        """(?iu)\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(January|February|March|April|May|June|July|August|September|October|November|December)(?:\s+(\d{4}))?\b"""
    )

    private val englishMonthFirstDatePattern = Regex(
        """(?iu)\b(?:on\s+)?(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b"""
    )

    private val relativeDatePattern = Regex(
        """(?iu)\b(today|tomorrow|heute|morgen|next\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|am\s+nächsten\s+(?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag))\b"""
    )

    private val relativeTimePattern = Regex(
        """(?iu)\b(?:(?:at|um)\s+)?(\d{1,2})(?::([0-5]\d))?\s*(am|pm|Uhr)?\b"""
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

    fun extractSuggestions(
        subject: String,
        body: String,
        emailId: String,
        referenceTimestamp: Long = System.currentTimeMillis(),
        sourceTimeZoneId: String = "Europe/Berlin"
    ): List<SmartActionSuggestion> {
        val text = listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) return emptyList()

        val suggestions = mutableListOf<SmartActionSuggestion>()
        val parsedDateTime = parseDate(text, requireTime = true, referenceTimestamp = referenceTimestamp, sourceTimeZoneId = sourceTimeZoneId)
        val parsedDateOnly = parseDate(text, requireTime = false, referenceTimestamp = referenceTimestamp, sourceTimeZoneId = sourceTimeZoneId)

        val isMeeting = listOf("Kolloquium", "Sprechstunde", "Meeting", "Sitzung", "Besprechung", "Termin", "appointment", "interview", "conference", "webinar", "call")
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
                confidenceReason = "Explicit date and time found in the email; verify the timezone and details.",
                sourceTimeZoneId = sourceTimeZoneId
            )
        }

        val hasActionKeyword = listOf(
            "Frist", "Fristende", "Ausschlussfrist", "Prüfungsanmeldung",
            "Antrag", "bitte", "erforderlich", "Ticket", "Deadline", "please", "submit",
            "required", "due date", "due by", "action required", "payment due", "application deadline"
        ).any { text.contains(it, ignoreCase = true) }

        if (hasActionKeyword) {
            val priority = if (
                listOf("Frist", "Fristende", "Ausschlussfrist", "Deadline", "due date", "due by", "payment due", "application deadline")
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

    private fun parseDate(
        text: String,
        requireTime: Boolean,
        referenceTimestamp: Long,
        sourceTimeZoneId: String
    ): ParsedDate? {
        data class DateParts(
            val day: Int,
            val monthName: String,
            val year: Int?,
            val hour: Int = 23,
            val minute: Int = 59,
            val meridiem: String? = null,
            val hasTime: Boolean = false
        )

        val relativeMatch = relativeDatePattern.find(text)
        if (relativeMatch != null) {
            val zone = TimeZone.getTimeZone(sourceTimeZoneId)
            val reference = Calendar.getInstance(zone).apply { timeInMillis = referenceTimestamp }
            val timeMatch = relativeTimePattern.find(text)
            if (requireTime && timeMatch == null) return null

            val relativeToken = relativeMatch.groupValues[1].lowercase(Locale.ROOT)
            val targetWeekday = when {
                relativeMatch.groupValues[0].contains("next", ignoreCase = true) ||
                    relativeMatch.groupValues[0].contains("nächsten", ignoreCase = true) -> {
                    when (
                        relativeMatch.groupValues[0]
                            .substringAfterLast(' ')
                            .lowercase(Locale.ROOT)
                    ) {
                        "monday", "montag" -> Calendar.MONDAY
                        "tuesday", "dienstag" -> Calendar.TUESDAY
                        "wednesday", "mittwoch" -> Calendar.WEDNESDAY
                        "thursday", "donnerstag" -> Calendar.THURSDAY
                        "friday", "freitag" -> Calendar.FRIDAY
                        "saturday", "samstag" -> Calendar.SATURDAY
                        "sunday", "sonntag" -> Calendar.SUNDAY
                        else -> null
                    }
                }
                else -> null
            }

            when {
                relativeToken == "tomorrow" || relativeToken == "morgen" -> reference.add(Calendar.DAY_OF_MONTH, 1)
                targetWeekday != null -> {
                    val daysAhead = (targetWeekday - reference.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    reference.add(Calendar.DAY_OF_MONTH, if (daysAhead == 0) 7 else daysAhead)
                }
                else -> Unit
            }

            val hasTime = timeMatch != null
            var hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 23
            val minute = timeMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: if (hasTime) 0 else 59
            when (timeMatch?.groupValues?.get(3)?.lowercase(Locale.ROOT)) {
                "am" -> if (hour == 12) hour = 0
                "pm" -> if (hour in 1..11) hour += 12
            }
            if (hour !in 0..23 || minute !in 0..59) return null

            reference.set(Calendar.HOUR_OF_DAY, hour)
            reference.set(Calendar.MINUTE, minute)
            reference.set(Calendar.SECOND, 0)
            reference.set(Calendar.MILLISECOND, 0)
            return ParsedDate(reference.timeInMillis, hasTime)
        }

        val timedParts = explicitDatePattern.find(text)?.let { match ->
            DateParts(
                day = match.groupValues[1].toIntOrNull() ?: return@let null,
                monthName = match.groupValues[2],
                year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull(),
                hour = match.groupValues[4].toIntOrNull() ?: return@let null,
                minute = match.groupValues[5].toIntOrNull() ?: return@let null,
                hasTime = true
            )
        } ?: englishDayFirstDateTimePattern.find(text)?.let { match ->
            DateParts(
                day = match.groupValues[1].toIntOrNull() ?: return@let null,
                monthName = match.groupValues[2],
                year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull(),
                hour = match.groupValues[4].toIntOrNull() ?: return@let null,
                minute = match.groupValues[5].toIntOrNull() ?: return@let null,
                meridiem = match.groupValues[6].takeIf { it.isNotBlank() },
                hasTime = true
            )
        } ?: englishMonthFirstDateTimePattern.find(text)?.let { match ->
            DateParts(
                day = match.groupValues[2].toIntOrNull() ?: return@let null,
                monthName = match.groupValues[1],
                year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull(),
                hour = match.groupValues[4].toIntOrNull() ?: return@let null,
                minute = match.groupValues[5].toIntOrNull() ?: return@let null,
                meridiem = match.groupValues[6].takeIf { it.isNotBlank() },
                hasTime = true
            )
        }

        val dateParts = timedParts ?: dateOnlyPattern.find(text)?.let { match ->
            DateParts(
                day = match.groupValues[1].toIntOrNull() ?: return@let null,
                monthName = match.groupValues[2],
                year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
            )
        } ?: englishDayFirstDatePattern.find(text)?.let { match ->
            DateParts(
                day = match.groupValues[1].toIntOrNull() ?: return@let null,
                monthName = match.groupValues[2],
                year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
            )
        } ?: englishMonthFirstDatePattern.find(text)?.let { match ->
            DateParts(
                day = match.groupValues[2].toIntOrNull() ?: return@let null,
                monthName = match.groupValues[1],
                year = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
            )
        } ?: return null

        if (requireTime && !dateParts.hasTime) return null
        val month = monthNumbers[dateParts.monthName.lowercase(Locale.ROOT)] ?: return null
        val zone = TimeZone.getTimeZone(sourceTimeZoneId)
        val now = Calendar.getInstance(zone).apply { timeInMillis = referenceTimestamp }

        var hour = if (dateParts.hasTime) dateParts.hour else 23
        val minute = if (dateParts.hasTime) dateParts.minute else 59
        when (dateParts.meridiem?.lowercase(Locale.ROOT)) {
            "am" -> if (hour == 12) hour = 0
            "pm" -> if (hour in 1..11) hour += 12
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        fun candidateFor(year: Int): Long? {
            val calendar = Calendar.getInstance(zone).apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dateParts.day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return runCatching { calendar.timeInMillis }.getOrNull()
        }

        var year = dateParts.year ?: now.get(Calendar.YEAR)
        var instant = candidateFor(year) ?: return null
        if (dateParts.year == null && instant < referenceTimestamp) {
            year += 1
            instant = candidateFor(year) ?: return null
        }
        return ParsedDate(instant, dateParts.hasTime)
    }
}
