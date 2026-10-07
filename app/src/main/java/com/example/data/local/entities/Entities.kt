package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mail_messages")
data class MailMessageEntity(
    @PrimaryKey val id: String,
    val mailboxId: String,
    val folder: String, // INBOX, SENT, DRAFTS, TRASH
    val threadId: String,
    val subject: String,
    val senderName: String,
    val senderEmail: String,
    val recipients: String,
    val receivedTimestamp: Long,
    val bodyText: String,
    val bodyHtml: String,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val hasAttachments: Boolean,
    val category: String = "Normal"
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val mailboxId: String,
    val title: String,
    val description: String,
    val startInstant: Long, // Epoch ms UTC
    val endInstant: Long,   // Epoch ms UTC
    val timeZone: String = "Europe/Berlin",
    val location: String,
    val attendees: String,
    val isAllDay: Boolean = false,
    val rsvpStatus: String = "ACCEPTED", // ACCEPTED, TENTATIVE, DECLINED, NONE
    val reminderMinutes: Int = 15
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val mailboxId: String,
    val title: String,
    val description: String,
    val dueInstant: Long?, // Epoch ms UTC
    val timeZone: String = "Europe/Berlin",
    val priority: String = "NORMAL", // HIGH, NORMAL, LOW
    val isCompleted: Boolean = false,
    val reminderInstant: Long? = null,
    val source: String = "MANUAL", // MANUAL, EMAIL_EXTRACTED
    val sourceEmailId: String? = null,
    val completedTimestamp: Long? = null
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val mailboxId: String,
    val displayName: String,
    val email: String,
    val phone: String,
    val organization: String = "",
    val department: String,
    val officeRoom: String = "",
    val isJluDirectory: Boolean = false
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val mailboxId: String,
    val title: String,
    val content: String,
    val updatedTimestamp: Long,
    val linkedEmailId: String? = null,
    val linkedEventId: String? = null,
    val linkedTaskId: String? = null
)

@Entity(tableName = "endpoint_verification")
data class EndpointVerificationEntity(
    @PrimaryKey val mailboxId: String,
    val host: String,
    val ewsUrl: String,
    val gate1Passed: Boolean,
    val gate2Passed: Boolean,
    val gate3Passed: Boolean,
    val verifiedMechanism: String,
    val challengeHeaders: String,
    val lastVerifiedTimestamp: Long,
    val latencyMs: Long
)
