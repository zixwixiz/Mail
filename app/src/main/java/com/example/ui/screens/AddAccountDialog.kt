package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MailboxPermission
import com.example.data.network.AuthVerificationResult
import com.example.data.network.AuthVerificationService
import com.example.ui.theme.JluGold
import com.example.ui.theme.JluNavy
import com.example.ui.theme.JluSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    isVerifying: Boolean,
    verificationResult: AuthVerificationResult?,
    onDismiss: () -> Unit,
    onAddAccount: (
        accountIdentifier: String,
        displayName: String,
        emailAddress: String,
        isSharedMailbox: Boolean,
        permission: MailboxPermission,
        department: String,
        endpointUrl: String,
        password: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Primary Credentials (Strictly only Username/Kennung and Password needed)
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Advanced & Optional Customizations
    var showAdvanced by remember { mutableStateOf(false) }
    var isSharedMailbox by remember { mutableStateOf(false) }
    var customDisplayName by remember { mutableStateOf("") }
    var customEmail by remember { mutableStateOf("") }
    var selectedPermission by remember { mutableStateOf(MailboxPermission.OWNER) }
    var customDepartment by remember { mutableStateOf("") }
    var endpointUrl by remember { mutableStateOf(AuthVerificationService.DEFAULT_JLU_EWS_ENDPOINT) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("add_account_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sign in to JLU Exchange",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connect with your official JLU credentials",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Official Protocol Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Protocol: Exchange EWS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = endpointUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CREDENTIAL 1: Username / Kennung
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("JLU Account ID (Kennung)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_identifier_input"),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = JluNavy)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // CREDENTIAL 2: Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_password_input"),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = {
                    Icon(Icons.Default.Security, contentDescription = null, tint = JluGold)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Use the JLU account identifier (Kennung) and enter the complete university email address separately.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Optional Advanced Settings Expandable
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 4.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showAdvanced) "Hide Advanced & Shared Mailbox Settings" else "Optional: Shared Mailbox & Advanced Settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Personal vs Shared Mailbox Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isSharedMailbox,
                            onClick = {
                                isSharedMailbox = false
                                selectedPermission = MailboxPermission.OWNER
                            },
                            label = { Text("Personal Account") },
                            leadingIcon = {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = false,
                            onClick = { },
                                isSharedMailbox = true
                                selectedPermission = MailboxPermission.EDITOR
                            },
                            label = { Text("Shared Mailbox (OWA only)") },
                            leadingIcon = {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isSharedMailbox) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Shared Mailbox Permissions:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MailboxPermission.values().forEach { perm ->
                                FilterChip(
                                    selected = selectedPermission == perm,
                                    onClick = { selectedPermission = perm },
                                    label = { Text(perm.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customDisplayName,
                        onValueChange = { customDisplayName = it },
                        label = { Text("Custom Display Name (Optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_display_name_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it },
                        label = { Text("JLU Email Address") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_email_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customDepartment,
                        onValueChange = { customDepartment = it },
                        label = { Text("Faculty / Department (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = endpointUrl,
                        onValueChange = { endpointUrl = it },
                        label = { Text("EWS SOAP Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Verification Result Card (if any failure or info)
            if (verificationResult != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (verificationResult.isSuccess)
                            JluSuccess.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (verificationResult.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (verificationResult.isSuccess) JluSuccess else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (verificationResult.isSuccess) "EWS Endpoint Verified" else "Verification Failed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (verificationResult.isSuccess)
                                    "Mailbox access verified for this session via ${verificationResult.selectedMechanism.displayName} (HTTP ${verificationResult.httpStatusCode})."
                                else
                                    verificationResult.errorMessage ?: "Could not verify credentials",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Security assurance pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = JluGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Credentials stay in the current app session and are sent only to the configured EWS endpoint.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            val isReady = username.isNotBlank() && password.isNotBlank() && customEmail.isNotBlank() && !isVerifying
            Button(
                onClick = {
                    val finalEmail = customEmail.trim()

                    val finalDisplayName = if (customDisplayName.isNotBlank()) {
                        customDisplayName.trim()
                    } else {
                        username.trim()
                    }

                    val finalDepartment = if (customDepartment.isNotBlank()) {
                        customDepartment.trim()
                    } else {
                        "Justus-Liebig-Universität Gießen"
                    }

                    onAddAccount(
                        username.trim(),
                        finalDisplayName,
                        finalEmail,
                        isSharedMailbox,
                        selectedPermission,
                        finalDepartment,
                        endpointUrl.trim(),
                        password
                    )
                },
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_and_add_account_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying Credentials & EWS Headers...")
                } else {
                    Text("Log In & Verify JLU Account")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
