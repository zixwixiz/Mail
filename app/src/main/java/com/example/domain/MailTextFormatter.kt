package com.example.domain

import androidx.core.text.HtmlCompat

/**
 * Converts MIME/HTML fragments to readable text for display and exports.
 * This formatter is not an HTML sanitizer and must not be used to sanitize WebView markup.
 */
object MailTextFormatter {
    private val breakTags = Regex("""(?i)<br\s*/?>""")
    private val listItemStart = Regex("""(?i)<li\b[^>]*>""")
    private val listItemEnd = Regex("""(?i)</li\s*>""")
    private val paragraphEnd = Regex("""(?i)</(p|div|section|article|blockquote|h[1-6])\s*>""")
    private val blockStart = Regex("""(?i)<(p|div|section|article|blockquote|h[1-6]|table|tr|ul|ol)\b[^>]*>""")
    private val blockEnd = Regex("""(?i)</(table|tr|ul|ol)\s*>""")
    private val cellStart = Regex("""(?i)<(td|th)\b[^>]*>""")
    private val cellEnd = Regex("""(?i)</(td|th)\s*>""")
    private const val TOKEN_PREFIX = "__JLU_MAIL_LAYOUT_"
    private const val TOKEN_SUFFIX = "__"

    /** One-line text for subjects, sender labels, and previews. */
    fun plainText(value: String): String =
        normalize(decodeHtml(value), preserveParagraphs = false)

    /**
     * Preserve original newlines and common HTML block/list boundaries while decoding entities.
     * Tokens protect breaks from HtmlCompat, which can otherwise collapse inserted newline chars.
     */
    fun readableBody(value: String): String {
        if (value.isBlank()) return ""
        val tokens = linkedMapOf<String, String>()
        fun token(replacement: String): String {
            val marker = TOKEN_PREFIX + tokens.size + TOKEN_SUFFIX
            tokens[marker] = replacement
            return marker
        }

        val prepared = prepareHtml(value)
            .replace(listItemStart) { token("• ") }
            .replace(listItemEnd) { token("\n") }
            .replace(breakTags) { token("\n") }
            .replace(paragraphEnd) { token("\n\n") }
            .replace(blockStart, "")
            .replace(blockEnd) { token("\n") }
            .replace(cellStart) { token(" ") }
            .replace(cellEnd) { token("\t") }
            .replace(Regex("\r\n|\r|\n+")) { token(it.value) }

        var decoded = HtmlCompat.fromHtml(prepared, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        tokens.forEach { (marker, replacement) -> decoded = decoded.replace(marker, replacement) }
        return normalize(decoded, preserveParagraphs = true)
    }

    private fun decodeHtml(value: String): String =
        HtmlCompat.fromHtml(prepareHtml(value), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    private fun prepareHtml(value: String): String = value
        .replace(Regex("""(?i)<style\b[^>]*>.*?</style\s*>"""), " ")
        .replace(Regex("""(?i)<script\b[^>]*>.*?</script\s*>"""), " ")
        .replace(Regex("""(?i)<head\b[^>]*>.*?</head\s*>"""), " ")
        .replace(Regex("""(?i)<img\b[^>]*alt=["']([^"']*)["'][^>]*>"""), " [$1] ")

    private fun normalize(value: String, preserveParagraphs: Boolean): String {
        var text = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00a0', ' ')
            .replace(Regex("[\\t ]+\\n"), "\n")
            .replace(Regex("\\n[\\t ]+"), "\n")
            .replace(Regex("[\\t ]{2,}"), " ")
        text = if (preserveParagraphs) {
            text.replace(Regex(" *\\n *"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
        } else {
            text.replace(Regex("\\s+"), " ")
        }
        return text.trim()
    }
}
