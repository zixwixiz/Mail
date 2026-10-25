package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.security.MessageDigest

object MailTranslationFingerprint {
    // Bump this when translation layout/output handling changes so stale flattened translations
    // are refreshed on the next translate request instead of being silently reused.
    private const val TRANSLATION_FORMAT_VERSION = "paragraph-layout-v2"

    fun forMessage(message: MailMessageEntity): String {
        val body = message.bodyText.ifBlank { MailTextFormatter.readableBody(message.bodyHtml) }
        val bytes = "${TRANSLATION_FORMAT_VERSION}\u0000${message.subject}\u0000$body".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
