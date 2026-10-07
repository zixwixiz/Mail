package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CalendarDao
import com.example.data.local.dao.ContactDao
import com.example.data.local.dao.MailDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.SyncDao
import com.example.data.local.dao.VerificationDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.NoteEntity
import com.example.data.local.entities.SyncQueueEntity
import com.example.data.local.entities.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MailMessageEntity::class,
        CalendarEventEntity::class,
        TaskEntity::class,
        ContactEntity::class,
        NoteEntity::class,
        EndpointVerificationEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mailDao(): MailDao
    abstract fun calendarDao(): CalendarDao
    abstract fun taskDao(): TaskDao
    abstract fun contactDao(): ContactDao
    abstract fun noteDao(): NoteDao
    abstract fun verificationDao(): VerificationDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jlu_mobile.db"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.seedInitialData()
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun seedInitialData() {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        // Seed Emails - Personal Mailbox
        val personalMails = listOf(
            MailMessageEntity(
                id = "mail_jlu_1",
                mailboxId = "personal_primary",
                folder = "INBOX",
                threadId = "th_hrz_1",
                subject = "Wartungsarbeiten an den zentralen Exchange-Diensten (EWS/OWA)",
                senderName = "HRZ Helpdesk",
                senderEmail = "support@hrz.uni-giessen.de",
                recipients = "all-students@uni-giessen.de",
                receivedTimestamp = now - 2 * oneHour,
                bodyText = "Sehr geehrte Universitätsangehörige,\n\nam kommenden Samstag, 18. Oktober, zwischen 02:00 und 06:00 Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am Microsoft Exchange 2019 Clustersystem (owa.uni-giessen.de) durch. Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein.\n\nMit freundlichen Grüßen,\nIhr HRZ-Team",
                bodyHtml = "<p>Sehr geehrte Universitätsangehörige,</p><p>am kommenden Samstag, 18. Oktober, zwischen 02:00 und 06:00 Uhr MESZ führt das Hochschulrechenzentrum planmäßige Wartungsarbeiten am <b>Microsoft Exchange 2019 Clustersystem</b> (owa.uni-giessen.de) durch. Der Zugriff über EWS und Webmail kann in diesem Zeitfenster kurzzeitig unterbrochen sein.</p><p>Mit freundlichen Grüßen,<br/>Ihr HRZ-Team</p>",
                isRead = false,
                isFlagged = true,
                hasAttachments = false,
                category = "HRZ Official"
            ),
            MailMessageEntity(
                id = "mail_jlu_2",
                mailboxId = "personal_primary",
                folder = "INBOX",
                threadId = "th_pruef_1",
                subject = "Fristende: Anmeldung für Modulabschlussprüfungen Wintersemester",
                senderName = "Prüfungsamt FB 07",
                senderEmail = "pruefungsamt-fb07@uni-giessen.de",
                recipients = "s1234567@uni-giessen.de",
                receivedTimestamp = now - 5 * oneHour,
                bodyText = "Guten Tag,\n\nbitte beachten Sie die verbindliche Ausschlussfrist für die Prüfungsanmeldungen: Freitag, 24. Oktober 2026 um 12:00 Uhr.\nNachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich.\n\nBitte prüfen Sie Ihr Prüfungskonto rechtzeitig.",
                bodyHtml = "<p>Guten Tag,</p><p>bitte beachten Sie die <b>verbindliche Ausschlussfrist</b> für die Prüfungsanmeldungen: <b>Freitag, 24. Oktober 2026 um 12:00 Uhr</b>.<br/>Nachmeldungen über FlexNow sind nach diesem Termin nicht mehr möglich.</p><p>Bitte prüfen Sie Ihr Prüfungskonto rechtzeitig.</p>",
                isRead = false,
                isFlagged = false,
                hasAttachments = true,
                category = "Deadlines"
            ),
            MailMessageEntity(
                id = "mail_jlu_3",
                mailboxId = "personal_primary",
                folder = "INBOX",
                threadId = "th_prof_1",
                subject = "Kolloquium: Neue Ansätze verteilter Systeme - Raum 204",
                senderName = "Prof. Dr. Karsten Müller",
                senderEmail = "karsten.mueller@informatik.uni-giessen.de",
                recipients = "s1234567@uni-giessen.de",
                receivedTimestamp = now - oneDay,
                bodyText = "Liebe Studierende,\n\nzum kommenden Forschungskolloquium laden wir Sie herzlich ein:\nDatum: 18. Oktober, 10:00 Uhr MESZ\nOrt: Heinrich-Buff-Ring 14, Seminarraum 204.\nWir diskutieren aktuelle Forschungsarbeiten zu EWS-Synchronisationsmustern und sicheren mobilen Protokollen.",
                bodyHtml = "<p>Liebe Studierende,</p><p>zum kommenden Forschungskolloquium laden wir Sie herzlich ein:<br/><b>Datum: 18. Oktober, 10:00 Uhr MESZ</b><br/>Ort: Heinrich-Buff-Ring 14, Seminarraum 204.<br/>Wir diskutieren aktuelle Forschungsarbeiten zu EWS-Synchronisationsmustern.</p>",
                isRead = true,
                isFlagged = true,
                hasAttachments = false,
                category = "Academic"
            ),
            MailMessageEntity(
                id = "mail_jlu_4",
                mailboxId = "personal_primary",
                folder = "SENT",
                threadId = "th_sent_1",
                subject = "Re: Anfrage Sprechstundentermin Masterarbeit",
                senderName = "Student Account",
                senderEmail = "s1234567@uni-giessen.de",
                recipients = "karsten.mueller@informatik.uni-giessen.de",
                receivedTimestamp = now - 2 * oneDay,
                bodyText = "Sehr geehrter Herr Professor Müller,\n\nvielen Dank für die Rückmeldung. Ich werde am Dienstag um 14:00 Uhr zur Sprechstunde in Raum 112 erscheinen.",
                bodyHtml = "<p>Sehr geehrter Herr Professor Müller,</p><p>vielen Dank für die Rückmeldung. Ich werde am Dienstag um 14:00 Uhr zur Sprechstunde in Raum 112 erscheinen.</p>",
                isRead = true,
                isFlagged = false,
                hasAttachments = false
            )
        )
        mailDao().insertAll(personalMails)

        // Seed Emails - Shared Mailboxes (Prüfungsamt & HRZ Helpdesk)
        val sharedMails = listOf(
            MailMessageEntity(
                id = "mail_shared_1",
                mailboxId = "shared_pruefungsamt",
                folder = "INBOX",
                threadId = "th_shared_p1",
                subject = "[Ticket #4829] Antrag auf Fristverlängerung Bachelor-Thesis",
                senderName = "Studierendensekretariat",
                senderEmail = "sekretariat@fb07.uni-giessen.de",
                recipients = "pruefungsamt-fb07@uni-giessen.de",
                receivedTimestamp = now - 3 * oneHour,
                bodyText = "Weitergeleiteter Antrag mit Attest im Anhang. Bitte um Prüfung durch den Prüfungsausschussvorsitzenden.",
                bodyHtml = "<p>Weitergeleiteter Antrag mit Attest im Anhang. Bitte um Prüfung durch den Prüfungsausschussvorsitzenden.</p>",
                isRead = false,
                isFlagged = true,
                hasAttachments = true,
                category = "Shared Ticket"
            ),
            MailMessageEntity(
                id = "mail_shared_2",
                mailboxId = "shared_hrz_helpdesk",
                folder = "INBOX",
                threadId = "th_shared_h1",
                subject = "[Helpdesk #9102] WLAN eduroam Zertifikatswechsel 2026",
                senderName = "Netzwerkabteilung HRZ",
                senderEmail = "noc@hrz.uni-giessen.de",
                recipients = "support@hrz.uni-giessen.de",
                receivedTimestamp = now - 4 * oneHour,
                bodyText = "Zur Information für den First-Level Support: Das Wurzelzertifikat T-TeleSec GlobalRoot Class 2 wird planmäßig erneuert. Anleitungen im Wiki sind aktualisiert.",
                bodyHtml = "<p>Zur Information für den First-Level Support: Das Wurzelzertifikat wird planmäßig erneuert.</p>",
                isRead = true,
                isFlagged = false,
                hasAttachments = false,
                category = "HRZ Ticket"
            )
        )
        mailDao().insertAll(sharedMails)

        // Seed Calendar Events (Berlin time zone)
        val calendarEvents = listOf(
            CalendarEventEntity(
                id = "evt_1",
                mailboxId = "personal_primary",
                title = "Forschungskolloquium Verteilte Systeme",
                description = "Diskussion zu EWS-Synchronisation und dezentralen Campus-Architekturen",
                startInstant = now + 24 * oneHour,
                endInstant = now + 26 * oneHour,
                timeZone = "Europe/Berlin",
                location = "HBR 14, Seminarraum 204",
                attendees = "Prof. Müller, Dr. Becker, FB07 Kollegium",
                isAllDay = false,
                rsvpStatus = "ACCEPTED"
            ),
            CalendarEventEntity(
                id = "evt_2",
                mailboxId = "personal_primary",
                title = "Sprechstunde Masterarbeit Prof. Müller",
                description = "Besprechung des finalen Exposés und Zeitplans",
                startInstant = now + 48 * oneHour,
                endInstant = now + 49 * oneHour,
                timeZone = "Europe/Berlin",
                location = "Institut für Informatik, Raum 112",
                attendees = "Prof. Müller, s1234567",
                isAllDay = false,
                rsvpStatus = "ACCEPTED"
            ),
            CalendarEventEntity(
                id = "evt_3",
                mailboxId = "shared_pruefungsamt",
                title = "Sitzung des Prüfungsausschusses FB07",
                description = "Entscheidung über Härtefallanträge und Genehmigung von Gutachtern",
                startInstant = now + 72 * oneHour,
                endInstant = now + 74 * oneHour,
                timeZone = "Europe/Berlin",
                location = "Dekanat Sitzungssaal",
                attendees = "Ausschussmitglieder",
                isAllDay = false,
                rsvpStatus = "ACCEPTED"
            )
        )
        calendarDao().insertAll(calendarEvents)

        // Seed Tasks
        val tasks = listOf(
            TaskEntity(
                id = "task_1",
                mailboxId = "personal_primary",
                title = "Prüfungsanmeldung FlexNow abschließen",
                description = "Fristende beachten: 24. Oktober um 12:00 Uhr",
                dueInstant = now + 5 * oneDay,
                timeZone = "Europe/Berlin",
                priority = "HIGH",
                isCompleted = false,
                source = "EMAIL_EXTRACTED",
                sourceEmailId = "mail_jlu_2"
            ),
            TaskEntity(
                id = "task_2",
                mailboxId = "personal_primary",
                title = "Exposé-Folien für Kolloquium vorbereiten",
                description = "Präsentationsentwurf für Raum 204",
                dueInstant = now + 20 * oneHour,
                timeZone = "Europe/Berlin",
                priority = "NORMAL",
                isCompleted = false,
                source = "MANUAL"
            ),
            TaskEntity(
                id = "task_3",
                mailboxId = "shared_hrz_helpdesk",
                title = "Ticket #9102 FAQ Eintrag verfassen",
                description = "Dokumentation für Studierende zum neuen eduroam-Profil",
                dueInstant = now + 2 * oneDay,
                timeZone = "Europe/Berlin",
                priority = "NORMAL",
                isCompleted = false,
                source = "EMAIL_EXTRACTED",
                sourceEmailId = "mail_shared_2"
            )
        )
        taskDao().insertAll(tasks)

        // Seed Contacts across multiple JLU faculties and central departments
        val contacts = listOf(
            ContactEntity(
                id = "c_1",
                mailboxId = "personal_primary",
                displayName = "Prof. Dr. Karsten Müller",
                email = "karsten.mueller@informatik.uni-giessen.de",
                phone = "+49 641 99-32140",
                organization = "Justus-Liebig-Universität Gießen",
                department = "FB 07 - Mathematik und Informatik",
                officeRoom = "Heinrich-Buff-Ring 14, Raum 112",
                isJluDirectory = true
            ),
            ContactEntity(
                id = "c_2",
                mailboxId = "personal_primary",
                displayName = "HRZ IT-Service Desk",
                email = "support@hrz.uni-giessen.de",
                phone = "+49 641 99-13100",
                organization = "Justus-Liebig-Universität Gießen",
                department = "Hochschulrechenzentrum (HRZ)",
                officeRoom = "Heinrich-Buff-Ring 44",
                isJluDirectory = true
            ),
            ContactEntity(
                id = "c_3",
                mailboxId = "personal_primary",
                displayName = "Zentrales Prüfungsamt (ZPA)",
                email = "zpa@uni-giessen.de",
                phone = "+49 641 99-12111",
                organization = "Justus-Liebig-Universität Gießen",
                department = "Zentrales Prüfungsamt",
                officeRoom = "Ludwigstraße 23, Raum 102",
                isJluDirectory = true
            ),
            ContactEntity(
                id = "c_4",
                mailboxId = "personal_primary",
                displayName = "Studierendensekretariat JLU",
                email = "studierendensekretariat@admin.uni-giessen.de",
                phone = "+49 641 99-16400",
                organization = "Justus-Liebig-Universität Gießen",
                department = "Zentrale Universitätsverwaltung",
                officeRoom = "Goethestraße 58",
                isJluDirectory = true
            ),
            ContactEntity(
                id = "c_5",
                mailboxId = "personal_primary",
                displayName = "Universitätsbibliothek (UB) Ausleihe",
                email = "ausleihe@bibsys.uni-giessen.de",
                phone = "+49 641 99-14000",
                organization = "Justus-Liebig-Universität Gießen",
                department = "Universitätsbibliothek",
                officeRoom = "Otto-Behaghel-Straße 8",
                isJluDirectory = true
            ),
            ContactEntity(
                id = "c_6",
                mailboxId = "personal_primary",
                displayName = "Prof. Dr. Elena Fischer",
                email = "elena.fischer@fb02.uni-giessen.de",
                phone = "+49 641 99-22010",
                organization = "Justus-Liebig-Universität Gießen",
                department = "FB 02 - Wirtschaftswissenschaften",
                officeRoom = "Licher Straße 66",
                isJluDirectory = true
            )
        )
        contactDao().insertAll(contacts)

        // Seed Notes
        val note = NoteEntity(
            id = "note_1",
            mailboxId = "personal_primary",
            title = "Notizen zu EWS-Protokoll & JLU Exchange 2019",
            content = "- Endpoint: https://owa.uni-giessen.de/EWS/Exchange.asmx\n- Exchange Version: Exchange 2019\n- Auth: Prüfen auf Negotiate / NTLM / Basic\n- Kein Exchange Online (Datenspeicherung nur bei der JLU)",
            updatedTimestamp = now,
            linkedEmailId = "mail_jlu_1"
        )
        noteDao().insert(note)

        // Initial default verification record
        val defaultVerif = EndpointVerificationEntity(
            id = 1,
            host = "owa.uni-giessen.de",
            ewsUrl = "https://owa.uni-giessen.de/EWS/Exchange.asmx",
            gate1Passed = true,
            gate2Passed = true,
            gate3Passed = true,
            verifiedMechanism = "NEGOTIATE, NTLM",
            challengeHeaders = "WWW-Authenticate: Negotiate\nWWW-Authenticate: NTLM\nWWW-Authenticate: Basic realm=\"owa.uni-giessen.de\"",
            lastVerifiedTimestamp = now - 30 * 60 * 1000L,
            latencyMs = 184
        )
        verificationDao().saveVerification(defaultVerif)
    }
}
