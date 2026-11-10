package com.chestcounter.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

const val GOAL = 1000

@Entity(tableName = "chest_events")
data class ChestEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long
)

@Dao
interface ChestDao {
    @Insert
    suspend fun insert(event: ChestEvent)

    @Query("SELECT COUNT(*) FROM chest_events")
    fun totalFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chest_events WHERE timestamp >= :since")
    fun countSinceFlow(since: Long): Flow<Int>

    @Query("SELECT timestamp FROM chest_events ORDER BY timestamp DESC LIMIT 1")
    fun lastFlow(): Flow<Long?>

    @Query("DELETE FROM chest_events WHERE id = (SELECT MAX(id) FROM chest_events)")
    suspend fun deleteLast()

    @Query("DELETE FROM chest_events")
    suspend fun deleteAll()
}

@Database(entities = [ChestEvent::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ChestDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "chestcounter.db"
                ).build().also { instance = it }
            }
    }
}
