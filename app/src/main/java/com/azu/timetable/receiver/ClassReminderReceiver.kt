package com.azu.timetable.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azu.timetable.data.database.AppDatabase
import com.azu.timetable.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule alarms after device reboot
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val slots = db.timetableDao().getAllSlots().firstOrNull() ?: emptyList()
                for (slot in slots) {
                    if (slot.isNotificationEnabled) {
                        NotificationHelper.scheduleSlotReminder(context, slot)
                    }
                }
            }
            return
        }

        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Upcoming Class"
        val venue = intent.getStringExtra(NotificationHelper.EXTRA_VENUE) ?: ""
        val time = intent.getStringExtra(NotificationHelper.EXTRA_TIME) ?: ""
        val faculty = intent.getStringExtra(NotificationHelper.EXTRA_FACULTY) ?: ""
        val id = intent.getIntExtra(NotificationHelper.EXTRA_ID, (System.currentTimeMillis() % 10000).toInt())

        NotificationHelper.showClassNotification(
            context = context,
            title = title,
            venue = venue,
            time = time,
            faculty = faculty,
            notificationId = id
        )
    }
}
