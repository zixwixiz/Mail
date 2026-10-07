package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }

}
