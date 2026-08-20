package com.kaizen.auto.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternMemoryDao {

    @Query("SELECT * FROM pattern_memory WHERE patternKey = :key LIMIT 1")
    fun findSync(key: String): PatternMemory?

    @Query("SELECT * FROM pattern_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PatternMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(memory: PatternMemory): Long

    @Query("DELETE FROM pattern_memory WHERE patternKey = :key")
    fun delete(key: String)

    @Query("DELETE FROM pattern_memory")
    fun clear()
}

@Dao
interface HealingEventDao {

    @Insert
    fun insert(event: HealingEvent): Long

    @Query("SELECT * FROM healing_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<HealingEvent>>

    @Query("SELECT COUNT(*) FROM healing_events WHERE succeeded = 1")
    fun successCount(): Int

    @Query("DELETE FROM healing_events WHERE createdAt < :cutoff")
    fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM healing_events")
    fun clear()
}

@Dao
interface ScreenObservationDao {

    @Query("SELECT * FROM screen_observations WHERE signature = :signature LIMIT 1")
    fun findSync(signature: String): ScreenObservation?

    @Query("SELECT * FROM screen_observations ORDER BY lastSeenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ScreenObservation>>

    @Query("SELECT * FROM screen_observations WHERE packageName = :pkg ORDER BY seenCount DESC LIMIT :limit")
    fun forPackage(pkg: String, limit: Int = 50): List<ScreenObservation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(observation: ScreenObservation): Long

    @Update
    fun update(observation: ScreenObservation)

    @Query("SELECT COUNT(*) FROM screen_observations")
    fun count(): Int

    @Query("DELETE FROM screen_observations")
    fun clear()
}

@Dao
interface RunLogDao {

    @Insert
    fun insert(log: RunLog): Long

    @Query("SELECT * FROM run_logs ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<RunLog>>

    @Query("DELETE FROM run_logs")
    fun clear()

    @Query("DELETE FROM run_logs WHERE createdAt < :cutoff")
    fun purgeOlderThan(cutoff: Long)
}
