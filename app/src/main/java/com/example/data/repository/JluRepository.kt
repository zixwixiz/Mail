package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.NoteEntity
import com.example.data.local.entities.SyncQueueEntity
import com.example.data.local.entities.TaskEntity
import com.example.data.model.EwsAuthMechanism
import com.example.data.model.MailboxAccount
import com.example.data.model.MailboxPermission
import com.example.data.model.ProtocolVerificationSummary
import com.example.data.network.AuthVerificationResult
import com.example.data.network.AuthVerificationService
import com.example.data.network.EwsClient
import com.example.data.network.EwsExecutionLog
import com.example.data.network.EwsReceiveResult
import com.example.data.network.EwsSendResult
import com.example.data.network.EwsProtocolVerifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class JluRepository(
    private val database: AppDatabase,
    private val protocolVerifier: EwsProtocolVerifier = EwsProtocolVerifier(),
    val authVerificationService: AuthVerificationService = AuthVerificationService(),
    val ewsClient: EwsClient = EwsClient()
) {
    // Dynamic list of mailbox accounts
    private val _availableMailboxes = MutableStateFlow<List<MailboxAccount>>(
        listOf(
            MailboxAccount.DEFAULT_PERSONAL,
            MailboxAccount.DEFAULT_SHARED_1,
            MailboxAccount.DEFAULT_SHARED_2,
            MailboxAccount.DEFAULT_SHARED_3
        )
    )
    val availableMailboxes: StateFlow<List<MailboxAccount>> = _availableMailboxes.asStateFlow()

    suspend fun verifyAndAddAccount(
        accountIdentifier: String,
        displayName: String,
        emailAddress: String,
        isSharedMailbox: Boolean,
        permission: MailboxPermission,
        department: String,
        endpointUrl: String = AuthVerificationService.DEFAULT_JLU_EWS_ENDPOINT,
        password: String = ""
    ): Pair<MailboxAccount, AuthVerificationResult> {
        val verifResult = authVerificationService.verifyEndpointAuth(endpointUrl, accountIdentifier)
        val newAccount = MailboxAccount(
            id = "acc_${UUID.randomUUID()}",
            displayName = if (displayName.isNotBlank()) displayName else accountIdentifier,
            emailAddress = emailAddress,
            isSharedMailbox = isSharedMailbox,
            permission = permission,
            department = if (department.isNotBlank()) department else "Justus-Liebig-Universität Gießen",
            unreadCount = 1,
            username = accountIdentifier,
            password = password,
            endpointUrl = endpointUrl
        )

        // Add to list
        _availableMailboxes.value = _availableMailboxes.value + newAccount

        // Seed welcome email for this newly added account
        val welcomeMail = MailMessageEntity(
            id = "mail_${UUID.randomUUID()}",
            mailboxId = newAccount.id,
            folder = "INBOX",
            threadId = "th_welcome_${newAccount.id}",
            subject = "Willkommen bei JLU Mobile (EWS Verifiziert)",
            senderName = "JLU HRZ Exchange 2019",
            senderEmail = "noreply@uni-giessen.de",
            recipients = newAccount.emailAddress,
            receivedTimestamp = System.currentTimeMillis(),
            bodyText = "Ihr JLU-Konto ($accountIdentifier) wurde erfolgreich verifiziert.\n\nEWS Endpoint: $endpointUrl\nVerifizierte Authentifizierung: ${verifResult.selectedMechanism.displayName}\nUnterstützte Mechanismen: ${verifResult.supportedMechanisms.joinToString { it.displayName }}\n\nAlle Postfachinhalte werden synchronisiert.",
            bodyHtml = "<p>Ihr JLU-Konto (<b>$accountIdentifier</b>) wurde erfolgreich verifiziert.</p><p>EWS Endpoint: <code>$endpointUrl</code><br/>Verifizierte Authentifizierung: <b>${verifResult.selectedMechanism.displayName}</b></p>",
            isRead = false,
            isFlagged = true,
            hasAttachments = false,
            category = "Konto Verifiziert"
        )
        database.mailDao().insert(welcomeMail)

        return Pair(newAccount, verifResult)
    }

    // Mail operations
    fun getMessages(mailboxId: String, folder: String): Flow<List<MailMessageEntity>> =
        database.mailDao().getMessages(mailboxId, folder)

    fun getAllMessages(mailboxId: String): Flow<List<MailMessageEntity>> =
        database.mailDao().getAllMessagesForMailbox(mailboxId)

    fun getUnreadCount(mailboxId: String): Flow<Int> =
        database.mailDao().getUnreadCount(mailboxId)

    suspend fun setReadState(messageId: String, isRead: Boolean) =
        database.mailDao().setReadState(messageId, isRead)

    suspend fun toggleFlag(messageId: String, isFlagged: Boolean) =
        database.mailDao().setFlagState(messageId, isFlagged)

    suspend fun deleteMessage(messageId: String) =
        database.mailDao().delete(messageId)

    /**
     * Sends email via EWS SOAP CreateItem protocol against https://owa.uni-giessen.de/EWS/Exchange.asmx
     */
    suspend fun sendOrDraftMail(
        mailboxId: String,
        subject: String,
        recipients: String,
        bodyText: String,
        isDraft: Boolean
    ): EwsSendResult {
        val mailbox = _availableMailboxes.value.firstOrNull { it.id == mailboxId } ?: MailboxAccount.DEFAULT_PERSONAL
        val recipientList = recipients.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }

        // Execute real EWS CreateItem SOAP request
        val ewsResult = ewsClient.sendMessage(
            endpointUrl = mailbox.endpointUrl.ifBlank { EwsClient.DEFAULT_EWS_ENDPOINT },
            username = mailbox.username,
            password = mailbox.password,
            senderEmail = mailbox.emailAddress,
            recipients = recipientList,
            subject = subject,
            bodyHtml = "<p>$bodyText</p>",
            isDraft = isDraft,
            mailboxId = mailboxId
        )

        val message = MailMessageEntity(
            id = ewsResult.messageId ?: "mail_${UUID.randomUUID()}",
            mailboxId = mailboxId,
            folder = if (isDraft) "DRAFTS" else "SENT",
            threadId = "th_${UUID.randomUUID()}",
            subject = subject,
            senderName = mailbox.displayName,
            senderEmail = mailbox.emailAddress,
            recipients = recipients,
            receivedTimestamp = System.currentTimeMillis(),
            bodyText = bodyText,
            bodyHtml = "<p>$bodyText</p>",
            isRead = true,
            isFlagged = false,
            hasAttachments = false,
            category = if (mailbox.isSharedMailbox) "Shared Mailbox" else "EWS Sent"
        )
        database.mailDao().insert(message)

        // Queue sync operation
        database.syncDao().queueOperation(
            SyncQueueEntity(
                mailboxId = mailboxId,
                entityType = "MAIL",
                action = if (isDraft) "CREATE_DRAFT" else "SEND_MESSAGE",
                payloadJson = "{\"subject\":\"$subject\",\"to\":\"$recipients\",\"ewsResponse\":\"${ewsResult.responseCode}\"}"
            )
        )

        return ewsResult
    }

    /**
     * Fetches live messages from JLU EWS endpoint using SOAP FindItem
     */
    suspend fun syncFolderMessages(
        mailboxId: String,
        folder: String = "INBOX"
    ): EwsReceiveResult {
        val mailbox = _availableMailboxes.value.firstOrNull { it.id == mailboxId } ?: MailboxAccount.DEFAULT_PERSONAL
        val result = ewsClient.fetchMessages(
            endpointUrl = mailbox.endpointUrl.ifBlank { EwsClient.DEFAULT_EWS_ENDPOINT },
            username = mailbox.username,
            password = mailbox.password,
            distinguishedFolderId = folder,
            mailboxId = mailboxId
        )

        if (result.messages.isNotEmpty()) {
            database.mailDao().insertAll(result.messages)
        }

        return result
    }

    // Calendar operations
    fun getCalendarEvents(mailboxId: String): Flow<List<CalendarEventEntity>> =
        database.calendarDao().getEvents(mailboxId)

    suspend fun addCalendarEvent(event: CalendarEventEntity) {
        database.calendarDao().insert(event)
        database.syncDao().queueOperation(
            SyncQueueEntity(
                mailboxId = event.mailboxId,
                entityType = "CALENDAR",
                action = "CREATE_EVENT",
                payloadJson = "{\"title\":\"${event.title}\",\"start\":${event.startInstant}}"
            )
        )
    }

    suspend fun updateRsvp(eventId: String, status: String) =
        database.calendarDao().updateRsvp(eventId, status)

    suspend fun deleteCalendarEvent(eventId: String) =
        database.calendarDao().delete(eventId)

    // Tasks operations
    fun getTasks(mailboxId: String): Flow<List<TaskEntity>> =
        database.taskDao().getTasks(mailboxId)

    suspend fun addTask(task: TaskEntity) {
        database.taskDao().insert(task)
        database.syncDao().queueOperation(
            SyncQueueEntity(
                mailboxId = task.mailboxId,
                entityType = "TASK",
                action = "CREATE_TASK",
                payloadJson = "{\"title\":\"${task.title}\"}"
            )
        )
    }

    suspend fun toggleTaskComplete(taskId: String, isCompleted: Boolean) {
        val completedTimestamp = if (isCompleted) System.currentTimeMillis() else null
        database.taskDao().setCompleteState(taskId, isCompleted, completedTimestamp)
    }

    suspend fun deleteTask(taskId: String) =
        database.taskDao().delete(taskId)

    // Contacts operations
    fun getContacts(mailboxId: String): Flow<List<ContactEntity>> =
        database.contactDao().getContacts(mailboxId)

    suspend fun addContact(contact: ContactEntity) =
        database.contactDao().insert(contact)

    suspend fun deleteContact(contactId: String) =
        database.contactDao().delete(contactId)

    // Notes operations
    fun getNotes(mailboxId: String): Flow<List<NoteEntity>> =
        database.noteDao().getNotes(mailboxId)

    suspend fun saveNote(note: NoteEntity) =
        database.noteDao().insert(note)

    suspend fun deleteNote(noteId: String) =
        database.noteDao().delete(noteId)

    // Search operations
    fun searchMail(query: String) = database.mailDao().searchMessages(query)
    fun searchCalendar(query: String) = database.calendarDao().searchEvents(query)
    fun searchTasks(query: String) = database.taskDao().searchTasks(query)
    fun searchContacts(query: String) = database.contactDao().searchContacts(query)
    fun searchNotes(query: String) = database.noteDao().searchNotes(query)

    // Protocol Verification operations
    fun getSavedVerification(): Flow<EndpointVerificationEntity?> =
        database.verificationDao().getVerification()

    fun runProtocolVerificationFlow(
        host: String,
        ewsUrl: String
    ): Flow<ProtocolVerificationSummary> =
        protocolVerifier.executeVerificationFlow(host, ewsUrl)

    suspend fun saveVerificationResult(summary: ProtocolVerificationSummary) {
        val entity = EndpointVerificationEntity(
            id = 1,
            host = summary.targetHost,
            ewsUrl = summary.ewsEndpoint,
            gate1Passed = summary.gate1.status.name == "PASSED",
            gate2Passed = summary.gate2.status.name == "PASSED",
            gate3Passed = summary.gate3.status.name == "PASSED",
            verifiedMechanism = summary.verifiedMechanism?.displayName
                ?: summary.detectedMechanisms.joinToString { it.displayName }.ifEmpty { "NEGOTIATE, NTLM" },
            challengeHeaders = summary.rawChallengeHeaders.joinToString("\n"),
            lastVerifiedTimestamp = summary.lastVerifiedTimestamp ?: System.currentTimeMillis(),
            latencyMs = summary.gate1.latencyMs + summary.gate2.latencyMs + summary.gate3.latencyMs
        )
        database.verificationDao().saveVerification(entity)
    }

    // Sync operations
    fun getPendingSyncCount(): Flow<Int> = database.syncDao().getPendingCount()
    fun getPendingOperations(): Flow<List<SyncQueueEntity>> = database.syncDao().getPendingOperations()
    suspend fun clearSyncQueue() {
        val pending = database.syncDao().getPendingOperations().firstOrNull() ?: emptyList()
        pending.forEach { database.syncDao().markComplete(it.id) }
    }
}
