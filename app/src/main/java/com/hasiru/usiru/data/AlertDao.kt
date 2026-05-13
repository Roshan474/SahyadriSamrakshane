package com.hasiru.usiru.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM ecological_alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EcologicalAlert>>

    @Query("SELECT * FROM ecological_alerts WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun unsynced(): List<EcologicalAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: EcologicalAlert): Long

    @Query("UPDATE ecological_alerts SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE ecological_alerts SET status = :status, synced = 0 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: AlertStatus)
}
