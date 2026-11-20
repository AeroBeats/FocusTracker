package com.focustracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<FocusSession>>

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Update
    suspend fun update(session: FocusSession)

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun activeSession(): FocusSession?
}
