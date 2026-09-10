package com.azu.timetable.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.azu.timetable.data.model.DateOverride
import kotlinx.coroutines.flow.Flow

@Dao
interface DateOverrideDao {
    @Query("SELECT * FROM date_overrides ORDER BY overrideDate ASC")
    fun getAll(): Flow<List<DateOverride>>

    @Query("DELETE FROM date_overrides")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(overrides: List<DateOverride>)
}