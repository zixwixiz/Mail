package com.example

import com.example.data.model.EwsAuthMechanism
import com.example.data.network.AuthVerificationService
import com.example.data.network.EwsClient
import com.example.data.network.EwsEndpointPolicy
import com.example.domain.SmartActionSuggestion
import com.example.domain.SmartExtractor
import kotlinx.coroutines.runBlocking
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
    fun authVerificationRejectsBlankEndpoint() = runBlocking {
        val result = AuthVerificationService().verifyEndpointAuth("", username = "", password = "", mailboxEmail = "")
        assertFalse(result.isSuccess)
        assertEquals(0, result.httpStatusCode)
        assertEquals("EWS endpoint URL is required.", result.errorMessage)
    }

    @Test
    fun authVerificationRejectsMalformedEndpointBeforeNetwork() = runBlocking {
        val result = AuthVerificationService().verifyEndpointAuth("not-a-url", username = "ad\\u12345", password = "password", mailboxEmail = "u12345@uni-giessen.de")
        assertFalse(result.isSuccess)
        assertEquals(0, result.httpStatusCode)
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
    fun endpointPolicyNormalizesAndValidatesJluAccounts() {
        assertEquals("ad\\u12345", EwsEndpointPolicy.normalizeUsername("u12345"))
        assertEquals("ad\\u12345", EwsEndpointPolicy.normalizeUsername("ad\\u12345"))
        assertTrue(EwsEndpointPolicy.validate(EwsEndpointPolicy.DEFAULT_ENDPOINT, "u12345@uni-giessen.de") == null)
        assertTrue(EwsEndpointPolicy.validate("http://example.com/EWS/Exchange.asmx", "u12345@uni-giessen.de")?.contains("HTTPS") == true)
        assertTrue(EwsEndpointPolicy.validate(EwsEndpointPolicy.DEFAULT_ENDPOINT, "user@example.com")?.contains("uni-giessen.de") == true)
    }

    @Test
    fun smartExtractorDoesNotInventDates() {
        val suggestions = SmartExtractor.extractSuggestions("Information zur Bibliothek", "Zur Kenntnisnahme: Öffnungszeiten wurden aktualisiert.", "mail-1")
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

    private fun fakeHttpClient(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
}
