package com.example.ui

import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.model.ProtocolVerificationSummary

object DiagnosticsFormatter {
    fun sanitized(
        summary: ProtocolVerificationSummary,
        saved: EndpointVerificationEntity?
    ): String = buildString {
        appendLine("JLU Mobile — Sanitized Diagnostics")
        appendLine("Host: " + summary.targetHost)
        appendLine("EWS endpoint: " + summary.ewsEndpoint)
        appendLine("Gate 1: " + summary.gate1.status)
        appendLine("Gate 2: " + summary.gate2.status)
        appendLine("Gate 3: " + summary.gate3.status)
        appendLine(
            "Detected authentication mechanisms: " +
                summary.detectedMechanisms.joinToString { it.displayName }.ifBlank { "none" }
        )
        appendLine(
            "HTTP status: " +
                (summary.httpStatusCode?.toString()
                    ?: if (saved?.gate3Passed == true) "200" else "not available")
        )
        appendLine(
            "Last verified: " +
                (summary.lastVerifiedTimestamp?.toString()
                    ?: saved?.lastVerifiedTimestamp?.toString()
                    ?: "not available")
        )
        appendLine("Failure: " + (summary.failureReason ?: "none"))
        appendLine()
        appendLine("No username, password, email body, or raw SOAP payload is included.")
    }
}
