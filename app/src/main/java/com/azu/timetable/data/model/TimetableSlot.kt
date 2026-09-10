package com.azu.timetable.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SlotType {
    CLASS,
    LAB,
    BREAK,
    ACTIVITY,
    MENTORING
}

@Immutable
@Entity(tableName = "timetable_slots")
data class TimetableSlot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayOfWeek: Int, // 1 = Mon, 2 = Tue, 3 = Wed, 4 = Thu, 5 = Fri, 6 = Sat, 7 = Sun
    val periodNumber: Int = 0, // 1..8, or 0 for breaks
    val title: String,
    val courseCode: String = "",
    val faculty: String = "",
    val venue: String = "A204",
    val startTime: String, // HH:mm format, e.g. "08:00"
    val endTime: String, // HH:mm format, e.g. "08:50"
    val slotType: SlotType = SlotType.CLASS,
    val notes: String = "",
    val isNotificationEnabled: Boolean = true,
    val color: Long? = null
)
