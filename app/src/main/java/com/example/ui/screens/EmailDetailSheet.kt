package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color as AndroidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MailMessageEntity
import com.example.domain.DetectedEmailChange
import com.example.domain.EmailSafetyAnalyzer
import com.example.domain.EmailSafetyLevel
import com.example.domain.FollowUpDetector
import com.example.domain.MailQuickSummaryBuilder
import com.example.domain.MailTextFormatter
import com.example.domain.SmartActionSuggestion
import com.example.domain.TargetLanguage
import com.example.domain.ThreadSummaryBuilder
import com.example.domain.TimezoneEngine
import com.example.domain.TranslationResult
import com.example.domain.TranslationService
import com.example.ui.theme.JluGold
import com.example.ui.theme.JluNavy
import com.example.ui.theme.JluSuccess
import com.example.ui.theme.JluWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailSheet(
    email: MailMessageEntity,
    threadMessages: List<MailMessageEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Long) -> Unit,
    onToggleFlag: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onTranslate: (TargetLanguage) -> Unit,
    currentTranslation: TranslationResult?,
    onClearTranslation: () -> Unit,
    smartSuggestions: List<SmartActionSuggestion>,
    detectedChanges: List<DetectedEmailChange>,
    onApplyCalendarSuggestion: (SmartActionSuggestion.CalendarSuggestion) -> Unit,
    onApplyTaskSuggestion: (SmartActionSuggestion.TaskSuggestion) -> Unit,
    onCreateFollowUpTask: (String) -> Unit,
    userLocalZoneId: String,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLangPicker by remember { mutableStateOf(false) }
    var showSnoozeMenu by remember(email.id) { mutableStateOf(false) }
    var showOriginalHtml by remember(email.id) { mutableStateOf(false) }
    val safetyAssessment = remember(email.id, email.subject, email.bodyText) {
        EmailSafetyAnalyzer.assess(email)
    }
    val quickSummary = remember(email.id, email.subject, email.bodyText, email.bodyHtml, email.attachments) {
        MailQuickSummaryBuilder.build(email)
    }
    val threadSummary = remember(threadMessages) { ThreadSummaryBuilder.build(threadMessages) }
    val followUpPromise = remember(email.id, email.bodyText, email.bodyHtml) {
        FollowUpDetector.detect(email)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("email_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFlag) {
                        Icon(
                            imageVector = if (email.isFlagged) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Flag",
                            tint = if (email.isFlagged) JluGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showSnoozeMenu = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = "Snooze")
                        }
                        DropdownMenu(
                            expanded = showSnoozeMenu,
                            onDismissRequest = { showSnoozeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("In 3 hours") },
                                onClick = {
                                    onSnooze(3L * 60 * 60 * 1000)
                                    showSnoozeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tomorrow (24 hours)") },
                                onClick = {
                                    onSnooze(24L * 60 * 60 * 1000)
                                    showSnoozeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Next week (7 days)") },
                                onClick = {
                                    onSnooze(7L * 24 * 60 * 60 * 1000)
                                    showSnoozeMenu = false
                                }
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subject
            Text(
                text = MailTextFormatter.plainText(email.subject),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sender Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(JluNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = email.senderName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = JluGold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = email.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = email.senderEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "To: ${email.recipients}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                val dateFormatted = remember(email.receivedTimestamp) {
                    SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY).format(Date(email.receivedTimestamp))
                }
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Blueprint Section 11: Email HTML Security & Sanitizer badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JluSuccess.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = JluSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HTML Security Pipeline: Safe rendering active (scripts stripped, tracker protection enabled).",
                        style = MaterialTheme.typography.labelSmall,
                        color = JluSuccess,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (email.hasAttachments || email.attachments.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Attachments",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (email.attachments.isNotBlank()) {
                            email.attachments.split("\n").filter { it.isNotBlank() }.forEach { attachment ->
                                Text(
                                    text = "• $attachment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "This email has attachments, but Exchange did not return their names.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Quick read",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = quickSummary.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    quickSummary.keyPoints.forEach { point ->
                        Text(
                            text = "• $point",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = "Extractive overview from message text; verify important details in the original.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            threadSummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Conversation context · ${summary.messageCount} messages",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (summary.participants.isNotEmpty()) {
                            Text(
                                text = "Participants: ${summary.participants.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = "Latest: ${summary.latestSubject}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = summary.latestSummary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            followUpPromise?.let { promise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Possible follow-up",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Detected promise: \"$promise\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            onClick = { onCreateFollowUpTask(promise) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Create follow-up task · 3 days")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (safetyAssessment.level != EmailSafetyLevel.NONE) {
                val highRiskSignals = safetyAssessment.level == EmailSafetyLevel.HIGH
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (highRiskSignals) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (highRiskSignals) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (highRiskSignals) "Multiple security warning signals" else "Review this email carefully",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        safetyAssessment.reasons.forEach { reason ->
                            Text(
                                text = "• $reason",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = "This is a heuristic warning, not proof that the email is malicious or safe.",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (detectedChanges.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Potential changes since the previous email",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        detectedChanges.forEach { change ->
                            Text(
                                text = "${change.label}: ${change.previousValue} → ${change.currentValue}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Text(
                            text = "Only matching dates, times, amounts, rooms, and locations are compared. Verify the original thread.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Blueprint Section 25: Smart Extraction Suggestions
            if (smartSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = JluGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Smart Actions Detected in Email",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        smartSuggestions.forEach { suggestion ->
                            when (suggestion) {
                                is SmartActionSuggestion.CalendarSuggestion -> {
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(
                                            text = suggestion.confidenceReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Source time: ${formatScheduleTime(suggestion.startInstant, suggestion.sourceTimeZoneId)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Your local time: ${formatScheduleTime(suggestion.startInstant, userLocalZoneId)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(
                                        onClick = { onApplyCalendarSuggestion(suggestion) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .testTag("apply_calendar_suggestion_button")
                                    ) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add to Calendar: ${suggestion.title}")
                                    }
                                }
                                is SmartActionSuggestion.TaskSuggestion -> {
                                    Text(
                                        text = suggestion.confidenceReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Button(
                                        onClick = { onApplyTaskSuggestion(suggestion) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = JluNavy
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .testTag("apply_task_suggestion_button")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = JluGold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Task: ${suggestion.title}", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Translation Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { showLangPicker = true },
                        modifier = Modifier.testTag("translate_button")
                    ) {
                        Icon(imageVector = Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentTranslation != null) "Translate: ${currentTranslation.targetLang.displayName}"
                            else "Translate Email"
                        )
                    }

                    DropdownMenu(
                        expanded = showLangPicker,
                        onDismissRequest = { showLangPicker = false }
                    ) {
                        TranslationService.SUPPORTED_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.flagEmoji} ${lang.displayName}") },
                                onClick = {
                                    onTranslate(lang)
                                    showLangPicker = false
                                }
                            )
                        }
                    }
                }

                if (currentTranslation != null) {
                    IconButton(onClick = onClearTranslation) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Show Original")
                    }
                }
            }

            // Reader View is the default for comfortable, privacy-friendly reading.
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            if (currentTranslation == null && email.bodyHtml.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showOriginalHtml) "Original HTML" else "Reader View",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showOriginalHtml = !showOriginalHtml }) {
                        Text(if (showOriginalHtml) "Reader View" else "View Original HTML")
                    }
                }
                if (showOriginalHtml) {
                    Text(
                        "Scripts and automatic remote-image loading are disabled for privacy.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        factory = { context ->
                            WebView(context).apply {
                                setBackgroundColor(AndroidColor.TRANSPARENT)
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                settings.domStorageEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.loadsImagesAutomatically = false
                            }
                        },
                        update = { webView ->
                            webView.settings.javaScriptEnabled = false
                            webView.settings.domStorageEnabled = false
                            val html = email.bodyHtml
                            webView.loadDataWithBaseURL(
                                "https://owa.uni-giessen.de/",
                                "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>body{font-family: sans-serif; font-size:16px; line-height:1.5; overflow-wrap:anywhere; color:#202124} img,table{max-width:100%;height:auto} pre{white-space:pre-wrap;overflow-wrap:anywhere}</style></head><body>$html</body></html>",
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    )
                } else {
                    Text(
                        text = MailTextFormatter.readableBody(email.bodyText.ifBlank { email.bodyHtml }),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp
                    )
                }
            } else if (currentTranslation != null) {
                // Translation Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Translation: German → ${currentTranslation.targetLang.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (currentTranslation.isPartial) {
                    Text(
                        text = "Translation may be incomplete. Compare with the original before acting on important details.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = MailTextFormatter.readableBody(currentTranslation.translatedText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Original German Content:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = MailTextFormatter.plainText(email.bodyText.ifBlank { email.bodyHtml }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = MailTextFormatter.readableBody(email.bodyText.ifBlank { email.bodyHtml }),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Reply, Forward
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onReply,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reply")
                }

                OutlinedButton(
                    onClick = onForward,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Forward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Forward")
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}


private fun formatScheduleTime(instant: Long, zoneId: String): String =
    SimpleDateFormat("EEE, d MMM yyyy · HH:mm z", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
    }.format(Date(instant))
