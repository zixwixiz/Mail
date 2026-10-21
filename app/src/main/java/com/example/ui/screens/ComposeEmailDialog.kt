package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MailboxAccount
import com.example.ui.theme.JluWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailDialog(
    activeMailbox: MailboxAccount,
    onDismiss: () -> Unit,
    onSend: (subject: String, recipients: String, body: String, isDraft: Boolean) -> Unit,
    initialSubject: String = "",
    initialRecipient: String = "",
    initialBody: String = "",
    dialogTitle: String = "New Message",
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var recipient by remember(initialRecipient) { mutableStateOf(initialRecipient) }
    var subject by remember(initialSubject) { mutableStateOf(initialSubject) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }

    val quickReplies = listOf(
        "Thanks for the update. I'll review this and get back to you.",
        "Could you please clarify the deadline and next steps?",
        "I confirm receipt of your message. Thank you."
    )
    fun insertQuickReply(template: String) {
        val quoteStart = listOf(
            body.indexOf("\n\n--- Original message ---").takeIf { it >= 0 },
            body.indexOf("\n\n--- Forwarded message ---").takeIf { it >= 0 }
        ).filterNotNull().minOrNull() ?: body.length
        val editableText = body.substring(0, quoteStart).trim()
        val quotedText = body.substring(quoteStart)
        body = if (editableText.isBlank()) "$template$quotedText" else "$editableText\n\n$template$quotedText"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("compose_email_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sender Identity Information
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "From: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${activeMailbox.displayName} <${activeMailbox.emailAddress}>",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (activeMailbox.isSharedMailbox) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = JluWarning.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Shared Mailbox",
                                style = MaterialTheme.typography.labelSmall,
                                color = JluWarning,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("To (e.g. prof.name@uni-giessen.de)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_to_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_subject_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (dialogTitle == "Reply") {
                Text(
                    text = "Quick reply templates",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickReplies) { template ->
                        FilterChip(
                            selected = false,
                            onClick = { insertQuickReply(template) },
                            label = {
                                Text(
                                    when (quickReplies.indexOf(template)) {
                                        0 -> "Thanks"
                                        1 -> "Clarify"
                                        else -> "Confirm receipt"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message Body") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("compose_body_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSend(subject, recipient, body, true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Drafts, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Draft")
                }

                Button(
                    onClick = { onSend(subject, recipient, body, false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("send_email_button"),
                    enabled = recipient.isNotBlank() && subject.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
