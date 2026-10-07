package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.CalendarEventEntity
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.EndpointVerificationEntity
import com.example.data.local.entities.MailMessageEntity
import com.example.data.local.entities.NoteEntity
import com.example.data.local.entities.SyncQueueEntity
import com.example.data.local.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {
    @Query("SELECT * FROM mail_messages WHERE mailboxId = :mailboxId AND folder = :folder ORDER BY receivedTimestamp DESC")
    fun getMessages(mailboxId: String, folder: String): Flow<List<MailMessageEntity>>

    @Query("SELECT * FROM mail_messages WHERE mailboxId = :mailboxId ORDER BY receivedTimestamp DESC")
    fun getAllMessagesForMailbox(mailboxId: String): Flow<List<MailMessageEntity>>

    @Query("SELECT * FROM mail_messages WHERE id = :id")
    fun getMessageById(id: String): Flow<MailMessageEntity?>

    @Query("SELECT * FROM mail_messages WHERE subject LIKE '%' || :query || '%' OR senderName LIKE '%' || :query || '%' OR bodyText LIKE '%' || :query || '%'")
    fun searchMessages(query: String): Flow<List<MailMessageEntity>>

    @Query("SELECT COUNT(*) FROM mail_messages WHERE mailboxId = :mailboxId AND isRead = 0")
    fun getUnreadCount(mailboxId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MailMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MailMessageEntity>)

    @Query("UPDATE mail_messages SET isRead = :isRead WHERE id = :id")
    suspend fun setReadState(id: String, isRead: Boolean)

    @Query("UPDATE mail_messages SET isFlagged = :isFlagged WHERE id = :id")
    suspend fun setFlagState(id: String, isFlagged: Boolean)

    @Query("DELETE FROM mail_messages WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_events WHERE mailboxId = :mailboxId ORDER BY startInstant ASC")
    fun getEvents(mailboxId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%'")
    fun searchEvents(query: String): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEventEntity>)

    @Query("UPDATE calendar_events SET rsvpStatus = :status WHERE id = :id")
    suspend fun updateRsvp(id: String, status: String)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE mailboxId = :mailboxId ORDER BY isCompleted ASC, priority = 'HIGH' DESC, dueInstant ASC")
    fun getTasks(mailboxId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchTasks(query: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET isCompleted = :isCompleted, completedTimestamp = :completedTimestamp WHERE id = :id")
    suspend fun setCompleteState(id: String, isCompleted: Boolean, completedTimestamp: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE mailboxId = :mailboxId OR isJluDirectory = 1 ORDER BY displayName ASC")
    fun getContacts(mailboxId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE displayName LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' OR department LIKE '%' || :query || '%'")
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE mailboxId = :mailboxId ORDER BY updatedTimestamp DESC")
    fun getNotes(mailboxId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VerificationDao {
    @Query("SELECT * FROM endpoint_verification WHERE id = 1")
    fun getVerification(): Flow<EndpointVerificationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVerification(entity: EndpointVerificationEntity)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingOperations(): Flow<List<SyncQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queueOperation(op: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun markComplete(id: Long)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
}
