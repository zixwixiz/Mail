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
    companion object {
        val DEFAULT_PERSONAL = MailboxAccount(
            id = "personal_primary",
            displayName = "Student Account (s-kennung)",
            emailAddress = "s1234567@uni-giessen.de",
            isSharedMailbox = false,
            permission = MailboxPermission.OWNER,
            department = "FB 07 - Mathematik und Informatik",
            unreadCount = 3
        )

        val DEFAULT_SHARED_1 = MailboxAccount(
            id = "shared_pruefungsamt",
            displayName = "Prüfungsamt FB07",
            emailAddress = "pruefungsamt-fb07@uni-giessen.de",
            isSharedMailbox = true,
            permission = MailboxPermission.REVIEWER,
            department = "Fachbereich 07 Prüfungsbüro",
            unreadCount = 1
        )

        val DEFAULT_SHARED_2 = MailboxAccount(
            id = "shared_hrz_helpdesk",
            displayName = "HRZ Helpdesk Student Assistants",
            emailAddress = "support@hrz.uni-giessen.de",
            isSharedMailbox = true,
            permission = MailboxPermission.EDITOR,
            department = "Hochschulrechenzentrum (HRZ)",
            unreadCount = 5
        )

        val DEFAULT_SHARED_3 = MailboxAccount(
            id = "shared_fachschaft",
            displayName = "Fachschaft Informatik",
            emailAddress = "fachschaft-info@uni-giessen.de",
            isSharedMailbox = true,
            permission = MailboxPermission.EDITOR,
            department = "Studierendenvertretung",
            unreadCount = 2
        )
    }
}
