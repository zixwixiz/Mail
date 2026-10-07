package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.MailboxCredentialStore
import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.NoteEntity
import com.example.data.local.entities.TaskEntity
import com.example.data.model.EwsAuthMechanism
import com.example.data.model.MailboxAccount
import com.example.data.model.MailboxPermission
import com.example.data.model.ProtocolVerificationSummary
import com.example.data.repository.JluRepository
import com.example.domain.SmartActionSuggestion
import com.example.domain.SmartExtractor
import com.example.domain.TargetLanguage
import com.example.domain.TimezoneEngine
import com.example.domain.TranslationResult
import com.example.domain.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.UUID

enum class NavigationTab(val label: String) {
    MAIL("Mail"),
    CALENDAR("Calendar"),
    TASKS("Tasks"),
    CONTACTS("Contacts"),
    NOTES("Notes"),
    DIAGNOSTICS("Diagnostics")
}

data class UiFeedback(
    val message: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class UniversalSearchResults(
    val query: String = "",
    val mail: List<MailMessageEntity> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList()
) {
    val totalCount: Int
        get() = mail.size + events.size + tasks.size + contacts.size + notes.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = JluRepository(database, credentialStore = MailboxCredentialStore(application.applicationContext))

    val availableMailboxes: StateFlow<List<MailboxAccount>> = repository.availableMailboxes

    // Add Account Sheet & Verification State
    private val _isAddAccountOpen = MutableStateFlow(false)
    val isAddAccountOpen: StateFlow<Boolean> = _isAddAccountOpen.asStateFlow()

    private val _isAccountVerifying = MutableStateFlow(false)
    val isAccountVerifying: StateFlow<Boolean> = _isAccountVerifying.asStateFlow()

    private val _accountVerificationResult = MutableStateFlow<com.example.data.network.AuthVerificationResult?>(null)
    val accountVerificationResult: StateFlow<com.example.data.network.AuthVerificationResult?> = _accountVerificationResult.asStateFlow()

    fun setAddAccountOpen(open: Boolean) {
        _isAddAccountOpen.value = open
        if (!open) {
            _accountVerificationResult.value = null
        }
    }

    fun addJluAccount(
        accountIdentifier: String,
        displayName: String,
        emailAddress: String,
        isSharedMailbox: Boolean,
        permission: MailboxPermission,
        department: String,
        endpointUrl: String = com.example.data.network.AuthVerificationService.DEFAULT_JLU_EWS_ENDPOINT,
        password: String = ""
    ) {
        viewModelScope.launch {
            _isAccountVerifying.value = true
            try {
                val (newAccount, result) = repository.verifyAndAddAccount(
                    accountIdentifier = accountIdentifier,
                    displayName = displayName,
                    emailAddress = emailAddress,
                    isSharedMailbox = isSharedMailbox,
                    permission = permission,
                    department = department,
                    endpointUrl = endpointUrl,
                    password = password
                )
                if (!result.isSuccess) {
                    _accountVerificationResult.value = result
                    _isAccountVerifying.value = false
                    showFeedback(result.errorMessage ?: "Exchange login failed.", isError = true)
                    return@launch
                }
                _accountVerificationResult.value = result
                _activeMailbox.value = newAccount
                _isAccountVerifying.value = false
                _isAddAccountOpen.value = false
                showFeedback("EWS endpoint verified (" + result.selectedMechanism.displayName + "); account added.")
                syncMailboxData(newAccount.id)
            } catch (e: Throwable) {
                _isAccountVerifying.value = false
                val message = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                _accountVerificationResult.value = com.example.data.network.AuthVerificationResult(
                    isSuccess = false,
                    endpointUrl = endpointUrl,
                    httpStatusCode = 0,
                    rawWwwAuthenticateHeaders = emptyList(),
                    supportedMechanisms = listOf(com.example.data.model.EwsAuthMechanism.NTLM),
                    selectedMechanism = com.example.data.model.EwsAuthMechanism.NTLM,
                    latencyMs = 0L,
                    diagnosticLogs = listOf("Exchange login error: $message"),
                    errorMessage = "Exchange login failed: $message"
                )
                showFeedback("Exchange login failed: $message", isError = true)
            }
        }
    }

    // Live EWS Sync State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun syncCurrentFolder() {
        val mailbox = _activeMailbox.value
        if (!mailbox.isConfigured) {
            showFeedback("Add and verify a mailbox before syncing.", isError = true)
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.syncFolderMessages(_activeMailbox.value.id, _selectedFolder.value)
            _isSyncing.value = false
            if (result.isSuccess) {
                runCatching { repository.syncCalendar(_activeMailbox.value.id) }
                runCatching { repository.syncContacts(_activeMailbox.value.id) }
                showFeedback("Exchange mail refreshed: " + result.messages.size + " message(s). Calendar and contacts refreshed.")
            } else {
                showFeedback("EWS FindItem: " + result.responseCode, isError = true)
            }
        }
    }

    // Active Mailbox State
    private val _activeMailbox = MutableStateFlow(MailboxAccount.UNCONFIGURED)
    val activeMailbox: StateFlow<MailboxAccount> = _activeMailbox.asStateFlow()

    init {
        repository.availableMailboxes.value.firstOrNull()?.let {
            _activeMailbox.value = it
            syncMailboxData(it.id)
        }
    }

    private fun syncMailboxData(mailboxId: String) {
        viewModelScope.launch {
            runCatching { repository.syncCalendar(mailboxId) }
            runCatching { repository.syncContacts(mailboxId) }
        }
    }


    // Navigation Tab
    private val _currentTab = MutableStateFlow(NavigationTab.MAIL)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    // Mail Folder Selection
    private val _selectedFolder = MutableStateFlow("INBOX")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    // Messages for current mailbox and folder
    val currentMessages: StateFlow<List<MailMessageEntity>> = combine(
        _activeMailbox,
        _selectedFolder
    ) { mailbox, folder ->
        Pair(mailbox.id, folder)
    }.flatMapLatest { (mailboxId, folder) ->
        repository.getMessages(mailboxId, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Events for current mailbox
    val currentEvents: StateFlow<List<CalendarEventEntity>> = _activeMailbox
        .flatMapLatest { repository.getCalendarEvents(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tasks for current mailbox
    val currentTasks: StateFlow<List<TaskEntity>> = _activeMailbox
        .flatMapLatest { repository.getTasks(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Contacts for current mailbox + JLU Directory
    val currentContacts: StateFlow<List<ContactEntity>> = _activeMailbox
        .flatMapLatest { repository.getContacts(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes for current mailbox
    val currentNotes: StateFlow<List<NoteEntity>> = _activeMailbox
        .flatMapLatest { repository.getNotes(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unread count
    val unreadCount: StateFlow<Int> = _activeMailbox
        .flatMapLatest { repository.getUnreadCount(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Saved verification from DB
    val savedVerification: StateFlow<EndpointVerificationEntity?> = _activeMailbox
        .flatMapLatest { repository.getSavedVerification(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Active Verification Summary State
    private val _verificationSummary = MutableStateFlow(
        ProtocolVerificationSummary(
            targetHost = com.example.data.network.EwsEndpointPolicy.DEFAULT_HOST,
            ewsEndpoint = com.example.data.network.EwsEndpointPolicy.DEFAULT_ENDPOINT,
        )
    )
    val verificationSummary: StateFlow<ProtocolVerificationSummary> = _verificationSummary.asStateFlow()

    // User Local Timezone selection for dual time comparison
    private val _userLocalZoneId = MutableStateFlow(TimeZone.getDefault().id)
    val userLocalZoneId: StateFlow<String> = _userLocalZoneId.asStateFlow()

    // Currently Selected Email Detail
    private val _selectedEmail = MutableStateFlow<MailMessageEntity?>(null)
    val selectedEmail: StateFlow<MailMessageEntity?> = _selectedEmail.asStateFlow()

    // Active translation for viewed email
    private val _currentTranslation = MutableStateFlow<TranslationResult?>(null)
    val currentTranslation: StateFlow<TranslationResult?> = _currentTranslation.asStateFlow()

    // Selected target language for translation
    private val _targetLanguage = MutableStateFlow(TranslationService.SUPPORTED_LANGUAGES[0])
    val targetLanguage: StateFlow<TargetLanguage> = _targetLanguage.asStateFlow()

    // Smart suggestions for viewed email
    private val _smartSuggestions = MutableStateFlow<List<SmartActionSuggestion>>(emptyList())
    val smartSuggestions: StateFlow<List<SmartActionSuggestion>> = _smartSuggestions.asStateFlow()

    // UI Feedback Banner / Snackbar
    private val _uiFeedback = MutableStateFlow<UiFeedback?>(null)
    val uiFeedback: StateFlow<UiFeedback?> = _uiFeedback.asStateFlow()

    // Pending Sync count
    // Universal Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow(UniversalSearchResults())
    val searchResults: StateFlow<UniversalSearchResults> = _searchResults.asStateFlow()
    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    // Modals & Dialogs
    private val _isComposeOpen = MutableStateFlow(false)
    val isComposeOpen: StateFlow<Boolean> = _isComposeOpen.asStateFlow()

    private val _isCreateEventOpen = MutableStateFlow(false)
    val isCreateEventOpen: StateFlow<Boolean> = _isCreateEventOpen.asStateFlow()

    private val _isCreateTaskOpen = MutableStateFlow(false)
    val isCreateTaskOpen: StateFlow<Boolean> = _isCreateTaskOpen.asStateFlow()

    private val _isCreateNoteOpen = MutableStateFlow(false)
    val isCreateNoteOpen: StateFlow<Boolean> = _isCreateNoteOpen.asStateFlow()

    fun selectMailbox(mailbox: MailboxAccount) {
        _activeMailbox.value = mailbox
        _selectedEmail.value = null
        showFeedback("Switched mailbox: " + mailbox.displayName)
    }

    fun disconnectActiveMailbox() {
        val mailboxId = _activeMailbox.value.id
        if (mailboxId.isBlank()) {
            showFeedback("No mailbox is connected.", isError = true)
            return
        }
        viewModelScope.launch { repository.disconnectMailbox(mailboxId) }
        _activeMailbox.value = MailboxAccount.UNCONFIGURED
        _selectedFolder.value = "INBOX"
        _selectedEmail.value = null
        _smartSuggestions.value = emptyList()
        _currentTranslation.value = null
        showFeedback("Mailbox disconnected and session credentials cleared.")
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun selectEmail(email: MailMessageEntity?) {
        _selectedEmail.value = email
        _currentTranslation.value = null
        if (email != null) {
            // Mark as read
            viewModelScope.launch {
                repository.setReadState(email.id, true)
            }
            // Extract smart suggestions
            _smartSuggestions.value = SmartExtractor.extractSuggestions(email.subject, email.bodyText, email.id)
        } else {
            _smartSuggestions.value = emptyList()
        }
    }

    fun toggleEmailFlag(emailId: String, currentFlag: Boolean) {
        viewModelScope.launch {
            repository.toggleFlag(emailId, !currentFlag)
        }
    }

    fun deleteEmail(emailId: String) {
        viewModelScope.launch {
            repository.deleteMessage(emailId)
            if (_selectedEmail.value?.id == emailId) {
                _selectedEmail.value = null
            }
            showFeedback("Message moved to local Trash; the server copy is unchanged.")
        }
    }

    fun translateCurrentEmail(target: TargetLanguage) {
        _targetLanguage.value = target
        val email = _selectedEmail.value ?: return
        val result = TranslationService.translateGermanAcademicText(email.bodyText, target)
        _currentTranslation.value = result
        showFeedback("Translated to ${target.displayName}")
    }

    fun clearTranslation() {
        _currentTranslation.value = null
    }

    // Smart Actions: Email to Calendar / Email to Task
    fun applyCalendarSuggestion(suggestion: SmartActionSuggestion.CalendarSuggestion) {
        if (!_activeMailbox.value.isConfigured) {
            showFeedback("Connect a mailbox before creating account-scoped data.", isError = true)
            return
        }

        viewModelScope.launch {
            val event = CalendarEventEntity(
                id = "evt_${UUID.randomUUID()}",
                mailboxId = _activeMailbox.value.id,
                title = suggestion.title,
                description = suggestion.description,
                startInstant = suggestion.startInstant,
                endInstant = suggestion.endInstant,
                timeZone = TimezoneEngine.JLU_CAMPUS_ZONE,
                location = suggestion.location,
                attendees = _activeMailbox.value.emailAddress,
                rsvpStatus = "ACCEPTED"
            )
            repository.addCalendarEvent(event)
            showFeedback("Event '${suggestion.title}' added to Calendar!")
        }
    }

    fun applyTaskSuggestion(suggestion: SmartActionSuggestion.TaskSuggestion) {
        if (!_activeMailbox.value.isConfigured) {
            showFeedback("Connect a mailbox before creating account-scoped data.", isError = true)
            return
        }

        viewModelScope.launch {
            val task = TaskEntity(
                id = "task_${UUID.randomUUID()}",
                mailboxId = _activeMailbox.value.id,
                title = suggestion.title,
                description = suggestion.description,
                dueInstant = suggestion.dueInstant,
                timeZone = TimezoneEngine.JLU_CAMPUS_ZONE,
                priority = suggestion.priority,
                isCompleted = false,
                source = "EMAIL_EXTRACTED",
                sourceEmailId = _selectedEmail.value?.id
            )
            repository.addTask(task)
            showFeedback("Task '${suggestion.title}' created from email!")
        }
    }

    // Protocol Verification Execution
    fun startProtocolVerification(host: String = com.example.data.network.EwsEndpointPolicy.DEFAULT_HOST, ewsUrl: String = com.example.data.network.EwsEndpointPolicy.DEFAULT_ENDPOINT) {
        viewModelScope.launch {
            repository.runProtocolVerificationFlow(host, ewsUrl, _activeMailbox.value.id).collect { summary ->
                _verificationSummary.value = summary
                if (!summary.isVerifying) {
                    if (summary.allGatesPassed) {
                        repository.saveVerificationResult(summary, _activeMailbox.value.id)
                        val mechanism = summary.verifiedMechanism?.displayName ?: summary.detectedMechanisms.joinToString { it.displayName }.ifBlank { "detected mechanisms" }
                        showFeedback("Gate 1, 2, and 3 Verified! Identified Mechanism: $mechanism")
                    } else if (summary.failureReason != null) {
                        showFeedback("Verification Failed: ${summary.failureReason}", isError = true)
                    }
                }
            }
        }
    }

    fun setUserLocalTimezone(zoneId: String) {
        _userLocalZoneId.value = zoneId
        showFeedback("Comparison timezone set to: $zoneId")
    }

    // Send or draft email
    fun sendEmail(subject: String, recipients: String, body: String, isDraft: Boolean = false) {
        val mailbox = _activeMailbox.value
        if (!mailbox.isConfigured) {
            showFeedback("Add and verify a mailbox before composing email.", isError = true)
            return
        }
        if (mailbox.permission == MailboxPermission.REVIEWER) {
            showFeedback("Permission Denied: This shared mailbox is Read-Only (Reviewer).", isError = true)
            return
        }
        viewModelScope.launch {
            val result = repository.sendOrDraftMail(_activeMailbox.value.id, subject, recipients, body, isDraft)
            _isComposeOpen.value = false
            if (result.isSuccess) {
                showFeedback(if (isDraft) "Draft saved" else "EWS CreateItem sent (${result.responseCode})")
            } else {
                showFeedback("EWS CreateItem: ${result.responseCode}", isError = true)
            }
        }
    }

    // Calendar
    fun createCalendarEvent(title: String, location: String, startEpochMs: Long, endEpochMs: Long) {
        if (!_activeMailbox.value.isConfigured) {
            showFeedback("Connect a mailbox before creating account-scoped data.", isError = true)
            return
        }

        viewModelScope.launch {
            val event = CalendarEventEntity(
                id = "evt_${UUID.randomUUID()}",
                mailboxId = _activeMailbox.value.id,
                title = title,
                description = "Created in JLU Mobile",
                startInstant = startEpochMs,
                endInstant = endEpochMs,
                timeZone = TimezoneEngine.JLU_CAMPUS_ZONE,
                location = location,
                attendees = _activeMailbox.value.emailAddress,
                rsvpStatus = "ACCEPTED"
            )
            repository.addCalendarEvent(event)
            _isCreateEventOpen.value = false
            showFeedback("Appointment '$title' scheduled")
        }
    }

    fun updateEventRsvp(eventId: String, status: String) {
        viewModelScope.launch {
            repository.updateRsvp(eventId, status)
            showFeedback("RSVP updated to $status")
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteCalendarEvent(eventId)
            showFeedback("Calendar event removed")
        }
    }

    // Tasks
    fun createTask(title: String, description: String, dueInstant: Long?, priority: String) {
        if (!_activeMailbox.value.isConfigured) {
            showFeedback("Connect a mailbox before creating account-scoped data.", isError = true)
            return
        }

        viewModelScope.launch {
            val task = TaskEntity(
                id = "task_${UUID.randomUUID()}",
                mailboxId = _activeMailbox.value.id,
                title = title,
                description = description,
                dueInstant = dueInstant,
                priority = priority,
                isCompleted = false,
                source = "MANUAL"
            )
            repository.addTask(task)
            _isCreateTaskOpen.value = false
            showFeedback("Task created")
        }
    }

    fun toggleTask(taskId: String, currentCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskComplete(taskId, !currentCompleted)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            showFeedback("Task deleted")
        }
    }

    // Notes
    fun createNote(title: String, content: String) {
        if (!_activeMailbox.value.isConfigured) {
            showFeedback("Connect a mailbox before creating account-scoped data.", isError = true)
            return
        }

        viewModelScope.launch {
            val note = NoteEntity(
                id = "note_${UUID.randomUUID()}",
                mailboxId = _activeMailbox.value.id,
                title = title,
                content = content,
                updatedTimestamp = System.currentTimeMillis()
            )
            repository.saveNote(note)
            _isCreateNoteOpen.value = false
            showFeedback("Note saved")
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            showFeedback("Note deleted")
        }
    }

    // Contacts
    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteContact(contactId)
            showFeedback("Contact removed")
        }
    }

    // Universal Search
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = UniversalSearchResults()
            return
        }
        val mailboxId = _activeMailbox.value.id
        if (mailboxId.isBlank()) {
            _searchResults.value = UniversalSearchResults(query = query)
            return
        }
        viewModelScope.launch {
            val mail = repository.searchMail(mailboxId, query).firstOrNull() ?: emptyList()
            val events = repository.searchCalendar(mailboxId, query).firstOrNull() ?: emptyList()
            val tasks = repository.searchTasks(mailboxId, query).firstOrNull() ?: emptyList()
            val contacts = repository.searchContacts(mailboxId, query).firstOrNull() ?: emptyList()
            val notes = repository.searchNotes(mailboxId, query).firstOrNull() ?: emptyList()
            if (_searchQuery.value != query || _activeMailbox.value.id != mailboxId) return@launch
            _searchResults.value = UniversalSearchResults(
                query = query,
                mail = mail,
                events = events,
                tasks = tasks,
                contacts = contacts,
                notes = notes
            )
        }
    }

    fun setSearchOpen(open: Boolean) {
        _isSearchOpen.value = open
        if (!open) {
            _searchQuery.value = ""
            _searchResults.value = UniversalSearchResults()
        }
    }

    fun setComposeOpen(open: Boolean) { _isComposeOpen.value = open }
    fun setCreateEventOpen(open: Boolean) { _isCreateEventOpen.value = open }
    fun setCreateTaskOpen(open: Boolean) { _isCreateTaskOpen.value = open }
    fun setCreateNoteOpen(open: Boolean) { _isCreateNoteOpen.value = open }

    private fun showFeedback(msg: String, isError: Boolean = false) {
        _uiFeedback.value = UiFeedback(msg, isError)
    }

    fun clearFeedback() {
        _uiFeedback.value = null
    }
}
