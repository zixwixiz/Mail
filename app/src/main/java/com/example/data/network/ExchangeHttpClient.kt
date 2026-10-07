package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.http.HttpHeaders
import org.apache.http.auth.AuthScope
import org.apache.http.auth.NTCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.NTLMSchemeFactory
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.config.RegistryBuilder
import org.apache.http.auth.AuthSchemeProvider

data class ExchangeHttpResponse(
    val statusCode: Int,
    val body: String
)

class ExchangeHttpClient {

    suspend fun postSoap(
        endpointUrl: String,
        username: String,
        password: String,
        soapAction: String,
        soapXml: String,
        anchorMailbox: String
    ): ExchangeHttpResponse = withContext(Dispatchers.IO) {
        require(endpointUrl.startsWith("https://", ignoreCase = true)) { "Exchange endpoint must use HTTPS." }
        require(username.isNotBlank() && password.isNotBlank()) { "Username and password are required." }

        val (domain, user) = splitUsername(username)
        val credentialsProvider: CredentialsProvider = BasicCredentialsProvider().apply {
            setCredentials(
                AuthScope.ANY,
                NTCredentials(user, password, null, domain)
            )
        }

        val authRegistry = RegistryBuilder.create<AuthSchemeProvider>()
            .register("ntlm", NTLMSchemeFactory())
            .build()

        val requestConfig = RequestConfig.custom()
            .setConnectTimeout(12_000)
            .setConnectionRequestTimeout(12_000)
            .setSocketTimeout(20_000)
            .setTargetPreferredAuthSchemes(listOf("NTLM"))
            .build()

        val httpClient: CloseableHttpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .setDefaultAuthSchemeRegistry(authRegistry)
            .setDefaultRequestConfig(requestConfig)
            .disableRedirectHandling()
            .build()

        httpClient.use { client ->
            val request = HttpPost(endpointUrl).apply {
                config = requestConfig
                setHeader(HttpHeaders.CONTENT_TYPE, "text/xml; charset=utf-8")
                setHeader("SOAPAction", soapAction)
                setHeader(HttpHeaders.USER_AGENT, "JLU-Mail-Android/1.0 (Exchange EWS NTLM)")
                setHeader("X-AnchorMailbox", anchorMailbox)
                entity = StringEntity(soapXml, ContentType.create("text/xml", Charsets.UTF_8))
            }

            client.execute(request).use { response ->
                ExchangeHttpResponse(
                    statusCode = response.statusLine.statusCode,
                    body = response.entity?.content?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                )
            }
        }
    }

    private fun splitUsername(username: String): Pair<String, String> {
        val trimmed = username.trim()
        val separator = trimmed.indexOf('\\')
        return if (separator > 0 && separator < trimmed.lastIndex) {
            trimmed.substring(0, separator) to trimmed.substring(separator + 1)
        } else {
            "ad" to trimmed
        }
    }
}
