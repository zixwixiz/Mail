package com.example.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "mail_translations",
    primaryKeys = ["mailboxId", "messageId", "targetLanguage"]
)
data class MailTranslationEntity(
    val mailboxId: String,
    val messageId: String,
    val targetLanguage: String,
    val originalFingerprint: String,
    val translatedSubject: String,
    val translatedPreview: String,
    val translatedBody: String,
    val isPartial: Boolean,
    val updatedTimestamp: Long
)
