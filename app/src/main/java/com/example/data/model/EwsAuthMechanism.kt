package com.example.data.model

enum class EwsAuthMechanism(val displayName: String, val description: String) {
    NTLM(
        "NTLM",
        "NT LAN Manager challenge-response authentication. Standard for internal Exchange on-premises."
    ),
    NEGOTIATE(
        "Negotiate (SPNEGO)",
        "Simple and Protected GSSAPI Negotiation. Automatically selects Kerberos or NTLM."
    ),
    BASIC(
        "Basic (Over TLS)",
        "Standard RFC 7617 HTTP Basic authentication. Base64 credentials sent over secure TLS connection."
    ),
    BEARER_OAUTH(
        "Bearer / OAuth 2.0",
        "Modern token-based authorization via university identity provider or AD FS."
    ),
    KERBEROS(
        "Kerberos",
        "Ticket-based network authentication protocol used in Active Directory domains."
    ),
    UNKNOWN(
        "Unrecognized / Custom",
        "The server returned an authentication scheme not in the standard EWS candidate list."
    );

    companion object {
        fun fromHeaderValue(value: String): EwsAuthMechanism {
            val upper = value.uppercase().trim()
            return when {
                upper.startsWith("NEGOTIATE") -> NEGOTIATE
                upper.startsWith("NTLM") -> NTLM
                upper.startsWith("BASIC") -> BASIC
                upper.startsWith("BEARER") -> BEARER_OAUTH
                upper.startsWith("KERBEROS") -> KERBEROS
                else -> UNKNOWN
            }
        }
    }
}

enum class GateStatus {
    IDLE,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

data class GateStepResult(
    val gateNumber: Int,
    val title: String,
    val description: String,
    val status: GateStatus = GateStatus.IDLE,
    val details: String = "",
    val latencyMs: Long = 0,
    val rawData: String? = null
)

data class ProtocolVerificationSummary(
    val targetHost: String = "exchange.uni-giessen.de",
    val ewsEndpoint: String = "https://owa.uni-giessen.de/EWS/Exchange.asmx",
    val exchangeVersion: String = "Not verified",
    val isVerifying: Boolean = false,
    val gate1: GateStepResult = GateStepResult(
        gateNumber = 1,
        title = "Gate 1 — Network & TLS 443",
        description = "DNS resolution, TCP port 443 connection, and TLS handshake validation."
    ),
    val gate2: GateStepResult = GateStepResult(
        gateNumber = 2,
        title = "Gate 2 — Auth Challenge (WWW-Authenticate)",
        description = "Send unauthenticated probe to EWS endpoint and inspect HTTP 401 WWW-Authenticate headers."
    ),
    val gate3: GateStepResult = GateStepResult(
        gateNumber = 3,
        title = "Gate 3 — EWS SOAP Envelope Operation",
        description = "Execute a harmless GetFolder (Inbox) / FindItem SOAP envelope probe."
    ),
    val detectedMechanisms: List<EwsAuthMechanism> = emptyList(),
    val rawChallengeHeaders: List<String> = emptyList(),
    val verifiedMechanism: EwsAuthMechanism? = null,
    val httpStatusCode: Int? = null,
    val lastVerifiedTimestamp: Long? = null,
    val failureReason: String? = null
) {
    val allGatesPassed: Boolean
        get() = gate1.status == GateStatus.PASSED &&
                gate2.status == GateStatus.PASSED &&
                gate3.status == GateStatus.PASSED
}
