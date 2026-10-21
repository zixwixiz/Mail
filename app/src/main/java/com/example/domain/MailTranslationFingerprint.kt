package com.example.domain

import com.example.data.local.entities.MailMessageEntity
import java.security.MessageDigest

object MailTranslationFingerprint {
    fun forMessage(message: MailMessageEntity): String {
        val body = message.bodyText.ifBlank { MailTextFormatter.plainText(message.bodyHtml) }
        val bytes = "${message.subject}\u0000$body".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
