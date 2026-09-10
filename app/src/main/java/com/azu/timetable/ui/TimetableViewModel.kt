package com.azu.timetable.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.azu.timetable.BuildConfig
import com.azu.timetable.data.database.AppDatabase
import com.azu.timetable.data.model.CalendarEvent
import com.azu.timetable.data.model.DateOverride
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot
import com.azu.timetable.data.repository.TimetableRepository
import com.azu.timetable.notification.NotificationHelper
import com.azu.timetable.ui.theme.ThemeMode
import com.azu.timetable.util.Versions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Immutable
data class CurrentScheduleStatus(
    val currentSlot: TimetableSlot? = null,
    val nextSlot: TimetableSlot? = null,
    val isToday: Boolean = false,
    val timeRemainingMinutes: Int = 0,
    val progressPercent: Float = 0f,
    val statusMessage: String = ""
)

@Immutable
data class SunsetNotice(
    val minVersion: String,
    val message: String
)

@Immutable
data class AppUpdate(
    val latestVersion: String,
    val url: String,
    val tag: String
)

class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimetableRepository
    private val prefs = application.getSharedPreferences("timetable_prefs", Context.MODE_PRIVATE)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun consumeToast() {
        _toast.value = null
    }

    companion object {
        private val SERVER_BASE = BuildConfig.SERVER_BASE
        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    val todayDayOfWeek: Int
        get() = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

    private val _selectedDay = MutableStateFlow(todayDayOfWeek)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appThemeColor = MutableStateFlow(
        prefs.getString("app_theme_color", "Dynamic") ?: "Dynamic"
    )
    val appThemeColor: StateFlow<String> = _appThemeColor.asStateFlow()

    private val _use24HourFormat = MutableStateFlow(
        prefs.getBoolean("use_24_hour_format", false)
    )
    val use24HourFormat: StateFlow<Boolean> = _use24HourFormat.asStateFlow()

    private val _calendarGridMode = MutableStateFlow(
        prefs.getBoolean("calendar_grid_mode", false)
    )
    val calendarGridMode: StateFlow<Boolean> = _calendarGridMode.asStateFlow()

    fun setCalendarGridMode(grid: Boolean) {
        if (_calendarGridMode.value == grid) return
        _calendarGridMode.value = grid
        prefs.edit().putBoolean("calendar_grid_mode", grid).apply()
    }

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean("notifications_enabled", true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _leadMinutes = MutableStateFlow(
        prefs.getInt("notification_lead_minutes", 10)
    )
    val leadMinutes: StateFlow<Int> = _leadMinutes.asStateFlow()

    private val _currentTime = MutableStateFlow(System.currentTimeMillis())

    private val hasChosenClass = prefs.contains("selected_class")
    private val _selectedClass = MutableStateFlow(
        prefs.getString("selected_class", "A") ?: "A"
    )
    val selectedClass: StateFlow<String> = _selectedClass.asStateFlow()

    // On first boot no class has been chosen yet, so show the selection dialog.
    private val _showClassSelection = MutableStateFlow(!hasChosenClass)
    val showClassSelection: StateFlow<Boolean> = _showClassSelection.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TimetableRepository(database.timetableDao(), database.dateOverrideDao(), database.calendarEventDao())

        viewModelScope.launch {
            repository.populateDefaultsIfNeeded()
            scheduleAllActiveAlarms()
            syncTimetableFromServer(_selectedClass.value)
            syncOverridesFromServer(_selectedClass.value)
            syncCalendarFromServer(_selectedClass.value)
        }

        // Ticker: emit a new "now" only when the minute-of-day changes,
        // so status recompositions happen once per minute instead of every 15s.
        viewModelScope.launch {
            var lastMinuteOfDay = -1
            while (true) {
                val cal = Calendar.getInstance()
                val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                if (minuteOfDay != lastMinuteOfDay) {
                    lastMinuteOfDay = minuteOfDay
                    _currentTime.value = System.currentTimeMillis()
                }
                delay(1_000)
            }
        }
    }

    val allSlots: StateFlow<List<TimetableSlot>> = repository.allSlots
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val overrides: StateFlow<List<DateOverride>> = repository.allOverrides
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val calendarEvents: StateFlow<List<CalendarEvent>> = repository.allCalendarEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentStatus: StateFlow<CurrentScheduleStatus> = combine(
        allSlots,
        _currentTime,
        overrides,
        calendarEvents,
        _selectedClass
    ) { slots, currentTimeMillis, ovr, ev, sectionId ->
        calculateCurrentStatus(slots, todayDayOfWeek, currentTimeMillis, ovr, ev, sectionId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CurrentScheduleStatus()
    )

    // Only the active slot id changes on each tick, so slot highlight
    // recompositions stay isolated from the full schedule status.
    val activeSlotId: StateFlow<Long?> = currentStatus
        .map { it.currentSlot?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sunset = MutableStateFlow<SunsetNotice?>(null)
    val sunset: StateFlow<SunsetNotice?> = _sunset.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdate?>(null)
    val updateInfo: StateFlow<AppUpdate?> = _updateInfo.asStateFlow()

    fun setSelectedDay(day: Int) {
        if (day in 1..7) {
            _selectedDay.value = day
        }
    }

    fun setShowClassSelection(show: Boolean) {
        _showClassSelection.value = show
    }

    fun selectClass(sectionId: String) {
        _selectedClass.value = sectionId
        prefs.edit().putString("selected_class", sectionId).apply()
        _showClassSelection.value = false

        viewModelScope.launch {
            loadTimetableFromJson(sectionId)
            syncTimetableFromServer(sectionId)
            syncOverridesFromServer(sectionId)
            syncCalendarFromServer(sectionId)
        }
    }

    private suspend fun loadTimetableFromJson(sectionId: String) {
        val newSlots = withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.assets.open("timetable.json")
                val jsonString = InputStreamReader(inputStream).use { it.readText() }
                val sections = JSONObject(jsonString).getJSONArray("sections")
                var targetSection: JSONObject? = null
                for (i in 0 until sections.length()) {
                    val sec = sections.getJSONObject(i)
                    if (sec.getString("sectionId").equals(sectionId, ignoreCase = true)) {
                        targetSection = sec
                        break
                    }
                }
                if (targetSection != null) parseSectionToSlots(targetSection) else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        if (newSlots.isEmpty()) return
        cancelAllAlarms()
        repository.resetAndInsert(newSlots)
        if (_notificationsEnabled.value) {
            scheduleAllActiveAlarms()
        }
    }

    private fun parseSectionToSlots(section: JSONObject): List<TimetableSlot> {
        val weekly = section.getJSONObject("weeklyTimetable")
        val newSlots = mutableListOf<TimetableSlot>()

        val daysMap = mapOf(
            "Monday" to 1, "Tuesday" to 2, "Wednesday" to 3,
            "Thursday" to 4, "Friday" to 5, "Saturday" to 6
        )

        for ((dayName, dayNum) in daysMap) {
            if (weekly.has(dayName)) {
                val dayArray = weekly.getJSONArray(dayName)
                for (i in 0 until dayArray.length()) {
                    val item = dayArray.getJSONObject(i)
                    val period = item.optInt("period", 0)
                    val timeStr = item.optString("time", "00:00-00:00")
                    val times = timeStr.split("-")
                    val start = if (times.isNotEmpty()) times[0] else ""
                    val end = if (times.size > 1) times[1] else ""

                    val cellType = item.optString("cellType", "theory")
                    val type = when (cellType) {
                        "laboratory" -> SlotType.LAB
                        "break" -> SlotType.BREAK
                        "activity" -> {
                            val act = item.optString("activity", "").lowercase()
                            if (act.contains("mentor")) SlotType.MENTORING else SlotType.ACTIVITY
                        }
                        else -> SlotType.CLASS
                    }

                    val title = item.optString("courseName", item.optString("activity", "Class"))
                    val courseCode = item.optString("courseCode", "")
                    val room = if (!item.isNull("room")) item.optString("room", "") else ""
                    val faculty = item.optString("inCharge", "")

                    newSlots.add(
                        TimetableSlot(
                            dayOfWeek = dayNum,
                            periodNumber = period,
                            title = title,
                            courseCode = courseCode,
                            faculty = faculty,
                            venue = room,
                            startTime = start,
                            endTime = end,
                            slotType = type
                        )
                    )
                }
            }
        }
        return newSlots
    }

    private suspend fun fetchJson(url: String): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        null
                    } else {
                        resp.body?.string()?.let { JSONObject(it) }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

    private suspend fun syncTimetableFromServer(sectionId: String) {
        try {
            val json = fetchJson("$SERVER_BASE/api/v1/db/timetable?class=$sectionId")
                ?: run {
                    _toast.value = "Failed to update timetable"
                    return
                }
            val version = json.optString("version", "")
            val storedVersion = prefs.getString("timetable_version_$sectionId", null)
            if (version.isNotEmpty() && version != storedVersion && json.has("section")) {
                val newSlots = withContext(Dispatchers.IO) {
                    parseSectionToSlots(json.getJSONObject("section"))
                }
                val overrides = withContext(Dispatchers.IO) {
                    parseOverrides(json.optJSONArray("overrides") ?: JSONArray())
                }
                cancelAllAlarms()
                repository.resetAndInsert(newSlots)
                repository.resetOverrides(overrides)
                prefs.edit().putString("timetable_version_$sectionId", version).apply()
                if (_notificationsEnabled.value) {
                    scheduleAllActiveAlarms()
                }
            }
            checkClientSunset(json)
        } catch (e: Exception) {
            _toast.value = "Failed to update timetable"
        }
    }

    private fun checkClientSunset(json: JSONObject) {
        if (_sunset.value != null) return
        val minVersion = json.optString("min_client_version", "").trim()
        val message = json.optString("sunset_message", "").trim()
        if (minVersion.isNotEmpty() && message.isNotEmpty() &&
            Versions.compare(BuildConfig.VERSION_NAME, minVersion) < 0
        ) {
            _sunset.value = SunsetNotice(minVersion, message)
        }
    }

    fun dismissSunset() {
        _sunset.value = null
    }

    private fun parseOverrides(array: JSONArray): List<DateOverride> {
        val parsed = mutableListOf<DateOverride>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            parsed += DateOverride(
                overrideDate = o.optString("override_date", ""),
                sectionId = o.optString("section_id", ""),
                period = if (o.isNull("period")) null else o.optInt("period"),
                cellType = o.optString("cell_type", ""),
                courseCode = o.optString("course_code", ""),
                activity = o.optString("activity", ""),
                room = o.optString("room", ""),
                isCancelled = o.optInt("is_cancelled", 0) == 1,
                reason = o.optString("reason", "")
            )
        }
        return parsed
    }

    private suspend fun syncOverridesFromServer(sectionId: String) {
        try {
            val json = fetchJson("$SERVER_BASE/api/v1/overrides") ?: return
            val array = json.optJSONArray("overrides") ?: JSONArray()
            val parsed = withContext(Dispatchers.IO) { parseOverrides(array) }
            repository.resetOverrides(parsed)
        } catch (e: Exception) {
            // Overrides are optional; a DB outage must never break the app.
        }
    }

    private suspend fun syncCalendarFromServer(sectionId: String) {
        try {
            val json = fetchJson("$SERVER_BASE/api/v1/calendar") ?: run {
                loadCalendarFromAsset()
                return
            }
            val parsed = withContext(Dispatchers.IO) {
                parseCalendarEvents(json.optJSONArray("events") ?: JSONArray())
            }
            repository.resetCalendarEvents(parsed)
        } catch (e: Exception) {
            loadCalendarFromAsset()
        }
    }

    private suspend fun loadCalendarFromAsset() {
        val events = withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.assets.open("calendar.json")
                val jsonString = InputStreamReader(inputStream).use { it.readText() }
                parseCalendarEvents(JSONArray(jsonString))
            } catch (e: Exception) {
                emptyList()
            }
        }
        repository.resetCalendarEvents(events)
    }

    private fun parseCalendarEvents(array: JSONArray): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val dates = parseCalendarDates(o.optString("date", o.optString("Date", "")))
            if (dates.isEmpty()) continue
            val details = o.optString("details", o.optString("Details", ""))
            val holiday = o.optBoolean("holiday", false) || isHolidayText(details)
            val day = o.optString("day", o.optString("Day", ""))
            for (date in dates) {
                out += CalendarEvent(date = date, day = day, details = details, isHoliday = holiday)
            }
        }
        return out.sortedBy { it.date }
    }

    private fun parseCalendarDates(dateStr: String): List<String> {
        if (dateStr.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val dotFmt = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{2})$""")
        val isoFmt = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
        for (part in dateStr.split("&")) {
            val p = part.trim()
            dotFmt.find(p)?.let { m ->
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val yy = m.groupValues[3].toInt()
                val year = if (yy < 100) 2000 + yy else yy
                out += String.format(Locale.US, "%04d-%02d-%02d", year, mo, d)
                return@let
            }
            isoFmt.find(p)?.let { m ->
                out += String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt()
                )
            }
        }
        return out
    }

    private fun isHolidayText(details: String): Boolean {
        val lower = details.lowercase(Locale.US)
        return listOf("holiday", "no class work", "pooja").any { lower.contains(it) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setAppThemeColor(color: String) {
        _appThemeColor.value = color
        prefs.edit().putString("app_theme_color", color).apply()
    }

    fun updateColorForSubject(subject: String, color: Long) {
        viewModelScope.launch {
            val slotsToUpdate = allSlots.value.filter { it.title == subject }
            slotsToUpdate.forEach { slot ->
                repository.updateSlot(slot.copy(color = color))
            }
        }
    }

    fun setUse24HourFormat(use24h: Boolean) {
        _use24HourFormat.value = use24h
        prefs.edit().putBoolean("use_24_hour_format", use24h).apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        viewModelScope.launch {
            if (enabled) {
                scheduleAllActiveAlarms()
            } else {
                cancelAllAlarms()
            }
        }
    }

    fun setLeadMinutes(minutes: Int) {
        _leadMinutes.value = minutes
        prefs.edit().putInt("notification_lead_minutes", minutes).apply()
        viewModelScope.launch {
            if (_notificationsEnabled.value) {
                scheduleAllActiveAlarms()
            }
        }
    }

    fun addSlot(slot: TimetableSlot) {
        viewModelScope.launch {
            val id = repository.insertSlot(slot)
            if (_notificationsEnabled.value && slot.isNotificationEnabled) {
                NotificationHelper.scheduleSlotReminder(
                    getApplication(),
                    slot.copy(id = id),
                    _leadMinutes.value
                )
            }
        }
    }

    fun updateSlot(slot: TimetableSlot) {
        viewModelScope.launch {
            repository.updateSlot(slot)
            if (_notificationsEnabled.value && slot.isNotificationEnabled) {
                NotificationHelper.scheduleSlotReminder(
                    getApplication(),
                    slot,
                    _leadMinutes.value
                )
            } else {
                NotificationHelper.cancelSlotReminder(getApplication(), slot.id)
            }
        }
    }

    fun deleteSlot(slot: TimetableSlot) {
        viewModelScope.launch {
            NotificationHelper.cancelSlotReminder(getApplication(), slot.id)
            repository.deleteSlot(slot)
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            cancelAllAlarms()
            repository.resetToDefaultSchedule()
            scheduleAllActiveAlarms()
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            syncTimetableFromServer(_selectedClass.value)
            syncOverridesFromServer(_selectedClass.value)
            syncCalendarFromServer(_selectedClass.value)
        }
    }

    fun checkForUpdate() {
        if (_updateInfo.value != null) return
        viewModelScope.launch {
            val json = fetchJson("https://api.github.com/repos/ThePurpleSaints/timetable/releases/latest")
            if (json == null) return@launch
            val tag = json.optString("tag_name", "").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isEmpty()) return@launch
            val latest = json.optString("name", version).trim().ifEmpty { version }
            if (Versions.compare(BuildConfig.VERSION_NAME, latest) >= 0) return@launch
            if (prefs.getBoolean("skip_update_$tag", false)) return@launch
            _updateInfo.value = AppUpdate(
                latestVersion = latest,
                url = json.optString("html_url", "https://github.com/ThePurpleSaints/timetable/releases"),
                tag = tag
            )
        }
    }

    fun dismissUpdate(tag: String) {
        prefs.edit().putBoolean("skip_update_$tag", true).apply()
        _updateInfo.value = null
    }

    fun closeUpdate() {
        _updateInfo.value = null
    }

    fun sendImmediateNextClassNotification() {
        val nowCal = Calendar.getInstance()
        val currentDay = todayDayOfWeek
        val currentMinutesOfDay = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        val todaySlots = allSlots.value
            .filter { it.dayOfWeek == currentDay }
            .sortedBy { it.startTime }

        // Find upcoming class today or first class of tomorrow
        val nextClassToday = todaySlots.firstOrNull { slot ->
            val startMin = parseTimeToMinutes(slot.startTime)
            startMin >= currentMinutesOfDay
        } ?: todaySlots.firstOrNull()

        if (nextClassToday != null) {
            NotificationHelper.showClassNotification(
                context = getApplication(),
                title = nextClassToday.title,
                venue = nextClassToday.venue,
                time = "${nextClassToday.startTime} - ${nextClassToday.endTime}",
                faculty = nextClassToday.faculty
            )
        } else {
            NotificationHelper.showClassNotification(
                context = getApplication(),
                title = "No More Classes Today",
                venue = "Enjoy your evening!",
                time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                faculty = "See you tomorrow"
            )
        }
    }

    private suspend fun scheduleAllActiveAlarms() {
        val slots = allSlots.value
        val context = getApplication<Application>()
        for (slot in slots) {
            if (slot.isNotificationEnabled && slot.slotType != SlotType.BREAK) {
                NotificationHelper.scheduleSlotReminder(context, slot, _leadMinutes.value)
            }
        }
    }

    private suspend fun cancelAllAlarms() {
        val slots = allSlots.value
        val context = getApplication<Application>()
        for (slot in slots) {
            NotificationHelper.cancelSlotReminder(context, slot.id)
        }
    }

    private fun calculateCurrentStatus(
        slots: List<TimetableSlot>,
        selectedDay: Int,
        currentTimeMillis: Long,
        overrides: List<DateOverride> = emptyList(),
        calendarEvents: List<CalendarEvent> = emptyList(),
        sectionId: String = _selectedClass.value
    ): CurrentScheduleStatus {
        val isToday = (selectedDay == todayDayOfWeek)

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(currentTimeMillis))
        val todayOverrides = overrides.filter {
            it.overrideDate == todayIso && it.sectionId.equals(sectionId, ignoreCase = true)
        }
        val holidayOverride = todayOverrides.firstOrNull { it.isCancelled && it.period == null }
        val overrideNotes = mutableListOf<String>()
        for (ov in todayOverrides) {
            when {
                ov.isCancelled && ov.period == null -> Unit
                ov.isCancelled -> overrideNotes.add(
                    (ov.courseCode.ifBlank { "A lesson" }) + " cancelled" +
                        (ov.reason.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                )
                ov.period == null -> overrideNotes.add(ov.activity.ifBlank { "Special schedule today" })
                else -> {
                    val roomNote = ov.room.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: ""
                    overrideNotes.add("P${ov.period} → ${ov.courseCode.ifBlank { ov.activity }}$roomNote")
                }
            }
        }
        val daySlots = slots.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startTime }

        if (daySlots.isEmpty()) {
            return CurrentScheduleStatus(
                isToday = isToday,
                statusMessage = if (isToday) "No lessons scheduled for today" else "No lessons scheduled"
            )
        }

        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentMinutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        var activeSlot: TimetableSlot? = null
        var nextSlot: TimetableSlot? = null
        var remainingMinutes = 0
        var progressPercent = 0f

        for (slot in daySlots) {
            val start = parseTimeToMinutes(slot.startTime)
            val end = parseTimeToMinutes(slot.endTime)

            if (currentMinutesOfDay in start until end) {
                activeSlot = slot
                val totalDuration = (end - start).coerceAtLeast(1)
                val elapsed = currentMinutesOfDay - start
                progressPercent = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                remainingMinutes = end - currentMinutesOfDay
                break
            } else if (start > currentMinutesOfDay && nextSlot == null) {
                nextSlot = slot
            }
        }

        if (activeSlot == null && nextSlot == null && daySlots.isNotEmpty()) {
            val firstSlotStart = parseTimeToMinutes(daySlots.first().startTime)
            if (currentMinutesOfDay < firstSlotStart) {
                nextSlot = daySlots.first()
            }
        }

        val message = when {
            !isToday -> "Viewing schedule for selected day"
            activeSlot != null -> {
                if (activeSlot.slotType == SlotType.BREAK) {
                    "Break in progress • Ends in ${remainingMinutes}m"
                } else {
                    "Ongoing: ${activeSlot.title} • Ends in ${remainingMinutes}m"
                }
            }
            nextSlot != null -> {
                val startMin = parseTimeToMinutes(nextSlot.startTime)
                val minsUntil = (startMin - currentMinutesOfDay).coerceAtLeast(0)
                if (minsUntil <= 60) {
                    "Up Next: ${nextSlot.title} in ${minsUntil}m"
                } else {
                    "Next class starts at ${nextSlot.startTime}"
                }
            }
            else -> "Today's lessons are finished"
        }

        val calendarHoliday = calendarEvents.firstOrNull { it.date == todayIso && it.isHoliday }
        val statusMessage = when {
            holidayOverride != null -> "No lessons today • ${holidayOverride.reason.ifBlank { "Holiday" }}"
            calendarHoliday != null -> "No lessons today • ${calendarHoliday.details}"
            overrideNotes.isEmpty() -> message
            else -> "$message • ${overrideNotes.joinToString(" | ")}"
        }

        return CurrentScheduleStatus(
            currentSlot = activeSlot,
            nextSlot = nextSlot,
            isToday = isToday,
            timeRemainingMinutes = remainingMinutes,
            progressPercent = progressPercent,
            statusMessage = statusMessage
        )
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun formatTime(timeStr: String, use24h: Boolean): String {
        if (use24h) return timeStr
        try {
            val parts = timeStr.split(":")
            if (parts.size < 2) return timeStr
            var hour = parts[0].toInt()
            val minute = parts[1]
            val amPm = if (hour >= 12) "PM" else "AM"
            if (hour > 12) hour -= 12
            if (hour == 0) hour = 12
            return String.format(Locale.getDefault(), "%02d:%s %s", hour, minute, amPm)
        } catch (e: Exception) {
            return timeStr
        }
    }
}
