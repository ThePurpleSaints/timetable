package com.azu.timetable.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "date_overrides")
data class DateOverride(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val overrideDate: String = "", // yyyy-MM-dd
    val sectionId: String = "",
    val period: Int? = null, // null = whole day
    val cellType: String = "",
    val courseCode: String = "",
    val activity: String = "",
    val room: String = "",
    val isCancelled: Boolean = false,
    val reason: String = ""
)