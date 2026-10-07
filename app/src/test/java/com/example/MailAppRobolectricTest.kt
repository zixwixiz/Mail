package com.example

import com.example.data.model.EwsAuthMechanism
import com.example.data.network.AuthVerificationService
import com.example.data.network.EwsClient
import com.example.data.network.EwsEndpointPolicy
import com.example.domain.SmartActionSuggestion
import com.example.domain.SmartExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.MediaType.Companion.toMediaType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MailAppRobolectricTest {

    @Test
    fun authVerificationAcceptsReal401Challenge() = runBlocking {
        val client = fakeHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .addHeader("WWW-Authenticate", "Negotiate")
                .addHeader("WWW-Authenticate", "NTLM")
                .addHeader("WWW-Authenticate", "Basic realm=\"example\"")
                .build()
        }

        val result = AuthVerificationService(client)
            .verifyEndpointAuth("https://example.test/EWS/Exchange.asmx")

        assertTrue(result.isSuccess)
        assertTrue(EwsAuthMechanism.NEGOTIATE in result.supportedMechanisms)
        assertTrue(EwsAuthMechanism.NTLM in result.supportedMechanisms)
        assertTrue(EwsAuthMechanism.BASIC in result.supportedMechanisms)
        assertEquals(EwsAuthMechanism.BASIC, result.selectedMechanism)
        assertEquals(401, result.httpStatusCode)
    }

    @Test
    fun authVerificationRejectsNonChallengeResponse() = runBlocking {
        val client = fakeHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        val result = AuthVerificationService(client)
            .verifyEndpointAuth("https://example.test/EWS/Exchange.asmx")

        assertFalse(result.isSuccess)
        assertEquals(200, result.httpStatusCode)
        assertTrue(result.errorMessage?.contains("401") == true)
    }

    @Test
    fun authMechanismParsingIsDeterministic() {
        assertEquals(EwsAuthMechanism.NEGOTIATE, EwsAuthMechanism.fromHeaderValue("Negotiate"))
        assertEquals(EwsAuthMechanism.NTLM, EwsAuthMechanism.fromHeaderValue("NTLM"))
        assertEquals(EwsAuthMechanism.BASIC, EwsAuthMechanism.fromHeaderValue("Basic realm=\"example\""))
        assertEquals(EwsAuthMechanism.BEARER_OAUTH, EwsAuthMechanism.fromHeaderValue("Bearer"))
    }

    @Test
    fun ewsParserUsesServerMessageIdAndDoesNotFabricateFields() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <m:FindItemResponse xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                                    xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types">
                  <m:ResponseMessages>
                    <m:FindItemResponseMessage ResponseClass="Success">
                      <m:ResponseCode>NoError</m:ResponseCode>
                      <m:RootFolder>
                        <t:Items>
                          <t:Message>
                            <t:ItemId Id="server-item-001" ChangeKey="change-001"/>
                            <t:Subject>Real subject</t:Subject>
                            <t:DateTimeReceived>2026-01-02T10:00:00Z</t:DateTimeReceived>
                            <t:From>
                              <t:Mailbox>
                                <t:Name>Real Sender</t:Name>
                                <t:EmailAddress>sender@example.edu</t:EmailAddress>
                              </t:Mailbox>
                            </t:From>
                            <t:ToRecipients>
                              <t:Mailbox><t:EmailAddress>recipient@example.edu</t:EmailAddress></t:Mailbox>
                            </t:ToRecipients>
                            <t:Body BodyType="Text">Real body</t:Body>
                            <t:IsRead>true</t:IsRead>
                            <t:HasAttachments>false</t:HasAttachments>
                          </t:Message>
                        </t:Items>
                      </m:RootFolder>
                    </m:FindItemResponseMessage>
                  </m:ResponseMessages>
                </m:FindItemResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val message = EwsClient()
            .parseEwsItemsFromXml(xml, "mailbox-1", "INBOX")
            .single()

        assertEquals("server-item-001", message.id)
        assertEquals("Real subject", message.subject)
        assertEquals("Real Sender", message.senderName)
        assertEquals("sender@example.edu", message.senderEmail)
        assertEquals("recipient@example.edu", message.recipients)
        assertEquals("Real body", message.bodyText)
    }

    @Test
    fun ewsParserSkipsMessagesWithoutServerItemId() {
        val xml = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types">
              <s:Body>
                <t:Message>
                  <t:Subject>Incomplete</t:Subject>
                  <t:From><t:Mailbox><t:EmailAddress>sender@example.edu</t:EmailAddress></t:Mailbox></t:From>
                </t:Message>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        assertTrue(
            EwsClient().parseEwsItemsFromXml(xml, "mailbox-1", "INBOX").isEmpty()
        )
    }

    @Test
    fun ewsSendRejectsFalseSuccessAndOnlyUsesServerId() = runBlocking {
        var requestCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestCount++
                val request = chain.request()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("<ResponseClass=\"Success\">".toResponseBody("text/xml".toMediaType()))
                    .build()
            })
            .build()

        val failed = EwsClient(client).sendMessage(
            endpointUrl = "https://example.test/EWS/Exchange.asmx",
            username = "user",
            password = "password",
            senderEmail = "user@example.edu",
            recipients = listOf("recipient@example.edu"),
            subject = "Subject",
            bodyHtml = "<p>Body</p>"
        )

        assertFalse(failed.isSuccess)
        assertNull(failed.messageId)
        assertEquals(500, failed.httpStatusCode)
        assertEquals(1, requestCount)
    }

    @Test
    fun endpointPolicyNormalizesAndValidatesJluAccounts() {
        assertEquals("ad\\u12345", EwsEndpointPolicy.normalizeUsername("u12345"))
        assertEquals("ad\\u12345", EwsEndpointPolicy.normalizeUsername("ad\\u12345"))
        assertTrue(EwsEndpointPolicy.validate(EwsEndpointPolicy.DEFAULT_ENDPOINT, "u12345@uni-giessen.de") == null)
        assertTrue(EwsEndpointPolicy.validate("http://example.com/EWS/Exchange.asmx", "u12345@uni-giessen.de")?.contains("HTTPS") == true)
        assertTrue(EwsEndpointPolicy.validate(EwsEndpointPolicy.DEFAULT_ENDPOINT, "user@example.com")?.contains("uni-giessen.de") == true)
    }

    @Test
    fun smartExtractorDoesNotInventDates() {
        val suggestions = SmartExtractor.extractSuggestions("Termin zur Besprechung", "Bitte melden Sie sich.", "mail-1")
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun smartExtractorUsesExplicitMeetingDateAndTime() {
        val suggestions = SmartExtractor.extractSuggestions("Kolloquium", "Datum: 18. Oktober 2026, 10:00 Uhr\nOrt: Raum 204", "mail-2")
        val calendar = suggestions.filterIsInstance<SmartActionSuggestion.CalendarSuggestion>().single()
        assertEquals("Raum 204", calendar.location)
        assertTrue(calendar.startInstant > 0L)
        assertEquals(60 * 60 * 1000L, calendar.endInstant - calendar.startInstant)
    }

    @Test
    fun ewsSendAcceptsNoErrorWithServerItemId() = runBlocking {
        val client = fakeHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("<ResponseClass=\"Success\"><ResponseCode>NoError</ResponseCode><Items><ItemId Id=\"server-created-001\"/></Items>".toResponseBody("text/xml".toMediaType()))
                .build()
        }
        val result = EwsClient(client).sendMessage(
            endpointUrl = EwsEndpointPolicy.DEFAULT_ENDPOINT,
            username = "ad\\u12345",
            password = "password",
            senderEmail = "u12345@uni-giessen.de",
            recipients = listOf("recipient@example.edu"),
            subject = "Subject",
            bodyHtml = "<p>Body</p>"
        )
        assertTrue(result.isSuccess)
        assertEquals("server-created-001", result.messageId)
        assertEquals("NoError", result.responseCode)
    }


    private fun fakeHttpClient(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
}
