package com.azu.timetable.ui.components

import androidx.compose.ui.graphics.Color
import com.azu.timetable.data.model.CalendarEvent
import java.util.Locale

enum class EventKind { TEST, CAT, HOLIDAY, EXAM, COMPLETION, ASSIGNMENT, MEETING, OTHER }

val TestLime = Color(0xFF76FF03)
val TestRed = Color(0xFFE60000)
val HolidayPink = Color(0xFFE91E63)
val ExamBlack = Color(0xFF000000)
val CompletionBlue = Color(0xFF1E88E5)
val AssignmentAmber = Color(0xFFFB8C00)
val MeetingTeal = Color(0xFF00897B)
val OtherViolet = Color(0xFF8E24AA)

fun eventKind(ev: CalendarEvent): EventKind {
    val d = ev.details.lowercase(Locale.US)
    return when {
        ev.isHoliday -> EventKind.HOLIDAY
        d.contains("cat") -> EventKind.CAT
        d.contains("cycle test") || d.contains("assessment test") -> EventKind.TEST
        d.contains("theory exam") || d.contains("practical exam") || d.contains("end sem") -> EventKind.EXAM
        d.contains("completion") -> EventKind.COMPLETION
        d.contains("due date") || d.contains("mark entry") -> EventKind.ASSIGNMENT
        d.contains("meeting") || d.contains("counselling") || d.contains("feedback") -> EventKind.MEETING
        else -> EventKind.OTHER
    }
}

fun isTestKind(kind: EventKind): Boolean = kind == EventKind.TEST || kind == EventKind.CAT

fun kindColor(kind: EventKind): Color = when (kind) {
    EventKind.TEST -> TestLime
    EventKind.CAT -> TestRed
    EventKind.HOLIDAY -> HolidayPink
    EventKind.EXAM -> ExamBlack
    EventKind.COMPLETION -> CompletionBlue
    EventKind.ASSIGNMENT -> AssignmentAmber
    EventKind.MEETING -> MeetingTeal
    EventKind.OTHER -> OtherViolet
}

fun isSolidKind(kind: EventKind): Boolean =
    kind == EventKind.TEST || kind == EventKind.CAT || kind == EventKind.HOLIDAY || kind == EventKind.EXAM

fun labelOf(kind: EventKind): String = when (kind) {
    EventKind.TEST -> "Cycle Tests"
    EventKind.CAT -> "CATs"
    EventKind.HOLIDAY -> "Holidays"
    EventKind.EXAM -> "End Sem"
    EventKind.COMPLETION -> "Completions"
    EventKind.ASSIGNMENT -> "Assignments"
    EventKind.MEETING -> "Meetings"
    EventKind.OTHER -> "Other"
}

fun isHighlightEvent(ev: CalendarEvent): Boolean {
    val kind = eventKind(ev)
    return isTestKind(kind) || kind == EventKind.HOLIDAY
}

fun prominentKind(kinds: List<EventKind>): EventKind =
    EventKind.entries.firstOrNull { it in kinds } ?: EventKind.OTHER