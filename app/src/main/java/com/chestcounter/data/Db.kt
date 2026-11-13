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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

object ChestGoal {
    const val TARGET = 1000
}

@Entity(tableName = "chest_events")
data class ChestEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val confidence: Float
)

@Dao
interface ChestDao {
    @Insert
    suspend fun insert(event: ChestEvent): Long

    @Query("SELECT COUNT(*) FROM chest_events")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chest_events")
    suspend fun total(): Int

    @Query("SELECT COUNT(*) FROM chest_events WHERE timestampMs >= :sinceMs")
    fun observeSince(sinceMs: Long): Flow<Int>

    @Query("SELECT * FROM chest_events ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChestEvent>>

    @Query(
        "DELETE FROM chest_events WHERE id = " +
            "(SELECT id FROM chest_events ORDER BY timestampMs DESC LIMIT 1)"
    )
    suspend fun deleteLast(): Int

    @Query("DELETE FROM chest_events")
    suspend fun deleteAll()
}

@Database(
    entities = [ChestEvent::class, SessionEntity::class, ObservationEntity::class, OpeningRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {

    abstract fun chestDao(): ChestDao
    abstract fun sessionDao(): SessionDao

    companion object {
        /** 1 -> 2 : ajout des tables de session. chest_events n'est PAS touchée. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `game_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedWallMs` INTEGER NOT NULL, " +
                        "`startedMonoMs` INTEGER NOT NULL, " +
                        "`endedWallMs` INTEGER, " +
                        "`status` TEXT NOT NULL, " +
                        "`engineVersion` TEXT NOT NULL, " +
                        "`frameWidth` INTEGER NOT NULL, " +
                        "`frameHeight` INTEGER NOT NULL, " +
                        "`framesReceived` INTEGER NOT NULL, " +
                        "`framesProcessed` INTEGER NOT NULL, " +
                        "`observationCount` INTEGER NOT NULL, " +
                        "`droppedObservations` INTEGER NOT NULL, " +
                        "`provisionalCount` INTEGER NOT NULL, " +
                        "`incompleteReason` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chest_observations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`epoch` INTEGER NOT NULL, " +
                        "`tMonoMs` INTEGER NOT NULL, " +
                        "`trackId` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`left` REAL NOT NULL, " +
                        "`top` REAL NOT NULL, " +
                        "`right` REAL NOT NULL, " +
                        "`bottom` REAL NOT NULL, " +
                        "`score` REAL NOT NULL, " +
                        "`visible` INTEGER NOT NULL, " +
                        "`phase` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chest_observations_sessionId_tMonoMs` " +
                        "ON `chest_observations` (`sessionId`, `tMonoMs`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `opening_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`epoch` INTEGER NOT NULL, " +
                        "`trackId` INTEGER NOT NULL, " +
                        "`tMonoMs` INTEGER NOT NULL, " +
                        "`wallMs` INTEGER NOT NULL, " +
                        "`confidence` REAL NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`attribution` TEXT NOT NULL, " +
                        "`reason` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_opening_records_sessionId` " +
                        "ON `opening_records` (`sessionId`)"
                )
            }
        }

        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "chestcounter.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
