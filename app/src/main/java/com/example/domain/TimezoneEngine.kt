package com.example.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DualTimeRepresentation(
    val instantEpochMs: Long,
    val jluBerlinTimeFormatted: String,
    val jluBerlinTimeZoneName: String,
    val userLocalTimeFormatted: String,
    val userLocalTimeZoneId: String,
    val timeDifferenceText: String
)

object TimezoneEngine {
    const val JLU_CAMPUS_ZONE = "Europe/Berlin"

    val POPULAR_TIMEZONES = listOf(
        "Europe/Berlin" to "JLU Campus (Gießen / Berlin)",
        "Asia/Kolkata" to "India (IST, Kolkata/Delhi)",
        "America/New_York" to "US Eastern (EST/EDT)",
        "America/Los_Angeles" to "US Pacific (PST/PDT)",
        "Asia/Dubai" to "Gulf Standard (GST)",
        "UTC" to "Coordinated Universal Time"
    )

    fun calculateDualTime(
        instantEpochMs: Long,
        targetUserZoneId: String = TimeZone.getDefault().id
    ): DualTimeRepresentation {
        val berlinTz = TimeZone.getTimeZone(JLU_CAMPUS_ZONE)
        val userTz = TimeZone.getTimeZone(targetUserZoneId)

        val berlinFormatter = SimpleDateFormat("EEE, dd. MMM yyyy, HH:mm", Locale.GERMANY).apply {
            timeZone = berlinTz
        }
        val userFormatter = SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.ENGLISH).apply {
            timeZone = userTz
        }

        val date = Date(instantEpochMs)
        val berlinFormatted = berlinFormatter.format(date)
        val userFormatted = userFormatter.format(date)

        val diffMinutes = (userTz.getOffset(instantEpochMs) - berlinTz.getOffset(instantEpochMs)) / (60 * 1000)
        val diffHours = diffMinutes / 60
        val remainingMinutes = kotlin.math.abs(diffMinutes % 60)

        val diffText = when {
            diffMinutes == 0 -> "Same time as JLU Campus"
            diffMinutes > 0 -> "+${diffHours}h ${if (remainingMinutes > 0) "$remainingMinutes min " else ""}ahead of Gießen"
            else -> "${diffHours}h ${if (remainingMinutes > 0) "$remainingMinutes min " else ""}behind Gießen"
        }

        return DualTimeRepresentation(
            instantEpochMs = instantEpochMs,
            jluBerlinTimeFormatted = berlinFormatted,
            jluBerlinTimeZoneName = berlinTz.getDisplayName(berlinTz.inDaylightTime(date), TimeZone.SHORT, Locale.GERMANY),
            userLocalTimeFormatted = userFormatted,
            userLocalTimeZoneId = targetUserZoneId,
            timeDifferenceText = diffText
        )
    }

    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val minutes = diffMs / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            diffMs < 60 * 1000 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> {
                val fmt = SimpleDateFormat("dd. MMM", Locale.GERMANY)
                fmt.format(Date(timestamp))
            }
        }
    }
}
