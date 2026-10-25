package com.example.domain

import androidx.core.text.HtmlCompat

/**
 * Converts email HTML to readable plain text. This is for display only, not an HTML sanitizer.
 */
object MailTextFormatter {
    /** One-line text for subjects, sender labels, and previews. */
    fun plainText(value: String): String =
        normalize(HtmlCompat.fromHtml(prepareHtml(value), HtmlCompat.FROM_HTML_MODE_LEGACY).toString(), false)

    /** Preserve paragraph, heading, line-break, and list boundaries from common email HTML. */
    fun readableBody(value: String): String {
        val prepared = prepareHtml(value)
            .replace(Regex("(?i)<li\\b[^>]*>"), "• ")
            .replace(Regex("(?i)</li\\s*>"), "\n")
            .replace(Regex("(?i)<(br|hr)\\b[^>]*>"), "\n")
            .replace(Regex("(?i)</(p|div|section|article|blockquote|h[1-6]|tr|table|ul|ol)\\s*>"), "\n")
            .replace(Regex("(?i)<(p|div|section|article|blockquote|h[1-6]|tr|table|ul|ol)\\b[^>]*>"), "\n")
        return normalize(HtmlCompat.fromHtml(prepared, HtmlCompat.FROM_HTML_MODE_LEGACY).toString(), true)
    }

    private fun prepareHtml(value: String): String = value
        .replace(Regex("(?i)<style\\b[^>]*>.*?</style\\s*>"), " ")
        .replace(Regex("(?i)<script\\b[^>]*>.*?</script\\s*>"), " ")
        .replace(Regex("(?i)<head\\b[^>]*>.*?</head\\s*>"), " ")
        .replace(Regex("(?i)<img\\b[^>]*alt=[\"']([^\"']*)[\"'][^>]*>"), " [$1] ")

    private fun normalize(value: String, preserveParagraphs: Boolean): String {
        var text = value.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\u00a0', ' ')
            .replace(Regex("[\\t ]+\n"), "\n")
            .replace(Regex("\n[\\t ]+"), "\n")
            .replace(Regex("[\\t ]{2,}"), " ")
        text = if (preserveParagraphs) {
            text.replace(Regex(" *\n *"), "\n").replace(Regex("\n{3,}"), "\n\n")
        } else {
            text.replace(Regex("\\s+"), " ")
        }
        return text.trim()
    }
}
