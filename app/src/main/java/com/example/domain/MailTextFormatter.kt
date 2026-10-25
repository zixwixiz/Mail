package com.example.domain

import androidx.core.text.HtmlCompat

/**
 * Converts email/MIME HTML fragments to reader-friendly text.
 *
 * This formatter is for display and notes only; it is not a replacement for sanitizing HTML
 * before loading any content in a WebView.
 */
object MailTextFormatter {
    /** One-line text for subjects, sender labels, and previews. */
    fun plainText(value: String): String =
        HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("[\\\\t\\\\n\\\\r ]+"), " ")
            .replace(' ', ' ')
            .trim()

    /**
     * Readable body text that preserves paragraph breaks and list structure in source and
     * translated email. HTML is normalized before parsing so common email markup remains legible.
     */
    fun readableBody(value: String): String {
        val normalizedHtml = value
            .replace(Regex("(?i)<\\\\s*br\\\\s*/?\\\\s*>"), "\n")
            .replace(Regex("(?i)<\\\\s*li\\\\b[^>]*>"), "\n• ")
            .replace(Regex("(?i)</\\\\s*li\\\\s*>"), "\n")
            .replace(Regex("(?i)</\\\\s*(p|div|section|article|h[1-6]|tr|blockquote)\\\\s*>"), "\n\n")

        return HtmlCompat.fromHtml(normalizedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("\r\n", "\n")
            .replace(' ', ' ')
            .replace(Regex("(?m)^[\\\\t ]*[-*][\\\\t ]+"), "• ")
            .replace(Regex("[\\\\t ]+\\\\n"), "\n")
            .replace(Regex("\\\\n[\\\\t ]+"), "\n")
            .replace(Regex("[\\\\t ]{2,}"), " ")
            .replace(Regex("\\\\n{3,}"), "\n\n")
            .trim()
    }
}
