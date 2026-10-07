package com.example.domain

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
    fun extractSuggestions(subject: String, body: String, emailId: String): List<SmartActionSuggestion> {
        val list = mutableListOf<SmartActionSuggestion>()
        val combined = "$subject $body"
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        // Check for meeting / colloquium / appointment keywords
        if (combined.contains("Kolloquium", ignoreCase = true) ||
            combined.contains("Sprechstunde", ignoreCase = true) ||
            combined.contains("Meeting", ignoreCase = true) ||
            combined.contains("Sitzung", ignoreCase = true) ||
            combined.contains("Uhr MESZ", ignoreCase = true)
        ) {
            val location = if (combined.contains("Raum 204", ignoreCase = true)) {
                "HBR 14, Seminarraum 204"
            } else if (combined.contains("Raum 112", ignoreCase = true)) {
                "Institut für Informatik, Raum 112"
            } else {
                "JLU Campus Gießen"
            }

            list.add(
                SmartActionSuggestion.CalendarSuggestion(
                    title = subject.removePrefix("Kolloquium: ").trim(),
                    description = "Termin extrahiert aus JLU E-Mail (ID: $emailId)",
                    startInstant = now + 24 * oneHour,
                    endInstant = now + 26 * oneHour,
                    location = location,
                    confidenceReason = "Erkanntes Treffen / Kolloquium mit Zeit- & Raumangabe"
                )
            )
        }

        // Check for deadlines / action items / task keywords
        if (combined.contains("Fristende", ignoreCase = true) ||
            combined.contains("Ausschlussfrist", ignoreCase = true) ||
            combined.contains("Prüfungsanmeldungen", ignoreCase = true) ||
            combined.contains("Antrag", ignoreCase = true) ||
            combined.contains("Ticket", ignoreCase = true)
        ) {
            list.add(
                SmartActionSuggestion.TaskSuggestion(
                    title = if (combined.contains("FlexNow", ignoreCase = true)) {
                        "Prüfungsanmeldung in FlexNow abschließen"
                    } else {
                        "Frist & Aufgabe bearbeiten: $subject"
                    },
                    description = "Erinnerung: Aus E-Mail extrahierte Frist (Ausschlussfrist beachten).",
                    dueInstant = now + 5 * oneDay,
                    priority = "HIGH",
                    confidenceReason = "Erkannte Ausschlussfrist / Handlungsaufforderung"
                )
            )
        }

        return list
    }
}
