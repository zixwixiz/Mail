package com.example.data.network

import com.example.data.model.EwsAuthMechanism
import com.example.data.model.GateStatus
import com.example.data.model.GateStepResult
import com.example.data.model.ProtocolVerificationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory

class EwsProtocolVerifier(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {

    fun executeVerificationFlow(
        host: String = "owa.uni-giessen.de",
        ewsUrl: String = EwsEndpointPolicy.DEFAULT_ENDPOINT,
        username: String? = null,
        password: String? = null,
        mailboxEmail: String? = null
    ): Flow<ProtocolVerificationSummary> = flow {
        var summary = ProtocolVerificationSummary(
            targetHost = host,
            ewsEndpoint = ewsUrl,
            isVerifying = true,
            gate1 = GateStepResult(1, "Gate 1 — Network & TLS 443", "Testing DNS & TLS connection on port 443...", GateStatus.RUNNING)
        )
        emit(summary)

        // ==========================================
        // GATE 1: Network, DNS & TLS Handshake
        // ==========================================
        val gate1Result = testNetworkAndTls(host)
        val gate1Passed = gate1Result.status == GateStatus.PASSED

        if (!gate1Passed) {
            summary = summary.copy(
                isVerifying = false,
                gate1 = gate1Result,
                gate2 = summary.gate2.copy(status = GateStatus.SKIPPED, details = "Skipped due to Gate 1 network failure"),
                gate3 = summary.gate3.copy(status = GateStatus.SKIPPED, details = "Skipped due to Gate 1 network failure"),
                failureReason = gate1Result.details
            )
            emit(summary)
            return@flow
        }

        summary = summary.copy(
            gate1 = gate1Result,
            gate2 = summary.gate2.copy(status = GateStatus.RUNNING, details = "Sending unauthenticated probe to examine WWW-Authenticate...")
        )
        emit(summary)

        // ==========================================
        // GATE 2: WWW-Authenticate Header Inspection
        // ==========================================
        val gate2Result = inspectAuthChallenges(ewsUrl)
        val gate2Passed = gate2Result.stepResult.status == GateStatus.PASSED

        if (!gate2Passed) {
            summary = summary.copy(
                isVerifying = false,
                gate2 = gate2Result.stepResult,
                gate3 = summary.gate3.copy(status = GateStatus.SKIPPED, details = "Skipped: Authentication challenge inspection failed"),
                failureReason = gate2Result.stepResult.details
            )
            emit(summary)
            return@flow
        }

        val primaryMechanism = gate2Result.mechanisms.firstOrNull { it == EwsAuthMechanism.BASIC }
            ?: gate2Result.mechanisms.first()

        summary = summary.copy(
            gate2 = gate2Result.stepResult,
            detectedMechanisms = gate2Result.mechanisms,
            rawChallengeHeaders = gate2Result.rawHeaders,
            verifiedMechanism = primaryMechanism,
            httpStatusCode = gate2Result.httpStatus,
            gate3 = summary.gate3.copy(status = GateStatus.RUNNING, details = "Sending a harmless GetFolder SOAP request to the EWS endpoint.")
        )
        emit(summary)

        // ==========================================
        // GATE 3: EWS SOAP Endpoint Probe
        // ==========================================
        val gate3Result = testEwsSoapEnvelope(ewsUrl, username, password, mailboxEmail)

        summary = summary.copy(
            isVerifying = false,
            gate3 = gate3Result,
            lastVerifiedTimestamp = if (gate3Result.status == GateStatus.PASSED) System.currentTimeMillis() else null,
            failureReason = gate3Result.details.takeUnless { gate3Result.status == GateStatus.PASSED }
        )
        emit(summary)
    }.flowOn(Dispatchers.IO)

    private suspend fun testNetworkAndTls(host: String): GateStepResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            val addresses = java.net.InetAddress.getAllByName(host)
            require(addresses.isNotEmpty()) { "DNS returned no addresses for " + host }
            val ipList = addresses.joinToString(", ") { it.hostAddress.orEmpty() }

            val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket() as javax.net.ssl.SSLSocket
            socket.use {
                it.soTimeout = 6000
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    it.sslParameters = it.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                }
                it.connect(InetSocketAddress(host, 443), 6000)
                it.startHandshake()
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                    require(HttpsURLConnection.getDefaultHostnameVerifier().verify(host, it.session)) {
                        "TLS certificate hostname verification failed for $host"
                    }
                }
                GateStepResult(
                    gateNumber = 1,
                    title = "Gate 1 — Network & TLS 443",
                    description = "DNS, TCP, and TLS handshake",
                    status = GateStatus.PASSED,
                    details = "Resolved " + host + " to: " + ipList + "\nTCP 443 connected and TLS handshake completed.",
                    latencyMs = System.currentTimeMillis() - startedAt,
                    rawData = "IP: " + ipList + "\nPort: 443/TCP\nTLS: " + it.session.protocol +
                            "\nCipher: " + it.session.cipherSuite
                )
            }
        } catch (e: Exception) {
            GateStepResult(
                gateNumber = 1,
                title = "Gate 1 — Network & TLS 443",
                description = "DNS, TCP, and TLS handshake",
                status = GateStatus.FAILED,
                details = "Network/TLS verification failed: " +
                        (e.localizedMessage ?: e.javaClass.simpleName),
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }
    }

    data class Gate2InspectionResult(
        val stepResult: GateStepResult,
        val mechanisms: List<EwsAuthMechanism>,
        val rawHeaders: List<String>,
        val httpStatus: Int
    )

    private suspend fun inspectAuthChallenges(ewsUrl: String): Gate2InspectionResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(ewsUrl)
                .head()
                .header("User-Agent", "JLU-Mobile-Android/1.0 (EWS Auth Verification)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val headers = response.headers("WWW-Authenticate")
                val safeHeaders = headers
                    .map { it.trim().substringBefore(" ").trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                val mechanisms = safeHeaders
                    .map(EwsAuthMechanism::fromHeaderValue)
                    .distinct()
                    .filter { it != EwsAuthMechanism.UNKNOWN }

                if (response.code == 401 && mechanisms.isNotEmpty()) {
                    return@withContext Gate2InspectionResult(
                        stepResult = GateStepResult(
                            gateNumber = 2,
                            title = "Gate 2 — Auth Challenge (WWW-Authenticate)",
                            description = "Authentication challenge inspection",
                            status = GateStatus.PASSED,
                            details = "HTTP 401 challenge received. Recognized mechanisms: " +
                                    mechanisms.joinToString { it.displayName },
                            latencyMs = System.currentTimeMillis() - startedAt,
                            rawData = safeHeaders.joinToString("\n")
                        ),
                        mechanisms = mechanisms,
                        rawHeaders = safeHeaders,
                        httpStatus = response.code
                    )
                }

                Gate2InspectionResult(
                    stepResult = GateStepResult(
                        gateNumber = 2,
                        title = "Gate 2 — Auth Challenge (WWW-Authenticate)",
                        description = "Authentication challenge inspection",
                        status = GateStatus.FAILED,
                        details = "Expected HTTP 401 with recognized WWW-Authenticate headers; received HTTP " +
                                response.code + ".",
                        latencyMs = System.currentTimeMillis() - startedAt,
                        rawData = headers.joinToString("\n")
                    ),
                    mechanisms = mechanisms,
                    rawHeaders = headers,
                    httpStatus = response.code
                )
            }
        } catch (e: Exception) {
            Gate2InspectionResult(
                stepResult = GateStepResult(
                    gateNumber = 2,
                    title = "Gate 2 — Auth Challenge (WWW-Authenticate)",
                    description = "Authentication challenge inspection",
                    status = GateStatus.FAILED,
                    details = "Authentication challenge request failed: " +
                            (e.localizedMessage ?: e.javaClass.simpleName),
                    latencyMs = System.currentTimeMillis() - startedAt
                ),
                mechanisms = emptyList(),
                rawHeaders = emptyList(),
                httpStatus = 0
            )
        }
    }

    private suspend fun testEwsSoapEnvelope(
        ewsUrl: String,
        username: String?,
        password: String?,
        mailboxEmail: String?
    ): GateStepResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val hasCredentials = !username.isNullOrBlank() && !password.isNullOrBlank() && !mailboxEmail.isNullOrBlank()
        if (!hasCredentials) {
            return@withContext GateStepResult(
                gateNumber = 3,
                title = "Gate 3 — EWS SOAP Endpoint Probe",
                description = "Authenticated GetFolder SOAP request",
                status = GateStatus.SKIPPED,
                details = "Skipped because no active mailbox credentials are available.",
                latencyMs = 0
            )
        }

        val soapPayload = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
              <soap:Header>
                <t:RequestServerVersion Version="Exchange2016" />
              </soap:Header>
              <soap:Body>
                <m:GetFolder>
                  <m:FolderShape><t:BaseShape>IdOnly</t:BaseShape></m:FolderShape>
                  <m:FolderIds>
                    <t:DistinguishedFolderId Id="inbox">
                      <t:Mailbox>
                        <t:EmailAddress>${mailboxEmail.trim()}</t:EmailAddress>
                      </t:Mailbox>
                    </t:DistinguishedFolderId>
                  </m:FolderIds>
                </m:GetFolder>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()

        try {
            val request = Request.Builder()
                .url(ewsUrl)
                .post(soapPayload.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://schemas.microsoft.com/exchange/services/2006/messages/GetFolder")
                .header("User-Agent", "JLU-Mobile-Android/1.0 (EWS SOAP Verification)")
                .header("Authorization", okhttp3.Credentials.basic(username!!, password!!))
                .header("X-AnchorMailbox", mailboxEmail!!)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val responseCode = parseResponseCode(body)
                val passed = response.code in 200..299 && responseCode == "NoError"
                val detail = when {
                    passed -> "Authenticated EWS SOAP GetFolder completed successfully (HTTP ${response.code})."
                    response.code == 401 -> "Authenticated EWS SOAP request was rejected with HTTP 401."
                    responseCode.isNotBlank() -> "EWS SOAP returned ${responseCode} (HTTP ${response.code})."
                    else -> "EWS SOAP endpoint returned HTTP ${response.code}."
                }

                GateStepResult(
                    gateNumber = 3,
                    title = "Gate 3 — EWS SOAP Endpoint Probe",
                    description = "Authenticated GetFolder SOAP request",
                    status = if (passed) GateStatus.PASSED else GateStatus.FAILED,
                    details = detail,
                    latencyMs = System.currentTimeMillis() - startedAt,
                    rawData = "HTTP " + response.code + " SOAP response received; message content omitted from diagnostics."
                )
            }
        } catch (e: Exception) {
            GateStepResult(
                gateNumber = 3,
                title = "Gate 3 — EWS SOAP Endpoint Probe",
                description = "Authenticated GetFolder SOAP request",
                status = GateStatus.FAILED,
                details = "EWS SOAP request failed: " + (e.localizedMessage ?: e.javaClass.simpleName),
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }
    }

    private fun parseResponseCode(xmlContent: String): String {
        if (xmlContent.isBlank()) return ""
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var currentTag = ""
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> if (currentTag.equals("ResponseCode", ignoreCase = true)) return parser.text.trim()
                }
                eventType = parser.next()
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
