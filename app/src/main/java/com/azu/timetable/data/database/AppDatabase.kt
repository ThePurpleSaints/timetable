package com.azu.timetable.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.azu.timetable.data.dao.CalendarEventDao
import com.azu.timetable.data.dao.DateOverrideDao
import com.azu.timetable.data.dao.TimetableDao
import com.azu.timetable.data.model.CalendarEvent
import com.azu.timetable.data.model.DateOverride
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot

class Converters {
    @TypeConverter
    fun fromSlotType(value: SlotType): String {
        return value.name
    }

    @TypeConverter
    fun toSlotType(value: String): SlotType {
        return try {
            SlotType.valueOf(value)
        } catch (e: Exception) {
            SlotType.CLASS
        }
    }
}

@Database(entities = [TimetableSlot::class, DateOverride::class, CalendarEvent::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao

    abstract fun dateOverrideDao(): DateOverrideDao

    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Only two breaks exist (Morning Break, Lunch Break). The extra
        // 'Short Break' rows were seeded by an older DefaultTimetable build.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM timetable_slots WHERE slotType = 'BREAK' AND title = 'Short Break'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetable_database"
                )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
