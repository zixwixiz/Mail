package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.EwsAuthMechanism
import com.example.data.network.AuthVerificationService
import com.example.data.network.EwsClient
import com.example.domain.TimezoneEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("JLU Mobile", appName)
  }

  @Test
  fun `verify WWW-Authenticate mechanism detection`() {
    assertEquals(EwsAuthMechanism.NEGOTIATE, EwsAuthMechanism.fromHeaderValue("Negotiate"))
    assertEquals(EwsAuthMechanism.NTLM, EwsAuthMechanism.fromHeaderValue("NTLM"))
    assertEquals(EwsAuthMechanism.BASIC, EwsAuthMechanism.fromHeaderValue("Basic realm=\"owa.uni-giessen.de\""))
  }

  @Test
  fun `verify AuthVerificationService execution and WWW-Authenticate inspection`() = runBlocking {
    val service = AuthVerificationService()
    val result = service.verifyEndpointAuth(AuthVerificationService.DEFAULT_JLU_EWS_ENDPOINT, "s1234567")
    assertTrue(result.isSuccess)
    assertTrue(result.supportedMechanisms.isNotEmpty())
    assertTrue(result.diagnosticLogs.isNotEmpty())
  }

  @Test
  fun `verify EwsClient SOAP XML parser for FindItem response`() {
    val sampleEwsXml = """
      <?xml version="1.0" encoding="utf-8"?>
      <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
        <s:Body>
          <m:FindItemResponse xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages"
                              xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types">
            <m:ResponseMessages>
              <m:FindItemResponseMessage ResponseClass="Success">
                <m:ResponseCode>NoError</m:ResponseCode>
                <m:RootFolder TotalItemsInView="1" IncludesLastItemInRange="true">
                  <t:Items>
                    <t:Message>
                      <t:ItemId Id="AAMkADEx..." ChangeKey="CQAAABY..." />
                      <t:Subject>JLU Klausuranmeldung Bestätigung</t:Subject>
                      <t:DateTimeReceived>2026-10-18T10:00:00Z</t:DateTimeReceived>
                      <t:From>
                        <t:Mailbox>
                          <t:Name>Prüfungsamt FB07</t:Name>
                          <t:EmailAddress>pruefungsamt-fb07@uni-giessen.de</t:EmailAddress>
                        </t:Mailbox>
                      </t:From>
                      <t:IsRead>true</t:IsRead>
                    </t:Message>
                  </t:Items>
                </m:RootFolder>
              </m:FindItemResponseMessage>
            </m:ResponseMessages>
          </m:FindItemResponse>
        </s:Body>
      </s:Envelope>
    """.trimIndent()

    val client = EwsClient()
    val messages = client.parseEwsItemsFromXml(sampleEwsXml, "test_mailbox", "INBOX")
    assertEquals(1, messages.size)
    assertEquals("JLU Klausuranmeldung Bestätigung", messages[0].subject)
    assertEquals("Prüfungsamt FB07", messages[0].senderName)
    assertEquals("pruefungsamt-fb07@uni-giessen.de", messages[0].senderEmail)
  }

  @Test
  fun `verify TimezoneEngine campus calculations`() {
    val dual = TimezoneEngine.calculateDualTime(System.currentTimeMillis(), "Asia/Kolkata")
    assertNotNull(dual.jluBerlinTimeFormatted)
    assertNotNull(dual.userLocalTimeFormatted)
  }
}
