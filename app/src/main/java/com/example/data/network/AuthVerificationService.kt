package com.example.data.network

import com.example.data.model.EwsAuthMechanism
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class AuthVerificationResult(
    val isSuccess: Boolean,
    val endpointUrl: String,
    val httpStatusCode: Int,
    val rawWwwAuthenticateHeaders: List<String>,
    val supportedMechanisms: List<EwsAuthMechanism>,
    val selectedMechanism: EwsAuthMechanism,
    val latencyMs: Long,
    val diagnosticLogs: List<String>,
    val errorMessage: String? = null
)

class AuthVerificationService(
    private val exchangeHttpClient: ExchangeHttpClient = ExchangeHttpClient()
) {
    companion object {
        const val DEFAULT_JLU_EWS_ENDPOINT = EwsEndpointPolicy.DEFAULT_ENDPOINT
        const val DEFAULT_JLU_OWA_HOST = EwsEndpointPolicy.DEFAULT_WEB_HOST
    }

    suspend fun verifyEndpointAuth(
        endpointUrl: String = DEFAULT_JLU_EWS_ENDPOINT,
        username: String,
        password: String,
        mailboxEmail: String
    ): AuthVerificationResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val endpointError = EwsEndpointPolicy.validate(endpointUrl, mailboxEmail)
        if (endpointError != null) return@withContext failure(endpointUrl, startedAt, endpointError)
        if (username.isBlank() || password.isBlank() || mailboxEmail.isBlank()) {
            return@withContext failure(endpointUrl, startedAt, "Kennung, password, and mailbox address are required.")
        }

        val soap = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
              <soap:Header><t:RequestServerVersion Version="Exchange2016" /></soap:Header>
              <soap:Body>
                <m:GetFolder>
                  <m:FolderShape><t:BaseShape>IdOnly</t:BaseShape></m:FolderShape>
                  <m:FolderIds>
                    <t:DistinguishedFolderId Id="inbox">
                      <t:Mailbox><t:EmailAddress>${escapeXml(mailboxEmail.trim())}</t:EmailAddress></t:Mailbox>
                    </t:DistinguishedFolderId>
                  </m:FolderIds>
                </m:GetFolder>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        try {
            val response = exchangeHttpClient.postSoap(
                endpointUrl = endpointUrl.trim(),
                username = username.trim(),
                password = password,
                soapAction = "http://schemas.microsoft.com/exchange/services/2006/messages/GetFolder",
                soapXml = soap,
                anchorMailbox = mailboxEmail.trim()
            )
            val latency = System.currentTimeMillis() - startedAt
            val responseCode = parseResponseCode(response.body)
            val success = response.statusCode in 200..299 && responseCode == "NoError"
            val message = when {
                success -> "Exchange EWS authentication and Inbox access verified."
                response.statusCode == 401 -> "Exchange rejected the credentials (HTTP 401)."
                responseCode.isNotBlank() -> "Exchange returned EWS ${responseCode} (HTTP ${response.statusCode})."
                else -> "Exchange returned HTTP ${response.statusCode}."
            }

            AuthVerificationResult(
                isSuccess = success,
                endpointUrl = endpointUrl,
                httpStatusCode = response.statusCode,
                rawWwwAuthenticateHeaders = emptyList(),
                supportedMechanisms = listOf(EwsAuthMechanism.NTLM),
                selectedMechanism = EwsAuthMechanism.NTLM,
                latencyMs = latency,
                diagnosticLogs = listOf(
                    "JLU Exchange endpoint verified over HTTPS.",
                    "Authentication: NTLM challenge-response (domain: ad).",
                    "EWS GetFolder(Inbox): " + if (success) "NoError" else responseCode.ifBlank { "HTTP_${response.statusCode}" }
                ),
                errorMessage = if (success) null else message
            )
        } catch (e: Exception) {
            failure(endpointUrl, startedAt, "Exchange login failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun failure(endpointUrl: String, startedAt: Long, message: String) =
        AuthVerificationResult(
            isSuccess = false,
            endpointUrl = endpointUrl,
            httpStatusCode = 0,
            rawWwwAuthenticateHeaders = emptyList(),
            supportedMechanisms = listOf(EwsAuthMechanism.NTLM),
            selectedMechanism = EwsAuthMechanism.NTLM,
            latencyMs = System.currentTimeMillis() - startedAt,
            diagnosticLogs = listOf(message),
            errorMessage = message
        )

    private fun parseResponseCode(xml: String): String {
        if (xml.isBlank()) return ""
        return try {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setInput(StringReader(xml))
            var tag = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) tag = parser.name
                if (event == XmlPullParser.TEXT && tag.equals("ResponseCode", true)) return parser.text.trim()
                event = parser.next()
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun escapeXml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
