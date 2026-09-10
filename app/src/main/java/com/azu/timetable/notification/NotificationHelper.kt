package com.azu.timetable.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.azu.timetable.MainActivity
import com.azu.timetable.R
import com.azu.timetable.data.model.TimetableSlot
import com.azu.timetable.receiver.ClassReminderReceiver
import java.util.Calendar

object NotificationHelper {
    const val CHANNEL_ID = "timetable_class_reminders"
    const val CHANNEL_NAME = "Class & Break Reminders"
    const val CHANNEL_DESC = "Notifications for upcoming classes and lectures"

    const val EXTRA_TITLE = "extra_slot_title"
    const val EXTRA_VENUE = "extra_slot_venue"
    const val EXTRA_TIME = "extra_slot_time"
    const val EXTRA_FACULTY = "extra_slot_faculty"
    const val EXTRA_ID = "extra_slot_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showClassNotification(
        context: Context,
        title: String,
        venue: String,
        time: String,
        faculty: String = "",
        notificationId: Int = (System.currentTimeMillis() % 10000).toInt()
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            append("Starts at $time")
            if (venue.isNotBlank()) append(" • Room $venue")
            if (faculty.isNotBlank()) append(" • $faculty")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Upcoming: $title")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title is scheduled for $time.\nVenue: $venue${if (faculty.isNotBlank()) "\nFaculty: $faculty" else ""}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }

    fun scheduleSlotReminder(
        context: Context,
        slot: TimetableSlot,
        minutesBefore: Int = 10
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val parts = slot.startTime.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        // Target day of week: Calendar.MONDAY = 2, Calendar.SUNDAY = 1
        // our dayOfWeek: 1 = Mon -> Calendar.MONDAY, 2 = Tue -> Calendar.TUESDAY, ... 7 = Sun -> Calendar.SUNDAY
        val calendarDay = when (slot.dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, calendarDay)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -minutesBefore)
        }

        // If time already passed this week, advance to next week
        if (targetCal.timeInMillis <= System.currentTimeMillis()) {
            targetCal.add(Calendar.DAY_OF_YEAR, 7)
        }

        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, slot.title)
            putExtra(EXTRA_VENUE, slot.venue)
            putExtra(EXTRA_TIME, "${slot.startTime} - ${slot.endTime}")
            putExtra(EXTRA_FACULTY, slot.faculty)
            putExtra(EXTRA_ID, slot.id.toInt())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            slot.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetCal.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetCal.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact alarm permission is restricted
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                targetCal.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelSlotReminder(context: Context, slotId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ClassReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            slotId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
