package com.example.data.network

import android.util.Log
import com.example.data.model.EwsAuthMechanism
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

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
    private val client: OkHttpClient = createVerificationHttpClient()
) {
    companion object {
        private const val TAG = "AuthVerificationService"
        const val DEFAULT_JLU_EWS_ENDPOINT = EwsEndpointPolicy.DEFAULT_ENDPOINT
        const val DEFAULT_JLU_OWA_HOST = "owa.uni-giessen.de"

        fun createVerificationHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .addNetworkInterceptor(WwwAuthenticateLoggingInterceptor())
                .build()
    }

    class WwwAuthenticateLoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val authHeaders = response.headers("WWW-Authenticate")
            if (authHeaders.isEmpty()) {
                Log.w(TAG, "No WWW-Authenticate header found (HTTP ${response.code})")
            } else {
                authHeaders.forEach { Log.i(TAG, "WWW-Authenticate: $it") }
            }
            return response
        }
    }

    suspend fun verifyEndpointAuth(
        endpointUrl: String = DEFAULT_JLU_EWS_ENDPOINT
    ): AuthVerificationResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val logs = mutableListOf<String>()

        fun log(message: String, warning: Boolean = false) {
            logs += message
            if (warning) Log.w(TAG, message) else Log.i(TAG, message)
        }

        if (endpointUrl.isBlank()) {
            return@withContext AuthVerificationResult(
                isSuccess = false,
                endpointUrl = endpointUrl,
                httpStatusCode = 0,
                rawWwwAuthenticateHeaders = emptyList(),
                supportedMechanisms = emptyList(),
                selectedMechanism = EwsAuthMechanism.UNKNOWN,
                latencyMs = 0,
                diagnosticLogs = listOf("EWS endpoint URL is empty."),
                errorMessage = "EWS endpoint URL is required."
            )
        }

        log("Probing EWS endpoint: $endpointUrl")
        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .head()
                .header("User-Agent", "JLU-Mobile-Android/1.0 (EWS Auth Verification)")
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startedAt
                val statusCode = response.code
                val rawHeaders = response.headers("WWW-Authenticate")
                val mechanisms = rawHeaders
                    .map(EwsAuthMechanism::fromHeaderValue)
                    .distinct()

                log("Received HTTP $statusCode in ${latency}ms")
                rawHeaders.forEach { log("WWW-Authenticate: $it") }

                if (statusCode != 401) {
                    return@withContext AuthVerificationResult(
                        isSuccess = false,
                        endpointUrl = endpointUrl,
                        httpStatusCode = statusCode,
                        rawWwwAuthenticateHeaders = rawHeaders,
                        supportedMechanisms = mechanisms,
                        selectedMechanism = mechanisms.firstOrNull() ?: EwsAuthMechanism.UNKNOWN,
                        latencyMs = latency,
                        diagnosticLogs = logs,
                        errorMessage = "Expected HTTP 401 authentication challenge, received HTTP $statusCode."
                    )
                }

                if (mechanisms.isEmpty() || mechanisms.any { it == EwsAuthMechanism.UNKNOWN }) {
                    return@withContext AuthVerificationResult(
                        isSuccess = false,
                        endpointUrl = endpointUrl,
                        httpStatusCode = statusCode,
                        rawWwwAuthenticateHeaders = rawHeaders,
                        supportedMechanisms = mechanisms,
                        selectedMechanism = mechanisms.firstOrNull() ?: EwsAuthMechanism.UNKNOWN,
                        latencyMs = latency,
                        diagnosticLogs = logs,
                        errorMessage = "The endpoint returned HTTP 401 with no fully recognized authentication scheme."
                    )
                }

                if (EwsAuthMechanism.BASIC !in mechanisms) {
                    return@withContext AuthVerificationResult(
                        isSuccess = false,
                        endpointUrl = endpointUrl,
                        httpStatusCode = statusCode,
                        rawWwwAuthenticateHeaders = rawHeaders,
                        supportedMechanisms = mechanisms,
                        selectedMechanism = mechanisms.first(),
                        latencyMs = latency,
                        diagnosticLogs = logs,
                        errorMessage = "The endpoint does not advertise Basic authentication, which this client requires for EWS requests."
                    )
                }

                val selected = EwsAuthMechanism.BASIC


                log("Selected mechanism: ${selected.displayName}")

                AuthVerificationResult(
                    isSuccess = true,
                    endpointUrl = endpointUrl,
                    httpStatusCode = statusCode,
                    rawWwwAuthenticateHeaders = rawHeaders,
                    supportedMechanisms = mechanisms,
                    selectedMechanism = selected,
                    latencyMs = latency,
                    diagnosticLogs = logs
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startedAt
            val error = e.localizedMessage ?: e.javaClass.simpleName
            log("Network exception: $error", true)
            AuthVerificationResult(
                isSuccess = false,
                endpointUrl = endpointUrl,
                httpStatusCode = 0,
                rawWwwAuthenticateHeaders = emptyList(),
                supportedMechanisms = emptyList(),
                selectedMechanism = EwsAuthMechanism.UNKNOWN,
                latencyMs = latency,
                diagnosticLogs = logs,
                errorMessage = error
            )
        }
    }
}
