package com.armor.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One line of the audit trail. [synced] flips once the event has reached the remote group log. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val type: String,
    val detail: String,
    val synced: Boolean = false,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
)

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Query("SELECT * FROM events ORDER BY timeMs DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE synced = 0 ORDER BY timeMs ASC, id ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<EventEntity>

    @Query("UPDATE events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Dao
interface ContactDao {
    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    suspend fun snapshot(): List<ContactEntity>
}

@Database(entities = [EventEntity::class, ContactEntity::class], version = 1, exportSchema = false)
abstract class ArmorDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun contacts(): ContactDao
}
