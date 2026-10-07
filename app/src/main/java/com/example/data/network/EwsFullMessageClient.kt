package com.example.data.network

import com.example.data.local.entities.MailMessageEntity

class EwsFullMessageClient(private val http: ExchangeHttpClient = ExchangeHttpClient()) {
    suspend fun get(endpoint: String, username: String, password: String, mailboxEmail: String, mailboxId: String, folder: String, ids: List<String>): List<MailMessageEntity> {
        if (ids.isEmpty()) return emptyList()
        val itemIds = ids.joinToString("\n") { "<t:ItemId Id=\"${xml(it)}\" />" }
        val soap = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
              xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
              xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
              <soap:Header><t:RequestServerVersion Version="Exchange2016"/></soap:Header>
              <soap:Body><m:GetItem>
                <m:ItemShape>
                  <t:BaseShape>IdOnly</t:BaseShape>
                  <t:BodyType>HTML</t:BodyType>
                  <t:AdditionalProperties>
                    <t:FieldURI FieldURI="item:Subject"/>
                    <t:FieldURI FieldURI="item:DateTimeReceived"/>
                    <t:FieldURI FieldURI="message:From"/>
                    <t:FieldURI FieldURI="message:ToRecipients"/>
                    <t:FieldURI FieldURI="message:CcRecipients"/>
                    <t:FieldURI FieldURI="message:IsRead"/>
                    <t:FieldURI FieldURI="item:HasAttachments"/>
                    <t:FieldURI FieldURI="item:Body"/>
                  </t:AdditionalProperties>
                </m:ItemShape>
                <m:ItemIds>ITEM_IDS</m:ItemIds>
              </m:GetItem></soap:Body>
            </soap:Envelope>
        """.trimIndent().replace("ITEM_IDS", itemIds)

        val response = http.postSoap(
            endpointUrl = endpoint,
            username = username,
            password = password,
            soapAction = "http://schemas.microsoft.com/exchange/services/2006/messages/GetItem",
            soapXml = soap,
            anchorMailbox = mailboxEmail
        )
        if (response.statusCode !in 200..299) return emptyList()
        return EwsClient().parseEwsItemsFromXml(response.body, mailboxId, folder)
    }

    private fun xml(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}
