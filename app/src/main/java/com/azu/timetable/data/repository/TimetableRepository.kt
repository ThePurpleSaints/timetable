package com.azu.timetable.data.repository

import com.azu.timetable.data.dao.CalendarEventDao
import com.azu.timetable.data.dao.DateOverrideDao
import com.azu.timetable.data.dao.TimetableDao
import com.azu.timetable.data.model.CalendarEvent
import com.azu.timetable.data.model.DateOverride
import com.azu.timetable.data.model.TimetableSlot
import kotlinx.coroutines.flow.Flow

class TimetableRepository(
    private val timetableDao: TimetableDao,
    private val dateOverrideDao: DateOverrideDao,
    private val calendarEventDao: CalendarEventDao
) {

    val allSlots: Flow<List<TimetableSlot>> = timetableDao.getAllSlots()

    val allOverrides: Flow<List<DateOverride>> = dateOverrideDao.getAll()

    val allCalendarEvents: Flow<List<CalendarEvent>> = calendarEventDao.getAll()

    fun getSlotsForDay(dayOfWeek: Int): Flow<List<TimetableSlot>> {
        return timetableDao.getSlotsForDay(dayOfWeek)
    }

    suspend fun getSlotsForDayDirect(dayOfWeek: Int): List<TimetableSlot> {
        return timetableDao.getSlotsForDayDirect(dayOfWeek)
    }

    suspend fun insertSlot(slot: TimetableSlot): Long {
        return timetableDao.insertSlot(slot)
    }

    suspend fun updateSlot(slot: TimetableSlot) {
        timetableDao.updateSlot(slot)
    }

    suspend fun deleteSlot(slot: TimetableSlot) {
        timetableDao.deleteSlot(slot)
    }

    suspend fun deleteSlotById(id: Long) {
        timetableDao.deleteSlotById(id)
    }

    suspend fun populateDefaultsIfNeeded() {
        val count = timetableDao.getCount()
        if (count == 0) {
            timetableDao.insertSlots(DefaultTimetable.getInitialSlots())
        }
    }

    suspend fun resetAndInsert(slots: List<TimetableSlot>) {
        timetableDao.clearAll()
        timetableDao.insertSlots(slots)
    }

    suspend fun resetOverrides(overrides: List<DateOverride>) {
        dateOverrideDao.clearAll()
        dateOverrideDao.insertAll(overrides)
    }

    suspend fun resetCalendarEvents(events: List<CalendarEvent>) {
        calendarEventDao.clearAll()
        calendarEventDao.insertAll(events)
    }

    suspend fun resetToDefaultSchedule() {
        timetableDao.clearAll()
        timetableDao.insertSlots(DefaultTimetable.getInitialSlots())
    }
}
