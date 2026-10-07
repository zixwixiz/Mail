package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.NoteEntity
import com.example.data.local.entities.TaskEntity
import com.example.data.model.MailboxAccount
import com.example.data.model.MailboxPermission
import com.example.data.model.ProtocolVerificationSummary
import com.example.data.network.AuthVerificationResult
import com.example.data.network.AuthVerificationService
import com.example.data.network.EwsClient
import com.example.data.network.EwsEndpointPolicy
import com.example.data.network.EwsProtocolVerifier
import com.example.data.network.EwsReceiveResult
import com.example.data.network.EwsSendResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class JluRepository(
    private val database: AppDatabase,
    private val protocolVerifier: EwsProtocolVerifier = EwsProtocolVerifier(),
    val authVerificationService: AuthVerificationService = AuthVerificationService(),
    val ewsClient: EwsClient = EwsClient()
) {
    private val _availableMailboxes = MutableStateFlow<List<MailboxAccount>>(emptyList())
    private val sessionPasswords = mutableMapOf<String, String>()
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
        require(accountIdentifier.isNotBlank()) { "Account identifier is required." }
        require(emailAddress.isNotBlank()) { "Email address is required." }
        require(password.isNotBlank()) { "Password is required." }
        EwsEndpointPolicy.validate(endpointUrl, emailAddress)?.let { error ->
            throw IllegalArgumentException(error)
        }

        val verification = authVerificationService.verifyEndpointAuth(endpointUrl, accountIdentifier)
        check(verification.isSuccess) {
            verification.errorMessage ?: "EWS authentication challenge verification failed."
        }

        val account = MailboxAccount(
            id = "acc_" + UUID.randomUUID(),
            displayName = displayName.ifBlank { accountIdentifier.trim() },
            emailAddress = emailAddress.trim(),
            isSharedMailbox = isSharedMailbox,
            permission = permission,
            department = department.ifBlank { "Justus-Liebig-Universität Gießen" },
            username = accountIdentifier.trim(),
            endpointUrl = endpointUrl.trim()
        )
        sessionPasswords[account.id] = password
        _availableMailboxes.value = _availableMailboxes.value + account
        return Pair(account, verification)
    }

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
        database.mailDao().moveToTrash(messageId)

    suspend fun sendOrDraftMail(
        mailboxId: String,
        subject: String,
        recipients: String,
        bodyText: String,
        isDraft: Boolean
    ): EwsSendResult {
        val mailbox = _availableMailboxes.value.firstOrNull { it.id == mailboxId }
            ?: error("Mailbox is not configured.")
        require(subject.isNotBlank()) { "Subject is required." }

        val recipientList = recipients
            .split(',', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
        require(recipientList.isNotEmpty()) { "At least one recipient is required." }

        val result = ewsClient.sendMessage(
            endpointUrl = mailbox.endpointUrl,
            username = mailbox.username,
            password = sessionPasswords[mailbox.id]
                ?: error("Mailbox credentials are no longer available; reconnect the account.")
            senderEmail = mailbox.emailAddress,
            recipients = recipientList,
            subject = subject.trim(),
            bodyHtml = "<p>" + escapeHtml(bodyText) + "</p>",
            isDraft = isDraft,
            mailboxId = mailboxId
        )
        if (!result.isSuccess || result.messageId.isNullOrBlank()) {
            return result
        }

        database.mailDao().insert(
            MailMessageEntity(
                id = result.messageId,
                mailboxId = mailboxId,
                folder = if (isDraft) "DRAFTS" else "SENT",
                threadId = "th_" + result.messageId,
                subject = subject.trim(),
                senderName = mailbox.displayName,
                senderEmail = mailbox.emailAddress,
                recipients = recipientList.joinToString(", "),
                receivedTimestamp = System.currentTimeMillis(),
                bodyText = bodyText,
                bodyHtml = "<p>" + escapeHtml(bodyText) + "</p>",
                isRead = true,
                isFlagged = false,
                hasAttachments = false,
                category = if (isDraft) "Draft" else "Sent"
            )
        )
        return result
    }

    suspend fun syncFolderMessages(mailboxId: String, folder: String = "INBOX"): EwsReceiveResult {
        val mailbox = _availableMailboxes.value.firstOrNull { it.id == mailboxId }
            ?: error("Mailbox is not configured.")
        val result = ewsClient.fetchMessages(
            endpointUrl = mailbox.endpointUrl,
            username = mailbox.username,
            password = mailbox.password,
            distinguishedFolderId = folder,
            mailboxId = mailboxId
        )
        if (result.isSuccess && result.messages.isNotEmpty()) {
            database.mailDao().insertAll(result.messages)
        }
        return result
    }

    fun getCalendarEvents(mailboxId: String): Flow<List<CalendarEventEntity>> =
        database.calendarDao().getEvents(mailboxId)

    suspend fun addCalendarEvent(event: CalendarEventEntity) {
        database.calendarDao().insert(event)
    }

    suspend fun updateRsvp(eventId: String, status: String) =
        database.calendarDao().updateRsvp(eventId, status)

    suspend fun deleteCalendarEvent(eventId: String) =
        database.calendarDao().delete(eventId)

    fun getTasks(mailboxId: String): Flow<List<TaskEntity>> =
        database.taskDao().getTasks(mailboxId)

    suspend fun addTask(task: TaskEntity) {
        database.taskDao().insert(task)
    }

    suspend fun toggleTaskComplete(taskId: String, isCompleted: Boolean) {
        database.taskDao().setCompleteState(
            taskId,
            isCompleted,
            if (isCompleted) System.currentTimeMillis() else null
        )
    }

    suspend fun deleteTask(taskId: String) = database.taskDao().delete(taskId)

    fun getContacts(mailboxId: String): Flow<List<ContactEntity>> =
        database.contactDao().getContacts(mailboxId)

    suspend fun addContact(contact: ContactEntity) = database.contactDao().insert(contact)
    suspend fun deleteContact(contactId: String) = database.contactDao().delete(contactId)

    fun getNotes(mailboxId: String): Flow<List<NoteEntity>> =
        database.noteDao().getNotes(mailboxId)

    suspend fun saveNote(note: NoteEntity) = database.noteDao().insert(note)
    suspend fun deleteNote(noteId: String) = database.noteDao().delete(noteId)

    fun searchMail(query: String) = database.mailDao().searchMessages(query)
    fun searchCalendar(query: String) = database.calendarDao().searchEvents(query)
    fun searchTasks(query: String) = database.taskDao().searchTasks(query)
    fun searchContacts(query: String) = database.contactDao().searchContacts(query)
    fun searchNotes(query: String) = database.noteDao().searchNotes(query)

    fun getSavedVerification(): Flow<EndpointVerificationEntity?> =
        database.verificationDao().getVerification()

    fun runProtocolVerificationFlow(host: String, ewsUrl: String): Flow<ProtocolVerificationSummary> =
        protocolVerifier.executeVerificationFlow(host, ewsUrl)

    suspend fun saveVerificationResult(summary: ProtocolVerificationSummary) {
        database.verificationDao().saveVerification(
            EndpointVerificationEntity(
                id = 1,
                host = summary.targetHost,
                ewsUrl = summary.ewsEndpoint,
                gate1Passed = summary.gate1.status.name == "PASSED",
                gate2Passed = summary.gate2.status.name == "PASSED",
                gate3Passed = summary.gate3.status.name == "PASSED",
                verifiedMechanism = summary.verifiedMechanism?.displayName
                    ?: summary.detectedMechanisms.joinToString { it.displayName },
                challengeHeaders = summary.rawChallengeHeaders.joinToString("\n"),
                lastVerifiedTimestamp = summary.lastVerifiedTimestamp ?: System.currentTimeMillis(),
                latencyMs = summary.gate1.latencyMs + summary.gate2.latencyMs + summary.gate3.latencyMs
            )
        )
    }

    private fun escapeHtml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
