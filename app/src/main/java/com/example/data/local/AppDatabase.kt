package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CalendarDao
import com.example.data.local.dao.ContactDao
import com.example.data.local.dao.MailDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.VerificationDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.NoteEntity
import com.example.data.local.entities.TaskEntity

@Database(
    entities = [
        MailMessageEntity::class,
        CalendarEventEntity::class,
        TaskEntity::class,
        ContactEntity::class,
        NoteEntity::class,
        EndpointVerificationEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mailDao(): MailDao
    abstract fun calendarDao(): CalendarDao
    abstract fun taskDao(): TaskDao
    abstract fun contactDao(): ContactDao
    abstract fun noteDao(): NoteDao
    abstract fun verificationDao(): VerificationDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS endpoint_verification")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS endpoint_verification (
                        mailboxId TEXT NOT NULL,
                        host TEXT NOT NULL,
                        ewsUrl TEXT NOT NULL,
                        gate1Passed INTEGER NOT NULL,
                        gate2Passed INTEGER NOT NULL,
                        gate3Passed INTEGER NOT NULL,
                        verifiedMechanism TEXT NOT NULL,
                        challengeHeaders TEXT NOT NULL,
                        lastVerifiedTimestamp INTEGER NOT NULL,
                        latencyMs INTEGER NOT NULL,
                        PRIMARY KEY(mailboxId)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sync_queue")
                db.execSQL("DELETE FROM mail_messages WHERE mailboxId IN ('personal_primary', 'shared_pruefungsamt', 'shared_hrz_helpdesk', 'shared_fachschaft')")
                db.execSQL("DELETE FROM calendar_events WHERE mailboxId IN ('personal_primary', 'shared_pruefungsamt', 'shared_hrz_helpdesk', 'shared_fachschaft')")
                db.execSQL("DELETE FROM tasks WHERE mailboxId IN ('personal_primary', 'shared_pruefungsamt', 'shared_hrz_helpdesk', 'shared_fachschaft')")
                db.execSQL("DELETE FROM contacts WHERE mailboxId IN ('personal_primary', 'shared_pruefungsamt', 'shared_hrz_helpdesk', 'shared_fachschaft')")
                db.execSQL("DELETE FROM notes WHERE mailboxId IN ('personal_primary', 'shared_pruefungsamt', 'shared_hrz_helpdesk', 'shared_fachschaft')")
                db.execSQL("DELETE FROM endpoint_verification WHERE id = 1")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jlu_mobile.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}
