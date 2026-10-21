package com.example.domain

import androidx.core.text.HtmlCompat

/**
 * Converts MIME/HTML fragments to readable plain text for inbox subjects and previews.
 *
 * Email headers and bodies are untrusted input. This formatter only produces display text;
 * it must never be used as a substitute for sanitizing HTML before rendering a WebView.
 */
object MailTextFormatter {
    fun plainText(value: String): String =
        HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("[\\t\\n\\r ]+"), " ")
            .trim()
}
