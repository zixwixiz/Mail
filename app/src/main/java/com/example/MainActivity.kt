package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.MailboxSelectorBar
import com.example.ui.screens.AddAccountDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ComposeEmailDialog
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.CreateEventDialog
import com.example.ui.screens.CreateNoteDialog
import com.example.ui.screens.CreateTaskDialog
import com.example.ui.screens.EmailDetailSheet
import com.example.ui.screens.MailScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.ProtocolVerificationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.UniversalSearchSheet
import com.example.ui.theme.JluGold
import com.example.ui.theme.JluNavy
import com.example.ui.theme.JluSuccess
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                JluMobileApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JluMobileApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val activeMailbox by viewModel.activeMailbox.collectAsStateWithLifecycle()
    val availableMailboxes by viewModel.availableMailboxes.collectAsStateWithLifecycle()
    val isAddAccountOpen by viewModel.isAddAccountOpen.collectAsStateWithLifecycle()
    val isAccountVerifying by viewModel.isAccountVerifying.collectAsStateWithLifecycle()
    val accountVerificationResult by viewModel.accountVerificationResult.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val events by viewModel.currentEvents.collectAsStateWithLifecycle()
    val tasks by viewModel.currentTasks.collectAsStateWithLifecycle()
    val contacts by viewModel.currentContacts.collectAsStateWithLifecycle()
    val notes by viewModel.currentNotes.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val verificationSummary by viewModel.verificationSummary.collectAsStateWithLifecycle()
    val savedVerification by viewModel.savedVerification.collectAsStateWithLifecycle()
    val userLocalZoneId by viewModel.userLocalZoneId.collectAsStateWithLifecycle()

    val selectedEmail by viewModel.selectedEmail.collectAsStateWithLifecycle()
    val currentTranslation by viewModel.currentTranslation.collectAsStateWithLifecycle()
    val smartSuggestions by viewModel.smartSuggestions.collectAsStateWithLifecycle()
    val uiFeedback by viewModel.uiFeedback.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isBiometricLockEnabled by viewModel.isBiometricLockEnabled.collectAsStateWithLifecycle()

    val isSearchOpen by viewModel.isSearchOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val isComposeOpen by viewModel.isComposeOpen.collectAsStateWithLifecycle()
    val isCreateEventOpen by viewModel.isCreateEventOpen.collectAsStateWithLifecycle()
    val isCreateTaskOpen by viewModel.isCreateTaskOpen.collectAsStateWithLifecycle()
    val isCreateNoteOpen by viewModel.isCreateNoteOpen.collectAsStateWithLifecycle()
    var isSettingsOpen by remember { mutableStateOf(false) }

    // Show feedback messages
    LaunchedEffect(uiFeedback) {
        uiFeedback?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JluNavy)
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "JLU Mobile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = JluGold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Status indicator for Gate Verification
                            val isVerified = verificationSummary.allGatesPassed || (savedVerification != null)
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isVerified) JluSuccess else JluGold)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = JluNavy,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.setSearchOpen(true) }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.selectTab(NavigationTab.DIAGNOSTICS) }) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = "Protocol Verification", tint = JluGold)
                        }
                        IconButton(onClick = { isSettingsOpen = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                )

                // Mailbox selector directly beneath top app bar
                MailboxSelectorBar(
                    activeMailbox = activeMailbox,
                    availableMailboxes = availableMailboxes,
                    onSelectMailbox = { viewModel.selectMailbox(it) },
                    onAddAccountClick = { viewModel.setAddAccountOpen(true) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Mail Tab
                NavigationBarItem(
                    selected = currentTab == NavigationTab.MAIL,
                    onClick = { viewModel.selectTab(NavigationTab.MAIL) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text("$unreadCount") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == NavigationTab.MAIL) Icons.Filled.Email else Icons.Outlined.Email,
                                contentDescription = "Mail"
                            )
                        }
                    },
                    label = { Text("Mail") },
                    modifier = Modifier.testTag("nav_tab_mail")
                )

                // Calendar Tab
                NavigationBarItem(
                    selected = currentTab == NavigationTab.CALENDAR,
                    onClick = { viewModel.selectTab(NavigationTab.CALENDAR) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == NavigationTab.CALENDAR) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = "Calendar"
                        )
                    },
                    label = { Text("Calendar") },
                    modifier = Modifier.testTag("nav_tab_calendar")
                )

                // Tasks Tab
                NavigationBarItem(
                    selected = currentTab == NavigationTab.TASKS,
                    onClick = { viewModel.selectTab(NavigationTab.TASKS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == NavigationTab.TASKS) Icons.Filled.AssignmentTurnedIn else Icons.Outlined.AssignmentTurnedIn,
                            contentDescription = "Tasks"
                        )
                    },
                    label = { Text("Tasks") },
                    modifier = Modifier.testTag("nav_tab_tasks")
                )

                // Contacts Tab
                NavigationBarItem(
                    selected = currentTab == NavigationTab.CONTACTS,
                    onClick = { viewModel.selectTab(NavigationTab.CONTACTS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == NavigationTab.CONTACTS) Icons.Filled.Contacts else Icons.Outlined.Contacts,
                            contentDescription = "Contacts"
                        )
                    },
                    label = { Text("Contacts") },
                    modifier = Modifier.testTag("nav_tab_contacts")
                )

                // Notes Tab
                NavigationBarItem(
                    selected = currentTab == NavigationTab.NOTES,
                    onClick = { viewModel.selectTab(NavigationTab.NOTES) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == NavigationTab.NOTES) Icons.Filled.Description else Icons.Outlined.Description,
                            contentDescription = "Notes"
                        )
                    },
                    label = { Text("Notes") },
                    modifier = Modifier.testTag("nav_tab_notes")
                )

                // Verification & Diagnostics Tab
                NavigationBarItem(
                    selected = currentTab == NavigationTab.DIAGNOSTICS,
                    onClick = { viewModel.selectTab(NavigationTab.DIAGNOSTICS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == NavigationTab.DIAGNOSTICS) Icons.Filled.Security else Icons.Outlined.Security,
                            contentDescription = "Diagnostics"
                        )
                    },
                    label = { Text("EWS Proof") },
                    modifier = Modifier.testTag("nav_tab_diagnostics")
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                NavigationTab.MAIL -> {
                    MailScreen(
                        activeMailbox = activeMailbox,
                        selectedFolder = selectedFolder,
                        messages = messages,
                        onSelectFolder = { viewModel.selectFolder(it) },
                        onSelectEmail = { viewModel.selectEmail(it) },
                        onToggleFlag = { id, flag -> viewModel.toggleEmailFlag(id, flag) },
                        onComposeClick = { viewModel.setComposeOpen(true) },
                        isSyncing = isSyncing,
                        onSyncClick = { viewModel.syncCurrentFolder() }
                    )
                }
                NavigationTab.CALENDAR -> {
                    CalendarScreen(
                        events = events,
                        userLocalZoneId = userLocalZoneId,
                        onSelectUserZone = { viewModel.setUserLocalTimezone(it) },
                        onUpdateRsvp = { id, status -> viewModel.updateEventRsvp(id, status) },
                        onDeleteEvent = { viewModel.deleteEvent(it) },
                        onAddEventClick = { viewModel.setCreateEventOpen(true) }
                    )
                }
                NavigationTab.TASKS -> {
                    TasksScreen(
                        tasks = tasks,
                        onToggleTask = { id: String, completed: Boolean -> viewModel.toggleTask(id, completed) },
                        onDeleteTask = { id: String -> viewModel.deleteTask(id) },
                        onAddTaskClick = { viewModel.setCreateTaskOpen(true) }
                    )
                }
                NavigationTab.CONTACTS -> {
                    ContactsScreen(contacts = contacts)
                }
                NavigationTab.NOTES -> {
                    NotesScreen(
                        notes = notes,
                        onDeleteNote = { viewModel.deleteNote(it) },
                        onAddNoteClick = { viewModel.setCreateNoteOpen(true) }
                    )
                }
                NavigationTab.DIAGNOSTICS -> {
                    ProtocolVerificationScreen(
                        summary = verificationSummary,
                        savedVerification = savedVerification,
                        onRunVerification = { host, ewsUrl ->
                            viewModel.startProtocolVerification(host, ewsUrl)
                        }
                    )
                }
            }
        }
    }

    // Modal Email Detail Viewer Sheet
    if (selectedEmail != null) {
        EmailDetailSheet(
            email = selectedEmail!!,
            onDismiss = { viewModel.selectEmail(null) },
            onDelete = { viewModel.deleteEmail(selectedEmail!!.id) },
            onToggleFlag = { viewModel.toggleEmailFlag(selectedEmail!!.id, selectedEmail!!.isFlagged) },
            onReply = {
                viewModel.selectEmail(null)
                viewModel.setComposeOpen(true)
            },
            onForward = {
                viewModel.selectEmail(null)
                viewModel.setComposeOpen(true)
            },
            onTranslate = { targetLang -> viewModel.translateCurrentEmail(targetLang) },
            currentTranslation = currentTranslation,
            onClearTranslation = { viewModel.clearTranslation() },
            smartSuggestions = smartSuggestions,
            onApplyCalendarSuggestion = { suggestion ->
                viewModel.applyCalendarSuggestion(suggestion)
            },
            onApplyTaskSuggestion = { suggestion ->
                viewModel.applyTaskSuggestion(suggestion)
            }
        )
    }

    // Compose Email Dialog
    if (isComposeOpen) {
        ComposeEmailDialog(
            activeMailbox = activeMailbox,
            onDismiss = { viewModel.setComposeOpen(false) },
            onSend = { subject, recipient, body, isDraft ->
                viewModel.sendEmail(subject, recipient, body, isDraft)
            }
        )
    }

    // Create Event Dialog
    if (isCreateEventOpen) {
        CreateEventDialog(
            onDismiss = { viewModel.setCreateEventOpen(false) },
            onCreate = { title, location, start, end ->
                viewModel.createCalendarEvent(title, location, start, end)
            }
        )
    }

    // Create Task Dialog
    if (isCreateTaskOpen) {
        CreateTaskDialog(
            onDismiss = { viewModel.setCreateTaskOpen(false) },
            onCreate = { title, desc, due, priority ->
                viewModel.createTask(title, desc, due, priority)
            }
        )
    }

    // Create Note Dialog
    if (isCreateNoteOpen) {
        CreateNoteDialog(
            onDismiss = { viewModel.setCreateNoteOpen(false) },
            onCreate = { title, content ->
                viewModel.createNote(title, content)
            }
        )
    }

    // Universal Search Sheet
    if (isSearchOpen) {
        UniversalSearchSheet(
            query = searchQuery,
            results = searchResults,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onSelectEmail = { viewModel.selectEmail(it) },
            onDismiss = { viewModel.setSearchOpen(false) }
        )
    }

    // Settings Sheet
    if (isSettingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsOpen = false }
        ) {
            SettingsScreen(
                activeMailbox = activeMailbox,
                isBiometricLockEnabled = isBiometricLockEnabled,
                onToggleBiometricLock = { viewModel.toggleBiometricLock(it) },
                pendingSyncCount = pendingSyncCount,
                onFlushSyncQueue = { viewModel.flushSyncQueue() },
                onExportDiagnostics = {
                    Toast.makeText(
                        context,
                        "Sanitized diagnostic report exported (RFC 7235 / EWS Exchange 2019 logs saved without credentials).",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onAddAccountClick = {
                    isSettingsOpen = false
                    viewModel.setAddAccountOpen(true)
                }
            )
        }
    }

    // Add Account Dialog
    if (isAddAccountOpen) {
        AddAccountDialog(
            isVerifying = isAccountVerifying,
            verificationResult = accountVerificationResult,
            onDismiss = { viewModel.setAddAccountOpen(false) },
            onAddAccount = { id, name, email, isShared, perm, dept, url, pass ->
                viewModel.addJluAccount(id, name, email, isShared, perm, dept, url, pass)
            }
        )
    }
}
