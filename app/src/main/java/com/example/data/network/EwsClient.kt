package com.example.data.network

import android.util.Base64
import android.util.Log
import com.example.data.local.entities.MailMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class EwsExecutionLog(
    val action: String,
    val endpointUrl: String,
    val requestSoapXml: String,
    val responseSoapXml: String?,
    val httpStatusCode: Int?,
    val durationMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class EwsSendResult(
    val isSuccess: Boolean,
    val messageId: String?,
    val responseCode: String,
    val httpStatusCode: Int,
    val soapLog: EwsExecutionLog
)

data class EwsReceiveResult(
    val isSuccess: Boolean,
    val messages: List<MailMessageEntity>,
    val responseCode: String,
    val httpStatusCode: Int,
    val soapLog: EwsExecutionLog
)

class EwsClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    companion object {
        private const val TAG = "EwsClient"
        const val DEFAULT_EWS_ENDPOINT = EwsEndpointPolicy.DEFAULT_ENDPOINT
        private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()
    }

    // Stores the most recent EWS SOAP transaction log for inspection in UI
    var lastExecutionLog: EwsExecutionLog? = null
        private set

    /**
     * Executes real EWS SOAP CreateItem request with MessageDisposition="SendAndSaveCopy"
     * against https://owa.uni-giessen.de/EWS/Exchange.asmx
     */
    suspend fun sendMessage(
        endpointUrl: String = DEFAULT_EWS_ENDPOINT,
        username: String,
        password: String,
        senderEmail: String,
        recipients: List<String>,
        subject: String,
        bodyHtml: String,
        isDraft: Boolean = false,
        mailboxId: String = "primary"
    ): EwsSendResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val disposition = if (isDraft) "SaveOnly" else "SendAndSaveCopy"
        val folderId = if (isDraft) "drafts" else "sentitems"

        require(username.isNotBlank() && password.isNotBlank()) { "Username and password are required." }
        require(senderEmail.isNotBlank()) { "Sender email is required." }
        require(recipients.isNotEmpty()) { "At least one recipient is required." }

        val soapRequest = buildCreateItemSoapEnvelope(
            disposition = disposition,
            folderId = folderId,
            senderEmail = senderEmail,
            subject = subject,
            bodyHtml = bodyHtml,
            recipients = recipients
        )

        Log.i(TAG, "[$endpointUrl] Executing EWS CreateItem ($disposition)...")

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(soapRequest.toRequestBody(XML_MEDIA_TYPE))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "http://schemas.microsoft.com/exchange/services/2006/messages/CreateItem")
            .header("User-Agent", "JLU-Mobile-Android/1.0 (EWS Client)")
            .header("X-AnchorMailbox", senderEmail)

        // Add Basic authentication header if credentials are supplied
        if (username.isNotBlank() && password.isNotBlank()) {
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            requestBuilder.header("Authorization", "Basic $encoded")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val duration = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val responseBody = response.body?.string() ?: ""

            Log.i(TAG, "EWS CreateItem finished with HTTP $statusCode in ${duration}ms")

            val responseCode = parseEwsResponseCode(responseBody)
            val messageId = if (statusCode in 200..299 && responseCode == "NoError") {
                parseEwsCreatedItemId(responseBody)
            } else {
                null
            }
            val isSuccess = statusCode in 200..299 &&
                    responseCode == "NoError" &&
                    !messageId.isNullOrBlank()

            val executionLog = EwsExecutionLog(
                action = "CreateItem ($disposition)",
                endpointUrl = endpointUrl,
                requestSoapXml = redactSensitiveXml(soapRequest),
                responseSoapXml = redactSensitiveXml(responseBody),
                httpStatusCode = statusCode,
                durationMs = duration,
                isSuccess = isSuccess,
                errorMessage = if (!isSuccess && responseCode == "NoError") {
                    "EWS returned NoError without a created ItemId."
                } else {
                    null
                }
            )
            lastExecutionLog = executionLog

            EwsSendResult(
                isSuccess = isSuccess,
                messageId = messageId,
                responseCode = if (isSuccess) "NoError" else responseCode.ifBlank { "HTTP_$statusCode" },
                httpStatusCode = statusCode,
                soapLog = executionLog
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.w(TAG, "EWS CreateItem network error: ${e.localizedMessage}")

            val executionLog = EwsExecutionLog(
                action = "CreateItem ($disposition)",
                endpointUrl = endpointUrl,
                requestSoapXml = redactSensitiveXml(soapRequest),
                responseSoapXml = null,
                httpStatusCode = null,
                durationMs = duration,
                isSuccess = false,
                errorMessage = e.localizedMessage
            )
            lastExecutionLog = executionLog

            EwsSendResult(
                isSuccess = false,
                messageId = null,
                responseCode = "NetworkError: ${e.localizedMessage}",
                httpStatusCode = 0,
                soapLog = executionLog
            )
        }
    }

    /**
     * Executes real EWS SOAP FindItem and GetItem requests against https://owa.uni-giessen.de/EWS/Exchange.asmx
     * to fetch messages from distinguished folders (inbox, sentitems, drafts, deleteditems).
     */
    suspend fun fetchMessages(
        endpointUrl: String = DEFAULT_EWS_ENDPOINT,
        username: String,
        password: String,
        distinguishedFolderId: String = "inbox",
        mailboxId: String = "primary",
        mailboxEmail: String,
        maxEntries: Int = 25
    ): EwsReceiveResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        require(maxEntries in 1..100) { "maxEntries must be between 1 and 100." }
        val ewsFolder = when (distinguishedFolderId.uppercase()) {
            "SENT" -> "sentitems"
            "DRAFTS" -> "drafts"
            "TRASH" -> "deleteditems"
            "INBOX" -> "inbox"
            else -> error("Unsupported EWS mail folder: $distinguishedFolderId")
        }

        require(username.isNotBlank() && password.isNotBlank()) { "Username and password are required." }
        require(mailboxEmail.isNotBlank()) { "Mailbox email is required." }
        val soapRequest = buildFindItemSoapEnvelope(folderId = ewsFolder, mailboxEmail = mailboxEmail, maxEntries = maxEntries)
        Log.i(TAG, "[$endpointUrl] Executing EWS FindItem for folder '$ewsFolder'...")

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(soapRequest.toRequestBody(XML_MEDIA_TYPE))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "http://schemas.microsoft.com/exchange/services/2006/messages/FindItem")
            .header("User-Agent", "JLU-Mobile-Android/1.0 (EWS Client)")
            .header("X-AnchorMailbox", mailboxEmail)

        if (username.isNotBlank() && password.isNotBlank()) {
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            requestBuilder.header("Authorization", "Basic $encoded")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val duration = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val responseBody = response.body?.string() ?: ""

            Log.i(TAG, "EWS FindItem completed with HTTP $statusCode in ${duration}ms")

            val parsedMessages = parseEwsItemsFromXml(responseBody, mailboxId, distinguishedFolderId.uppercase())

            val executionLog = EwsExecutionLog(
                action = "FindItem ($ewsFolder)",
                endpointUrl = endpointUrl,
                requestSoapXml = redactSensitiveXml(soapRequest),
                responseSoapXml = redactSensitiveXml(responseBody),
                httpStatusCode = statusCode,
                durationMs = duration,
                isSuccess = statusCode in 200..299 && parseEwsResponseCode(responseBody) == "NoError"
            )
            lastExecutionLog = executionLog

            EwsReceiveResult(
                isSuccess = executionLog.isSuccess,
                messages = parsedMessages,
                responseCode = if (executionLog.isSuccess) "NoError" else parseEwsResponseCode(responseBody).ifBlank { "HTTP_$statusCode" },
                httpStatusCode = statusCode,
                soapLog = executionLog
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.w(TAG, "EWS FindItem network error: ${e.localizedMessage}")

            val executionLog = EwsExecutionLog(
                action = "FindItem ($ewsFolder)",
                endpointUrl = endpointUrl,
                requestSoapXml = redactSensitiveXml(soapRequest),
                responseSoapXml = null,
                httpStatusCode = null,
                durationMs = duration,
                isSuccess = false,
                errorMessage = e.localizedMessage
            )
            lastExecutionLog = executionLog

            EwsReceiveResult(
                isSuccess = false,
                messages = emptyList(),
                responseCode = "NetworkError: ${e.localizedMessage}",
                httpStatusCode = 0,
                soapLog = executionLog
            )
        }
    }

    private fun buildCreateItemSoapEnvelope(
        disposition: String,
        folderId: String,
        subject: String,
        bodyHtml: String,
        recipients: List<String>,
        senderEmail: String
    ): String {
        val sanitizedSubject = escapeXml(subject)
        val sanitizedBody = escapeXml(bodyHtml)

        val recipientXml = recipients.joinToString("\n") { email ->
            """
            <t:Mailbox>
              <t:EmailAddress>${escapeXml(email.trim())}</t:EmailAddress>
            </t:Mailbox>
            """.trimIndent()
        }

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
              <soap:Header>
                <t:RequestServerVersion Version="Exchange2016" />
              </soap:Header>
              <soap:Body>
                <m:CreateItem MessageDisposition="$disposition">
                  <m:SavedItemFolderId>
                    <t:DistinguishedFolderId Id="$folderId">
                      <t:Mailbox>
                        <t:EmailAddress>${escapeXml(senderEmail.trim())}</t:EmailAddress>
                      </t:Mailbox>
                    </t:DistinguishedFolderId>
                  </m:SavedItemFolderId>
                  <m:Items>
                    <t:Message>
                      <t:ItemClass>IPM.Note</t:ItemClass>
                      <t:From>
                        <t:Mailbox>
                          <t:EmailAddress>${escapeXml(senderEmail.trim())}</t:EmailAddress>
                        </t:Mailbox>
                      </t:From>
                      <t:Subject>$sanitizedSubject</t:Subject>
                      <t:Body BodyType="HTML">$sanitizedBody</t:Body>
                      <t:ToRecipients>
                        $recipientXml
                      </t:ToRecipients>
                    </t:Message>
                  </m:Items>
                </m:CreateItem>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
    }

    private fun buildFindItemSoapEnvelope(folderId: String, mailboxEmail: String, maxEntries: Int): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:t="http://schemas.microsoft.com/exchange/services/2006/types"
                           xmlns:m="http://schemas.microsoft.com/exchange/services/2006/messages">
              <soap:Header>
                <t:RequestServerVersion Version="Exchange2016" />
              </soap:Header>
              <soap:Body>
                <m:FindItem Traversal="Shallow">
                  <m:ItemShape>
                    <t:BaseShape>IdOnly</t:BaseShape>
                    <t:AdditionalProperties>
                      <t:FieldURI FieldURI="item:Subject" />
                      <t:FieldURI FieldURI="item:Body" />
                      <t:FieldURI FieldURI="item:DateTimeReceived" />
                      <t:FieldURI FieldURI="message:From" />
                      <t:FieldURI FieldURI="message:ToRecipients" />
                      <t:FieldURI FieldURI="message:IsRead" />
                      <t:FieldURI FieldURI="item:HasAttachments" />
                    </t:AdditionalProperties>
                  </m:ItemShape>
                  <m:IndexedPageItemView MaxEntriesReturned="$maxEntries" Offset="0" BasePoint="Beginning" />
                  <m:ParentFolderIds>
                    <t:DistinguishedFolderId Id="$folderId">
                      <t:Mailbox>
                        <t:EmailAddress>${escapeXml(mailboxEmail.trim())}</t:EmailAddress>
                      </t:Mailbox>
                    </t:DistinguishedFolderId>
                  </m:ParentFolderIds>
                </m:FindItem>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
    }

    /**
     * Parses EWS SOAP response XML into MailMessageEntity items
     */
    fun parseEwsItemsFromXml(
        xmlContent: String,
        mailboxId: String,
        folderName: String
    ): List<MailMessageEntity> {
        if (xmlContent.isBlank()) return emptyList()

        val messages = mutableListOf<MailMessageEntity>()
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentSubject = ""
            var currentSenderName = ""
            var currentSenderEmail = ""
            val currentRecipients = mutableListOf<String>()
            var currentReceivedMs = 0L
            var currentBody = ""
            var currentBodyType = ""
            var currentIsRead = false
            var currentHasAttachments = false
            var currentId = ""
            var insideMessage = false
            var insideFrom = false
            var insideToRecipients = false
            var insideBody = false
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when {
                            currentTag.equals("Message", ignoreCase = true) -> {
                                insideMessage = true
                                currentSubject = ""
                                currentSenderName = ""
                                currentSenderEmail = ""
                                currentRecipients.clear()
                                currentReceivedMs = 0L
                                currentBody = ""
                                currentBodyType = ""
                                currentIsRead = false
                                currentHasAttachments = false
                                currentId = ""
                            }
                            insideMessage && currentTag.equals("From", ignoreCase = true) -> insideFrom = true
                            insideMessage && currentTag.equals("ToRecipients", ignoreCase = true) -> insideToRecipients = true
                            insideMessage && currentTag.equals("Body", ignoreCase = true) -> {
                                insideBody = true
                                currentBodyType = parser.getAttributeValue(null, "BodyType").orEmpty()
                            }
                            insideMessage && currentTag.equals("ItemId", ignoreCase = true) -> {
                                currentId = parser.getAttributeValue(null, "Id").orEmpty()
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (insideMessage && text.isNotEmpty()) {
                            when {
                                currentTag.equals("Subject", ignoreCase = true) ->
                                    currentSubject = text
                                insideFrom && currentTag.equals("Name", ignoreCase = true) && currentSenderName.isBlank() ->
                                    currentSenderName = text
                                insideFrom && currentTag.equals("EmailAddress", ignoreCase = true) && currentSenderEmail.isBlank() ->
                                    currentSenderEmail = text
                                insideToRecipients && currentTag.equals("EmailAddress", ignoreCase = true) ->
                                    currentRecipients += text
                                currentTag.equals("DateTimeReceived", ignoreCase = true) ->
                                    currentReceivedMs = parseIsoDateTime(text)
                                insideBody && currentTag.equals("Body", ignoreCase = true) ->
                                    currentBody = text
                                currentTag.equals("IsRead", ignoreCase = true) ->
                                    currentIsRead = text.equals("true", ignoreCase = true)
                                currentTag.equals("HasAttachments", ignoreCase = true) ->
                                    currentHasAttachments = text.equals("true", ignoreCase = true)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when {
                            parser.name.equals("From", ignoreCase = true) -> insideFrom = false
                            parser.name.equals("ToRecipients", ignoreCase = true) -> insideToRecipients = false
                            parser.name.equals("Body", ignoreCase = true) -> insideBody = false
                            parser.name.equals("Message", ignoreCase = true) && insideMessage -> {
                                if (currentId.isNotBlank() &&
                                    (currentSubject.isNotBlank() || currentSenderEmail.isNotBlank())
                                ) {
                                    val bodyIsHtml = currentBodyType.equals("HTML", ignoreCase = true)
                                    messages += MailMessageEntity(
                                        id = currentId,
                                        mailboxId = mailboxId,
                                        folder = folderName,
                                        threadId = "th_" + currentId,
                                        subject = currentSubject.ifBlank { "(No Subject)" },
                                        senderName = currentSenderName,
                                        senderEmail = currentSenderEmail,
                                        recipients = currentRecipients.joinToString(", "),
                                        receivedTimestamp = currentReceivedMs,
                                        bodyText = if (bodyIsHtml) stripHtml(currentBody) else currentBody,
                                        bodyHtml = if (bodyIsHtml) currentBody else "",
                                        isRead = currentIsRead,
                                        isFlagged = false,
                                        hasAttachments = currentHasAttachments
                                    )
                                }
                                insideMessage = false
                                insideFrom = false
                                insideToRecipients = false
                                insideBody = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "XML parsing of EWS response failed")
        }

        return messages
    }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()


    private fun parseEwsResponseCode(xmlContent: String): String {
        if (xmlContent.isBlank()) return ""
        var found = ""
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var currentTag = ""
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> if (
                        currentTag.equals("ResponseCode", ignoreCase = true) && found.isBlank()
                    ) {
                        found = parser.text.trim()
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            return ""
        }
        return found
    }

    private fun parseEwsCreatedItemId(xmlContent: String): String? {
        if (xmlContent.isBlank()) return null
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var inItems = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name.equals("Items", ignoreCase = true)) inItems = true
                        if (inItems && parser.name.equals("ItemId", ignoreCase = true)) {
                            val id = parser.getAttributeValue(null, "Id")
                            if (!id.isNullOrBlank()) return id
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name.equals("Items", ignoreCase = true)) inItems = false
                }
                eventType = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun redactSensitiveXml(xml: String): String {
        if (xml.isBlank()) return xml
        return xml.replace(
            Regex("""(<(?:\w+:)?Body\b[^>]*>)([\s\S]*?)(</(?:\w+:)?Body>)""", RegexOption.IGNORE_CASE),
            "$1[REDACTED_EMAIL_BODY]$3"
        )
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun parseIsoDateTime(isoString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(isoString.substringBefore("Z").substringBefore("."))?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
