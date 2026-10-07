package com.example.data.network

import java.net.URI
import java.util.Locale

object EwsEndpointPolicy {
    const val DEFAULT_HOST = "exchange.uni-giessen.de"
    const val DEFAULT_WEB_HOST = "owa.uni-giessen.de"
    const val DEFAULT_PATH = "/EWS/Exchange.asmx"
    const val DEFAULT_ENDPOINT = "https://exchange.uni-giessen.de/EWS/Exchange.asmx"

    fun normalizeUsername(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) { "Account identifier is required." }
        return when {
            trimmed.contains("\\") -> trimmed
            trimmed.contains("@") -> throw IllegalArgumentException(
                "Use the JLU account identifier, not the email address."
            )
            else -> "ad\\$trimmed"
        }
    }

    fun validate(endpointUrl: String, emailAddress: String? = null): String? {
        val trimmedUrl = endpointUrl.trim()
        if (trimmedUrl.isBlank()) return "EWS endpoint URL is required."

        val uri = try {
            URI(trimmedUrl)
        } catch (_: Exception) {
            return "EWS endpoint URL is invalid."
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return "EWS endpoint must use HTTPS."
        }
        if (!uri.userInfo.isNullOrBlank()) {
            return "EWS endpoint must not contain embedded credentials."
        }
        if (uri.port != -1 && uri.port != 443) {
            return "EWS endpoint must use port 443."
        }

        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank() ||
            !(host == DEFAULT_HOST || host == DEFAULT_WEB_HOST)
        ) {
            return "EWS endpoint host must belong to uni-giessen.de."
        }

        val path = uri.path.trimEnd('/')
        if (!path.equals(DEFAULT_PATH, ignoreCase = true)) {
            return "EWS endpoint path must be /EWS/Exchange.asmx."
        }

        if (!emailAddress.isNullOrBlank()) {
            val emailHost = emailAddress.substringAfterLast('@', "").lowercase(Locale.ROOT)
            if (emailHost.isBlank() ||
                !(emailHost == "uni-giessen.de" || emailHost.endsWith(".uni-giessen.de"))
            ) {
                return "Mailbox address must belong to uni-giessen.de."
            }
        }

        return null
    }
}
