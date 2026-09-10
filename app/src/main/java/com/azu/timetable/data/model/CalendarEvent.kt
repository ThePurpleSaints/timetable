package com.azu.timetable.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String = "", // yyyy-MM-dd
    val day: String = "",
    val details: String = "",
    val isHoliday: Boolean = false
)