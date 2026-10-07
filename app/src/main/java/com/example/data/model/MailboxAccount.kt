package com.example.data.model

enum class MailboxPermission(val label: String, val canWrite: Boolean, val canManage: Boolean) {
    OWNER("Owner", true, true),
    EDITOR("Editor (Send/Edit)", true, false),
    REVIEWER("Reviewer (Read-Only)", false, false)
}

data class MailboxAccount(
    val id: String,
    val displayName: String,
    val emailAddress: String,
    val isSharedMailbox: Boolean,
    val permission: MailboxPermission,
    val department: String,
    val unreadCount: Int = 0,
    val username: String = "",
    val password: String = "",
    val endpointUrl: String = "https://owa.uni-giessen.de/EWS/Exchange.asmx"
) {
    val isConfigured: Boolean
        get() = id.isNotBlank() && username.isNotBlank() && endpointUrl.isNotBlank()

    companion object {
        val UNCONFIGURED = MailboxAccount(
            id = "",
            displayName = "No mailbox connected",
            emailAddress = "",
            isSharedMailbox = false,
            permission = MailboxPermission.REVIEWER,
            department = ""
        )
    }
}
