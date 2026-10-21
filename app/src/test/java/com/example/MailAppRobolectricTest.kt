package com.example

import com.example.data.model.EwsAuthMechanism
import com.example.data.network.AuthVerificationService
import com.example.data.network.EwsClient
import com.example.data.network.EwsEndpointPolicy
import com.example.data.local.entities.MailMessageEntity
import com.example.domain.ContactDiscovery
import com.example.domain.EmailChangeDetector
import com.example.domain.EmailSafetyAnalyzer
import com.example.domain.EmailSafetyLevel
import com.example.domain.FollowUpDetector
import com.example.domain.MailBriefingBuilder
import com.example.domain.MailQuickSummaryBuilder
import com.example.domain.MailSearchQueryParser
import com.example.domain.MailTextFormatter
import com.example.domain.MailTranslationFingerprint
import com.example.domain.ThreadSummaryBuilder
import com.example.domain.TranslationService
import com.example.domain.ReadingPriority
import com.example.domain.ReadingQueueClassifier
import com.example.domain.SmartActionSuggestion
import com.example.domain.SmartExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

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

    @Test
    fun mailTextFormatterDecodesHtmlEntitiesAndRemovesMarkup() {
        assertEquals("Re: Meeting & notes", MailTextFormatter.plainText("<b>Re:</b> Meeting &amp; notes"))
    }

    @Test
    fun mailTextFormatterNormalizesWhitespace() {
        assertEquals("first second", MailTextFormatter.plainText("  first\n\tsecond  "))
    }

    @Test
    fun smartExtractorParsesEnglishDateAndPreservesSourceTimezone() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val referenceTimestamp = Calendar.getInstance(berlin).apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis

        val suggestion = SmartExtractor.extractSuggestions(
            "Team Meeting",
            "The meeting is on 12 November 2026 at 10:30 AM.",
            "mail-english-date",
            referenceTimestamp,
            "Europe/Berlin"
        ).filterIsInstance<SmartActionSuggestion.CalendarSuggestion>().single()

        val start = Calendar.getInstance(berlin).apply { timeInMillis = suggestion.startInstant }
        assertEquals(2026, start.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, start.get(Calendar.MONTH))
        assertEquals(12, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, start.get(Calendar.MINUTE))
        assertEquals("Europe/Berlin", suggestion.sourceTimeZoneId)
    }

    @Test
    fun smartExtractorResolvesTomorrowRelativeToEmailTimestamp() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val received = Calendar.getInstance(berlin).apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 9, 0, 0)
        }.timeInMillis

        val suggestion = SmartExtractor.extractSuggestions(
            "Team Meeting",
            "The meeting is tomorrow at 10:15 AM.",
            "mail-relative-date",
            received,
            "Europe/Berlin"
        ).filterIsInstance<SmartActionSuggestion.CalendarSuggestion>().single()

        val start = Calendar.getInstance(berlin).apply { timeInMillis = suggestion.startInstant }
        assertEquals(2026, start.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, start.get(Calendar.MONTH))
        assertEquals(2, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, start.get(Calendar.MINUTE))
    }

    @Test
    fun readingQueuePrioritizesDeadlinesAndFlags() {
        fun message(subject: String, flagged: Boolean = false) = MailMessageEntity(
            id = subject,
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = subject,
            subject = subject,
            senderName = "Sender",
            senderEmail = "sender@example.com",
            recipients = "me@example.com",
            receivedTimestamp = 1L,
            bodyText = "",
            bodyHtml = "",
            isRead = false,
            isFlagged = flagged,
            hasAttachments = false
        )

        assertEquals(
            ReadingPriority.ACTION_REQUIRED,
            ReadingQueueClassifier.classify(message("Deadline: submit by Friday")).priority
        )
        assertEquals(
            ReadingPriority.IMPORTANT,
            ReadingQueueClassifier.classify(message("Meeting notes", flagged = true)).priority
        )
        assertEquals(
            ReadingPriority.ROUTINE,
            ReadingQueueClassifier.classify(message("Weekly newsletter")).priority
        )
    }

    @Test
    fun mailBriefingUsesLocalDayAndExcludesSentMessages() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.OCTOBER, 21, 12, 0, 0)
        }.timeInMillis
        val todayTimestamp = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 10)
        }.timeInMillis
        val received = MailMessageEntity(
            id = "today-1",
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-1",
            subject = "Deadline: submit form",
            senderName = "Sender",
            senderEmail = "sender@example.com",
            recipients = "me@example.com",
            receivedTimestamp = todayTimestamp,
            bodyText = "",
            bodyHtml = "",
            isRead = false,
            isFlagged = false,
            hasAttachments = false
        )
        val sent = received.copy(id = "sent-1", folder = "SENT")
        val old = received.copy(id = "old-1", receivedTimestamp = now - 8L * 24 * 60 * 60 * 1000)

        val briefing = MailBriefingBuilder.build(listOf(received, sent, old), now, "Asia/Kolkata")
        assertEquals(1, briefing.todayCount)
        assertEquals(1, briefing.todayUnreadCount)
        assertEquals(1, briefing.todayActionCount)
        assertEquals(1, briefing.weekCount)
    }

    @Test
    fun contactDiscoveryDeduplicatesAndExcludesOwnAndAutomatedAddresses() {
        fun message(
            id: String,
            senderName: String,
            senderEmail: String,
            recipients: String,
            body: String = ""
        ) = MailMessageEntity(
            id = id,
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = id,
            subject = "Project update",
            senderName = senderName,
            senderEmail = senderEmail,
            recipients = recipients,
            receivedTimestamp = 10L,
            bodyText = body,
            bodyHtml = "",
            isRead = true,
            isFlagged = false,
            hasAttachments = false
        )

        val messages = listOf(
            message(
                "mail-1",
                "Alice Example",
                "alice@example.com",
                "me@example.com, Bob.Brown@example.org",
                "Regards\nPhone: +49 123 456 789\nCompany: Example Org"
            ),
            message("mail-2", "Automated", "no-reply@example.com", "me@example.com")
        )
        val candidates = ContactDiscovery.discover(
            messages = messages,
            existingEmails = setOf("already-saved@example.com"),
            ownEmail = "me@example.com"
        )

        assertEquals(2, candidates.size)
        val alice = candidates.single { it.email == "alice@example.com" }
        assertEquals("Alice Example", alice.displayName)
        assertEquals("+49 123 456 789", alice.phone)
        assertTrue(alice.organization.contains("Example Org"))
        assertEquals("Bob Brown", candidates.single { it.email == "bob.brown@example.org" }.displayName)
    }

    @Test
    fun emailSafetyAnalyzerFlagsUrgentSensitiveLinksWithoutClaimingCertainty() {
        val message = MailMessageEntity(
            id = "suspicious-1",
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-1",
            subject = "Urgent action required",
            senderName = "Unknown Sender",
            senderEmail = "unknown@example.com",
            recipients = "me@example.com",
            receivedTimestamp = 1L,
            bodyText = "Verify your account immediately at https://example.invalid/login and enter your password.",
            bodyHtml = "",
            isRead = false,
            isFlagged = false,
            hasAttachments = false
        )
        val assessment = EmailSafetyAnalyzer.assess(message)
        assertEquals(EmailSafetyLevel.HIGH, assessment.level)
        assertTrue(assessment.reasons.isNotEmpty())
    }

    @Test
    fun emailSafetyAnalyzerDoesNotFlagRoutineMailWithoutRiskSignals() {
        val message = MailMessageEntity(
            id = "routine-1",
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-1",
            subject = "Meeting notes",
            senderName = "Colleague",
            senderEmail = "colleague@example.com",
            recipients = "me@example.com",
            receivedTimestamp = 1L,
            bodyText = "Here are the notes from today's meeting.",
            bodyHtml = "",
            isRead = false,
            isFlagged = false,
            hasAttachments = false
        )
        assertEquals(EmailSafetyLevel.NONE, EmailSafetyAnalyzer.assess(message).level)
    }

    @Test
    fun emailChangeDetectorFindsChangedTimeAndAmountWithinThread() {
        fun message(id: String, received: Long, body: String) = MailMessageEntity(
            id = id,
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-42",
            subject = "Meeting update",
            senderName = "Sender",
            senderEmail = "sender@example.com",
            recipients = "me@example.com",
            receivedTimestamp = received,
            bodyText = body,
            bodyHtml = "",
            isRead = true,
            isFlagged = false,
            hasAttachments = false
        )

        val previous = message("old", 1L, "Time: 10:00 Uhr. Fee: 50 EUR.")
        val current = message("new", 2L, "Time: 11:30 Uhr. Fee: 75 EUR.")
        val changes = EmailChangeDetector.detect(current, listOf(previous, current))

        assertEquals(2, changes.size)
        assertTrue(changes.any { it.label == "Time" && it.previousValue != it.currentValue })
        assertTrue(changes.any { it.label == "Amount" && it.previousValue != it.currentValue })
    }

    @Test
    fun mailSearchParserAppliesGmailStyleFiltersAndLeavesUnknownOperatorsAsText() {
        val parsed = MailSearchQueryParser.parse(
            "from:alice subject:\"invoice report\" has:attachment is:unread after:2026-01-01 in:inbox"
        )
        assertEquals("", parsed.text)
        assertEquals("alice", parsed.from)
        assertEquals("invoice report", parsed.subject)
        assertTrue(parsed.hasAttachment)
        assertTrue(parsed.unreadOnly)
        assertEquals("INBOX", parsed.folder)
        assertTrue(parsed.afterTimestamp != null)

        val unknown = MailSearchQueryParser.parse("foo:bar invoice")
        assertEquals("foo:bar invoice", unknown.text)
    }

    @Test
    fun automaticTranslationOnlyRunsForLikelyGermanText() {
        assertTrue(TranslationService.isLikelyGerman("Sehr geehrte Damen und Herren, bitte beachten Sie die Frist."))
        assertFalse(TranslationService.isLikelyGerman("Hi, please review the attached document and let me know."))
    }

    @Test
    fun quickSummaryExtractsOpeningSentencesAndVerifiableFacts() {
        val message = MailMessageEntity(
            id = "summary-1",
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-1",
            subject = "Application deadline",
            senderName = "Office",
            senderEmail = "office@example.com",
            recipients = "me@example.com",
            receivedTimestamp = 1L,
            bodyText = "Please submit the form by 12.11.2026 at 10:30 Uhr. The fee is 50 EUR. Contact us for questions.",
            bodyHtml = "",
            isRead = false,
            isFlagged = false,
            hasAttachments = true,
            attachments = "application.pdf"
        )
        val summary = MailQuickSummaryBuilder.build(message)
        assertTrue(summary.summary.startsWith("Please submit the form"))
        assertTrue(summary.keyPoints.any { it.startsWith("Dates mentioned:") })
        assertTrue(summary.keyPoints.any { it.startsWith("Times mentioned:") })
        assertTrue(summary.keyPoints.any { it.startsWith("Amounts mentioned:") })
        assertTrue(summary.keyPoints.any { it.contains("application.pdf") })
    }

    @Test
    fun threadSummaryUsesLatestMessageAndDistinctParticipants() {
        fun message(id: String, received: Long, sender: String, body: String) = MailMessageEntity(
            id = id,
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-summary",
            subject = "Project update",
            senderName = sender,
            senderEmail = "${sender.lowercase().replace(" ", ".")}@example.com",
            recipients = "me@example.com",
            receivedTimestamp = received,
            bodyText = body,
            bodyHtml = "",
            isRead = true,
            isFlagged = false,
            hasAttachments = false
        )
        val summary = ThreadSummaryBuilder.build(
            listOf(
                message("old", 1L, "Alice Example", "We will meet at 10:00."),
                message("new", 2L, "Bob Example", "The meeting moved to 11:00.")
            )
        )

        assertEquals(2, summary?.messageCount)
        assertEquals(listOf("Alice Example", "Bob Example"), summary?.participants)
        assertTrue(summary?.latestSummary?.contains("moved to 11:00") == true)
    }

    @Test
    fun followUpDetectorOnlySuggestsExplicitPromises() {
        fun message(body: String) = MailMessageEntity(
            id = "follow-up",
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-1",
            subject = "Document",
            senderName = "Sender",
            senderEmail = "sender@example.com",
            recipients = "me@example.com",
            receivedTimestamp = 1L,
            bodyText = body,
            bodyHtml = "",
            isRead = false,
            isFlagged = false,
            hasAttachments = false
        )
        assertTrue(FollowUpDetector.detect(message("I'll send you the revised document tomorrow.")) != null)
        assertEquals(null, FollowUpDetector.detect(message("Thank you for your message. Have a nice day.")))
    }

    @Test
    fun translationFingerprintChangesWhenMessageContentChanges() {
        val message = MailMessageEntity(
            id = "fingerprint-1",
            mailboxId = "mailbox-1",
            folder = "INBOX",
            threadId = "thread-1",
            subject = "Original subject",
            senderName = "Sender",
            senderEmail = "sender@example.com",
            recipients = "me@example.com",
            receivedTimestamp = 1L,
            bodyText = "Original body",
            bodyHtml = "",
            isRead = false,
            isFlagged = false,
            hasAttachments = false
        )
        assertEquals(
            MailTranslationFingerprint.forMessage(message),
            MailTranslationFingerprint.forMessage(message.copy(isRead = true))
        )
        assertTrue(
            MailTranslationFingerprint.forMessage(message) !=
                MailTranslationFingerprint.forMessage(message.copy(bodyText = "Updated body"))
        )
    }
}
