package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.util.Locale

enum class ReadingPriority {
    ACTION_REQUIRED,
    IMPORTANT,
    ROUTINE
}

data class ReadingQueueClassification(
    val priority: ReadingPriority,
    val reason: String
)

/**
 * Transparent, deterministic inbox triage. This is a rules-based helper, not an AI model;
 * it prioritizes explicit deadlines and requests without claiming to understand every message.
 */
object ReadingQueueClassifier {
    private val urgentTerms = listOf(
        "deadline", "due by", "due date", "action required", "payment due",
        "frist", "fristende", "ausschlussfrist", "dringend", "urgent",
        "respond by", "reply by", "expires on"
    )
    private val actionTerms = listOf(
        "please reply", "please respond", "please send", "please submit",
        "required", "submit", "confirm", "bitte", "erforderlich",
        "rückmeldung", "antrag", "can you", "could you", "we need you to"
    )

    fun classify(message: MailMessageEntity): ReadingQueueClassification {
        val text = "${message.subject}\n${message.bodyText}"
            .lowercase(Locale.ROOT)

        if (urgentTerms.any(text::contains)) {
            return ReadingQueueClassification(
                ReadingPriority.ACTION_REQUIRED,
                "Contains deadline, urgency, or time-sensitive wording."
            )
        }
        if (message.category.contains("urgent", ignoreCase = true) ||
            message.category.contains("high", ignoreCase = true)
        ) {
            return ReadingQueueClassification(
                ReadingPriority.IMPORTANT,
                "Marked as high priority by the mail source."
            )
        }
        if (actionTerms.any(text::contains)) {
            return ReadingQueueClassification(
                ReadingPriority.ACTION_REQUIRED,
                "Contains wording that may request a response or action."
            )
        }
        if (message.isFlagged) {
            return ReadingQueueClassification(
                ReadingPriority.IMPORTANT,
                "Flagged for follow-up."
            )
        }
        return ReadingQueueClassification(
            ReadingPriority.ROUTINE,
            "No clear deadline or action phrase was detected."
        )
    }
}
