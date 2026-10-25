package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.MailBriefingBuilder
import com.example.domain.MailCategory
import com.example.domain.MailOrganization
import com.example.domain.MailPeriodNotesBuilder
import com.example.domain.MailExportItem
import com.example.domain.MailExportFormatter
import com.example.domain.MailTextFormatter
import com.example.domain.MailTranslationFingerprint
import com.example.domain.ReadingPriority
import com.example.domain.ReadingQueueClassifier
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.MailTranslationEntity
import com.example.data.model.MailboxAccount
import com.example.domain.TimezoneEngine
import com.example.ui.theme.JluGold
import com.example.ui.theme.JluNavy
import com.example.ui.theme.JluSuccess
import com.example.ui.theme.JluWarning

@Composable
fun MailScreen(
    activeMailbox: MailboxAccount,
    selectedFolder: String,
    messages: List<MailMessageEntity>,
    allMailboxMessages: List<MailMessageEntity> = messages,
    translations: List<MailTranslationEntity> = emptyList(),
    onSelectFolder: (String) -> Unit,
    onSelectEmail: (MailMessageEntity) -> Unit,
    onToggleFlag: (String, Boolean) -> Unit,
    onComposeClick: () -> Unit,
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    previousAppOpenedTimestamp: Long = 0L,
    onExportSelected: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val briefing = remember(allMailboxMessages) { MailBriefingBuilder.build(allMailboxMessages) }
    var notesLookbackDays by remember(activeMailbox.id) { mutableStateOf(3) }
    var showPeriodNotes by remember(activeMailbox.id) { mutableStateOf(false) }
    var showTodayActions by remember(activeMailbox.id) { mutableStateOf(false) }
    val periodNotes = remember(allMailboxMessages, notesLookbackDays) {
        MailPeriodNotesBuilder.build(allMailboxMessages, notesLookbackDays)
    }
    val translationsById = remember(translations) { translations.associateBy { it.messageId } }
    var categoryFilter by remember(selectedFolder) { mutableStateOf<MailCategory?>(null) }
    var dateFilterDays by remember(selectedFolder) { mutableStateOf<Int?>(null) }
    var senderFilter by remember(selectedFolder) { mutableStateOf("") }
    var selectedIds by remember(selectedFolder) { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember(selectedFolder) { mutableStateOf(false) }
    var includeAttachmentReferences by remember(selectedFolder) { mutableStateOf(true) }
    val visibleMessages = remember(messages, selectedFolder) {
        if (selectedFolder == "SNOOZED") {
            messages.filter { it.snoozedUntil != null && it.snoozedUntil > System.currentTimeMillis() }
        } else {
            messages.filter { it.snoozedUntil == null || it.snoozedUntil <= System.currentTimeMillis() }
        }
    }

    val folders = listOf(
        "INBOX" to "Inbox",
        "SENT" to "Sent",
        "DRAFTS" to "Drafts",
        "TRASH" to "Trash",
        "ARCHIVE" to "Archive",
        "SNOOZED" to "Snoozed",
        "ATTACHMENTS" to "Attachments"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (activeMailbox.permission.canWrite) {
                FloatingActionButton(
                    onClick = onComposeClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("compose_fab")
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Compose Email")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (previousAppOpenedTimestamp > 0L) {
                Text(
                    text = "Previous app session: " + TimezoneEngine.formatRelativeTime(previousAppOpenedTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                )
            }

            // Folder Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders) { (folderKey, folderLabel) ->
                    val isSelected = selectedFolder == folderKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFolder(folderKey) },
                        label = {
                            Text(
                                when (folderKey) {
                                    "ATTACHMENTS" -> "$folderLabel (${allMailboxMessages.count { it.hasAttachments }})"
                                    "SNOOZED" -> "$folderLabel (${allMailboxMessages.count { it.snoozedUntil != null && it.snoozedUntil > System.currentTimeMillis() }})"
                                    else -> folderLabel
                                }
                            )
                        },
                        leadingIcon = {
                            val icon = when (folderKey) {
                                "INBOX" -> Icons.Default.Inbox
                                "SENT" -> Icons.Default.Send
                                "DRAFTS" -> Icons.Default.Drafts
                                "TRASH" -> Icons.Default.Archive
                                "ATTACHMENTS" -> Icons.Default.Attachment
                                "SNOOZED" -> Icons.Default.Schedule
                                else -> Icons.Default.Archive
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (!activeMailbox.permission.canWrite) {
                Surface(
                    color = JluWarning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shared Mailbox Read-Only Mode (Reviewer permission)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = JluWarning
                        )
                    }
                }
            }

            // EWS Protocol & Live Sync Status Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = JluSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Protocol: exchange_ews • /EWS/Exchange.asmx",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = onSyncClick,
                            enabled = activeMailbox.isConfigured,
                            modifier = Modifier.size(24.dp).testTag("sync_ews_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync EWS Folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inbox overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Today: ${briefing.todayCount} · ${briefing.todayUnreadCount} unread · ${briefing.todayActionCount} possible actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "This week: ${briefing.weekCount} · ${briefing.weekUnreadCount} unread · ${briefing.weekActionCount} possible actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showTodayActions = !showTodayActions },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (showTodayActions) "Hide" else "Today's actions")
                        }
                    }
                    if (showTodayActions) {
                        if (briefing.topActionMessages.isEmpty()) {
                            Text("No likely action mail detected today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            briefing.topActionMessages.forEach { actionMessage ->
                                TextButton(
                                    onClick = { onSelectEmail(actionMessage) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "${actionMessage.subject.ifBlank { "(no subject)" }} · ${actionMessage.senderName}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .then(if (showPeriodNotes) Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mail notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Last ${notesLookbackDays} days · ${periodNotes.messageCount} messages · ${periodNotes.possibleActionCount} possible actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showPeriodNotes = !showPeriodNotes }) {
                            Text(if (showPeriodNotes) "Hide notes" else "Generate notes")
                        }
                    }

                    if (showPeriodNotes) {
                        Text(
                            "On-device extracted notes from cached messages, not generative AI. Dates and actions are clues; verify them in the original email.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(1, 2, 3, 5, 10, 30)) { days ->
                                FilterChip(
                                    selected = notesLookbackDays == days,
                                    onClick = { notesLookbackDays = days },
                                    label = { Text("Last $days day" + if (days == 1) "" else "s") }
                                )
                            }
                        }
                        Text(
                            "${periodNotes.unreadCount} unread · ${periodNotes.possibleActionCount} possible actions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (periodNotes.categoryCounts.isNotEmpty()) {
                            Text(
                                "Categories: " + periodNotes.categoryCounts.take(4).joinToString(" · ") { "${it.label} (${it.count})" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (periodNotes.items.isEmpty()) {
                            Text("No received messages found in this period.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            periodNotes.items.forEach { note ->
                                val priorityLabel = when (note.priority) {
                                    ReadingPriority.ACTION_REQUIRED -> "POSSIBLE ACTION"
                                    ReadingPriority.IMPORTANT -> "IMPORTANT"
                                    ReadingPriority.ROUTINE -> "ROUTINE"
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            allMailboxMessages.firstOrNull { it.id == note.sourceId }?.let(onSelectEmail)
                                        }
                                        .padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        priorityLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(note.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${note.sender} · " + TimezoneEngine.formatRelativeTime(note.receivedTimestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(note.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                                    note.keyPoints.forEach { point ->
                                        Text("• $point", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                    }
                                }
                            }
                            if (periodNotes.omittedItemCount > 0) {
                                Text(
                                    "${periodNotes.items.size} detailed notes shown; ${periodNotes.omittedItemCount} additional messages are included in the counts.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Message List or Empty State
            if (visibleMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                !activeMailbox.isConfigured -> "Connect a mailbox to view messages"
                                selectedFolder == "SNOOZED" -> "No snoozed messages"
                                messages.isNotEmpty() -> "All messages in this folder are snoozed"
                                else -> "No messages in $selectedFolder"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                !activeMailbox.isConfigured -> "Use the Add / Connect button above to sign in"
                                selectedFolder == "SNOOZED" -> "Snoozed messages return to their folders when due"
                                messages.isNotEmpty() -> "Snoozed messages return automatically when due"
                                else -> "Mailbox: ${activeMailbox.displayName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                var queueFilter by remember(selectedFolder) { mutableStateOf(ReadingQueueFilter.ALL) }
                val filteredMessages = visibleMessages.filter { message ->
                    val priority = ReadingQueueClassifier.classify(message).priority
                    val category = MailOrganization.categorize(message.subject, message.senderName + " " + message.senderEmail, message.bodyText)
                    val matchesQueue = when (queueFilter) {
                        ReadingQueueFilter.ALL -> true
                        ReadingQueueFilter.ACTION -> priority == ReadingPriority.ACTION_REQUIRED
                        ReadingQueueFilter.IMPORTANT -> priority == ReadingPriority.IMPORTANT
                        ReadingQueueFilter.ROUTINE -> priority == ReadingPriority.ROUTINE
                    }
                    val matchesCategory = categoryFilter == null || category == categoryFilter
                    val matchesDate = dateFilterDays == null || MailOrganization.receivedWithinDays(message.receivedTimestamp, dateFilterDays!!)
                    val matchesSender = senderFilter.isBlank() || message.senderName.contains(senderFilter, ignoreCase = true) || message.senderEmail.contains(senderFilter, ignoreCase = true)
                    matchesQueue && matchesCategory && matchesDate && matchesSender
                }

                fun exportSelected(format: String) {
                    val selected = filteredMessages.filter { it.id in selectedIds }
                    if (selected.isEmpty()) return
                    val exportItems = selected.map { message ->
                        val attachmentNames = message.attachments.lineSequence().filter { it.isNotBlank() }.toList()
                        MailExportItem(
                            id = message.id,
                            subject = MailTextFormatter.plainText(message.subject),
                            senderName = message.senderName,
                            senderEmail = message.senderEmail,
                            receivedTimestamp = message.receivedTimestamp,
                            body = MailTextFormatter.plainText(message.bodyText.ifBlank { message.bodyHtml }),
                            attachments = if (includeAttachmentReferences) attachmentNames else emptyList(),
                            mediaFileNames = if (includeAttachmentReferences) attachmentNames else emptyList()
                        )
                    }
                    val extension = when (format) { "csv" -> "csv"; "text" -> "txt"; else -> "md" }
                    val mimeType = when (format) { "csv" -> "text/csv"; "text" -> "text/plain"; else -> "text/markdown" }
                    onExportSelected(MailExportFormatter.format(exportItems, format, includeAttachmentReferences), "jlu-mail-export." + extension, mimeType)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "mail_management_filters") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Filter and export mail", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = senderFilter, onValueChange = { senderFilter = it }, label = { Text("Filter by sender name or email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("Category", style = MaterialTheme.typography.labelMedium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item { FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All") }) }
                                    items(MailCategory.values().toList()) { category ->
                                        FilterChip(selected = categoryFilter == category, onClick = { categoryFilter = if (categoryFilter == category) null else category }, label = { Text(category.label) })
                                    }
                                }
                                Text("Received", style = MaterialTheme.typography.labelMedium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val dateChoices = listOf<Int?>(null, 0, 2, 3, 5, 10)
                                    items(dateChoices) { days ->
                                        val label = when (days) { null -> "Any date"; 0 -> "Today"; else -> days.toString() + " days" }
                                        FilterChip(selected = dateFilterDays == days, onClick = { dateFilterDays = days }, label = { Text(label) })
                                    }
                                }
                                if (!selectionMode) {
                                    TextButton(onClick = { selectionMode = true }) { Text("Select emails to export") }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = filteredMessages.isNotEmpty() && filteredMessages.all { it.id in selectedIds },
                                            onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + filteredMessages.map { it.id } else selectedIds - filteredMessages.map { it.id }.toSet() }
                                        )
                                        Text("Select all filtered (" + filteredMessages.count { it.id in selectedIds } + ")", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = includeAttachmentReferences, onCheckedChange = { includeAttachmentReferences = it })
                                        Text("Include attachment filenames (media files themselves are not cached)", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (selectedIds.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(onClick = { exportSelected("markdown") }) { Text("Export .md") }
                                            TextButton(onClick = { exportSelected("csv") }) { Text("Export CSV") }
                                            TextButton(onClick = { exportSelected("text") }) { Text("Export .txt") }
                                        }
                                    }
                                    TextButton(onClick = { selectedIds = emptySet(); selectionMode = false }) { Text("Cancel selection") }
                                }
                            }
                        }
                    }

                    item(key = "reading_queue") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Smart reading queue",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Rules-based triage for deadlines, requests, and flagged mail. Check the original message before acting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(ReadingQueueFilter.values().toList()) { filter ->
                                        val count = when (filter) {
                                            ReadingQueueFilter.ALL -> visibleMessages.size
                                            ReadingQueueFilter.ACTION -> visibleMessages.count {
                                                ReadingQueueClassifier.classify(it).priority == ReadingPriority.ACTION_REQUIRED
                                            }
                                            ReadingQueueFilter.IMPORTANT -> visibleMessages.count {
                                                ReadingQueueClassifier.classify(it).priority == ReadingPriority.IMPORTANT
                                            }
                                            ReadingQueueFilter.ROUTINE -> visibleMessages.count {
                                                ReadingQueueClassifier.classify(it).priority == ReadingPriority.ROUTINE
                                            }
                                        }
                                        FilterChip(
                                            selected = queueFilter == filter,
                                            onClick = { queueFilter = filter },
                                            label = { Text("${filter.label} ($count)") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredMessages.isEmpty()) {
                        item(key = "empty_queue_filter") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No messages match this reading queue.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredMessages, key = { it.id }) { email ->
                            val cachedTranslation = translationsById[email.id]?.takeIf {
                                it.targetLanguage == "en" &&
                                    it.originalFingerprint == MailTranslationFingerprint.forMessage(email)
                            }
                            EmailItemCard(
                                email = email,
                                translation = cachedTranslation,
                                onClick = { onSelectEmail(email) },
                                onToggleFlag = { onToggleFlag(email.id, email.isFlagged) },
                                selectionMode = selectionMode,
                                isSelected = email.id in selectedIds,
                                onToggleSelected = { selectionMode = true; selectedIds = if (email.id in selectedIds) selectedIds - email.id else selectedIds + email.id }
                            )
                        }
                    }
                }
            }
        }
    }
}


private enum class ReadingQueueFilter(val label: String) {
    ALL("All"),
    ACTION("Action needed"),
    IMPORTANT("Important"),
    ROUTINE("Routine")
}

@Composable
fun EmailItemCard(
    email: MailMessageEntity,
    translation: MailTranslationEntity? = null,
    onClick: () -> Unit,
    onToggleFlag: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = { if (selectionMode) onToggleSelected() else onClick() },
        modifier = modifier
            .fillMaxWidth()
            .testTag("email_item_${email.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (!email.isRead) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (selectionMode) Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
                    if (!email.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = email.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (email.hasAttachments) {
                        Icon(
                            imageVector = Icons.Default.Attachment,
                            contentDescription = "Attachment",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = TimezoneEngine.formatRelativeTime(email.receivedTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = MailTextFormatter.plainText(translation?.translatedSubject?.takeIf { it.isNotBlank() } ?: email.subject),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!email.isRead) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = MailTextFormatter.plainText(translation?.translatedPreview?.takeIf { it.isNotBlank() } ?: email.bodyText.ifBlank { email.bodyHtml }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (translation?.isPartial == true) {
                Text(
                    text = "Translation may be incomplete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (email.lastOpenedTimestamp > 0L) {
                Text(
                    text = "Last opened: " + TimezoneEngine.formatRelativeTime(email.lastOpenedTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = MailOrganization.categorize(email.subject, email.senderName + " " + email.senderEmail, email.bodyText).label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (email.attachments.isNotBlank()) {
                Text(
                    text = "Attachments: " + email.attachments.lineSequence().take(2).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (email.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = email.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                IconButton(
                    onClick = onToggleFlag,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (email.isFlagged) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Flag",
                        tint = if (email.isFlagged) JluGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

