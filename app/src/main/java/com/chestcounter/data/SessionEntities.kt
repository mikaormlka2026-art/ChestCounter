package com.chestcounter.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object SessionStatus {
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val INTERRUPTED = "INTERRUPTED"
    const val ANALYZED = "ANALYZED"
}

object OpeningSource {
    const val LIVE = "LIVE"
    const val REANALYSIS = "REANALYSIS"
}

@Entity(tableName = "game_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedWallMs: Long,
    val startedMonoMs: Long,
    val endedWallMs: Long?,
    val status: String,
    val engineVersion: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val framesReceived: Int,
    val framesProcessed: Int,
    val observationCount: Int,
    val droppedObservations: Int,
    val provisionalCount: Int,
    val incompleteReason: String?
)

@Entity(
    tableName = "chest_observations",
    indices = [Index(value = ["sessionId", "tMonoMs"])]
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val epoch: Int,
    val tMonoMs: Long,
    val trackId: Int,
    val kind: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val visible: Boolean,
    val phase: String?
)

@Entity(
    tableName = "opening_records",
    indices = [Index(value = ["sessionId"])]
)
data class OpeningRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val epoch: Int,
    val trackId: Int,
    val tMonoMs: Long,
    val wallMs: Long,
    val confidence: Float,
    val source: String,
    val attribution: String,
    val reason: String?
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Insert
    suspend fun insertObservations(list: List<ObservationEntity>)

    @Insert
    suspend fun insertOpening(o: OpeningRecord): Long

    @Query(
        "UPDATE game_sessions SET status = :to, endedWallMs = :wallMs, incompleteReason = :reason " +
            "WHERE status = :from"
    )
    suspend fun closeStale(from: String, to: String, wallMs: Long, reason: String): Int

    @Query(
        "UPDATE game_sessions SET status = :status, endedWallMs = :wallMs, framesReceived = :received, " +
            "framesProcessed = :processed, observationCount = :observations, " +
            "droppedObservations = :dropped, provisionalCount = :provisional, " +
            "frameWidth = :width, frameHeight = :height, incompleteReason = :reason WHERE id = :id"
    )
    suspend fun finishSession(
        id: Long,
        status: String,
        wallMs: Long,
        received: Int,
        processed: Int,
        observations: Int,
        dropped: Int,
        provisional: Int,
        width: Int,
        height: Int,
        reason: String?
    ): Int

    @Query("SELECT * FROM game_sessions ORDER BY id DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM chest_observations WHERE sessionId = :id ORDER BY tMonoMs, id")
    suspend fun observations(id: Long): List<ObservationEntity>

    @Query("SELECT * FROM opening_records WHERE sessionId = :id ORDER BY tMonoMs, id")
    suspend fun openings(id: Long): List<OpeningRecord>

    @Query("DELETE FROM opening_records WHERE sessionId = :id AND source = :source")
    suspend fun deleteOpenings(id: Long, source: String): Int

    @Query("DELETE FROM chest_observations WHERE sessionId = :id")
    suspend fun deleteObservations(id: Long): Int

    @Query("DELETE FROM game_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long): Int
}
