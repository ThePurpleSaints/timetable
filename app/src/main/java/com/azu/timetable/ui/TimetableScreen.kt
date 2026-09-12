package com.azu.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.azu.timetable.data.model.CalendarEvent
import com.azu.timetable.data.model.DateOverride
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot
import com.azu.timetable.notification.NotificationHelper
import com.azu.timetable.ui.components.AddEditSlotDialog
import com.azu.timetable.ui.components.BreakCard
import com.azu.timetable.ui.components.CalendarScreen
import com.azu.timetable.ui.components.CurrentStatusCard
import com.azu.timetable.ui.components.SlotCard
import com.azu.timetable.ui.components.TodayEventBanner
import com.azu.timetable.ui.components.isHighlightEvent
import com.azu.timetable.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Immutable
data class DayTabItem(
    val dayNumber: Int, // 1..7
    val shortName: String,
    val fullName: String
)

val DayTabs = listOf(
    DayTabItem(1, "Mon", "Monday"),
    DayTabItem(2, "Tue", "Tuesday"),
    DayTabItem(3, "Wed", "Wednesday"),
    DayTabItem(4, "Thu", "Thursday"),
    DayTabItem(5, "Fri", "Friday"),
    DayTabItem(6, "Sat", "Saturday"),
    DayTabItem(7, "Sun", "Sunday")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allSlots by viewModel.allSlots.collectAsState()
    val activeSlotId by viewModel.activeSlotId.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val leadMinutes by viewModel.leadMinutes.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val use24HourFormat by viewModel.use24HourFormat.collectAsState()
    val calendarGridMode by viewModel.calendarGridMode.collectAsState()
    val showClassSelection by viewModel.showClassSelection.collectAsState()
    val classDirectory by viewModel.classDirectory.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    val toastMessage by viewModel.toast.collectAsState()
    val overrides by viewModel.overrides.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val sunsetNotice by viewModel.sunset.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage, snackbarHostState) {
        val message = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeToast()
    }

    val todayDay = viewModel.todayDayOfWeek
    val formattedCurrentDate = remember {
        SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date()).uppercase()
    }

    // Class label from the server directory (e.g. "II CSE A"), falling back to
    // the stored section id while the directory is still loading.
    val classLabel = remember(classDirectory, selectedClass) {
        classDirectory?.classes
            ?.firstOrNull { it.sectionId == selectedClass }
            ?.sectionName
            ?: selectedClass
    }

    // Precompute slots grouped by day to eliminate allocations during scrolling
    val slotsByDay = remember(allSlots) {
        allSlots.groupBy { it.dayOfWeek }.mapValues { (_, list) ->
            list.sortedBy { it.startTime }
        }
    }

    // Weekdays 1..7 (Mon first) that have academic events this week (for day capsule tint)
    val eventDaySet = remember(calendarEvents, todayDay) {
        val cal = Calendar.getInstance()
        val sinceMon = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -sinceMon)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val eventsByDate = calendarEvents.filter { isHighlightEvent(it) }.map { it.date }.toSet()
        val set = mutableSetOf<Int>()
        val dayMillis = 24L * 60 * 60 * 1000
        for (offset in 0..6) {
            val iso = fmt.format(Date(cal.timeInMillis + offset * dayMillis))
            if (eventsByDate.contains(iso)) set += offset + 1
        }
        set
    }

    val todayEvents = remember(calendarEvents) {
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        calendarEvents.filter { it.date == iso }
    }

    // Pager state (0..6 representing Mon..Sun) initialized to today
    val pagerState = rememberPagerState(
        initialPage = (todayDay - 1).coerceIn(0, 6),
        pageCount = { 7 }
    )

    // Navigation and screen states
    var currentDestination by remember { mutableStateOf("Pager") }
    val displayPageIndex by remember {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt().coerceIn(0, 6)
        }
    }
    val selectedDay by remember { derivedStateOf { displayPageIndex + 1 } }
    val isTodaySelected by remember { derivedStateOf { selectedDay == todayDay && currentDestination == "Pager" } }

    // Dialog states
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<TimetableSlot?>(null) }
    var slotToDelete by remember { mutableStateOf<TimetableSlot?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var scheduleResetSignal by remember { mutableStateOf(0) }

    // True while a day-detail sheet is open in the calendar; that sheet
    // handles back presses itself, so the global handler stays out of the way.
    var calendarDetailOpen by remember { mutableStateOf(false) }

    val isOnTodayView = currentDestination == "Pager" && selectedDay == todayDay

    // Back on any page returns to Today; the handler lets the system back
    // press fall through only when we are already on the Today page.
    BackHandler(enabled = !calendarDetailOpen && !isOnTodayView) {
        coroutineScope.launch {
            if (currentDestination != "Pager") {
                currentDestination = "Pager"
            }
            pagerState.animateScrollToPage(todayDay - 1, animationSpec = tween(durationMillis = 200))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "$formattedCurrentDate • $classLabel",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        val currentDayName = when (currentDestination) {
                            "Calendar" -> "Calendar"
                            "Settings" -> ""
                            else -> DayTabs.getOrNull(selectedDay - 1)?.fullName ?: "Schedule"
                        }
                        if (currentDayName.isNotEmpty()) {
                            Text(
                                text = currentDayName,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    // Notification Settings button
                    IconButton(
                        onClick = { currentDestination = "Settings" },
                        modifier = Modifier.testTag("notification_button")
                    ) {
                        Icon(
                            imageVector = if (notificationsEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "Notification Settings",
                            tint = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Overflow menu
                    Box {
                        IconButton(
                            onClick = { showTopMenu = true },
                            modifier = Modifier.testTag("top_overflow_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Jump to Today") },
                                leadingIcon = { Icon(Icons.Default.Today, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(todayDay - 1, animationSpec = tween(durationMillis = 150))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change Class") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.setShowClassSelection(true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.refreshAll()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Elegant Dark Navigation Bar (Natural height + System Window Insets)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                // Classes Item
                NavigationBarItem(
                    selected = isTodaySelected,
                    onClick = {
                        currentDestination = "Pager"
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(todayDay - 1, animationSpec = tween(durationMillis = 150))
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isTodaySelected) Icons.Filled.School else Icons.Outlined.School,
                            contentDescription = "Classes"
                        )
                    },
                    label = {
                        Text(
                            text = "Classes",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Calendar Item
                NavigationBarItem(
                    selected = currentDestination == "Calendar",
                    onClick = {
                        currentDestination = "Calendar"
                        scheduleResetSignal++
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentDestination == "Calendar") Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = "Schedule"
                        )
                    },
                    label = {
                        Text(
                            text = "Schedule",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Settings Item
                NavigationBarItem(
                    selected = currentDestination == "Settings",
                    onClick = { currentDestination = "Settings" },
                    icon = {
                        Icon(
                            imageVector = if (currentDestination == "Settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        floatingActionButton = {
            if (currentDestination == "Pager") {
                FloatingActionButton(
                    onClick = {
                        editingSlot = null
                        showAddEditDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_slot_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Class")
                        Text("Add Slot", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentDestination == "Settings") {
                com.azu.timetable.ui.components.SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (currentDestination == "Calendar") {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshAll() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    CalendarScreen(
                        calendarEvents = calendarEvents,
                        slotsByDay = slotsByDay,
                        overrides = overrides,
                        sectionId = selectedClass,
                        use24h = use24HourFormat,
                        gridMode = calendarGridMode,
                        onGridModeChange = viewModel::setCalendarGridMode,
                        resetSignal = scheduleResetSignal,
                        onDetailChange = { open -> calendarDetailOpen = open },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Day selector capsules bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(DayTabs) { index, dayTab ->
                        val isSelected = selectedDay == dayTab.dayNumber
                        val isCurrentDay = todayDay == dayTab.dayNumber
                        val dayHasEvent = eventDaySet.contains(dayTab.dayNumber)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                dayHasEvent -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = when {
                                isSelected -> null
                                dayHasEvent -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index, animationSpec = tween(durationMillis = 200))
                                    }
                                }
                                .testTag("day_tab_${dayTab.shortName}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = dayTab.shortName,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (isCurrentDay) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(5.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }

                // High-performance Swipeable HorizontalPager
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshAll() },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("day_pager_pull_refresh")
                ) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 2,
                        key = { it },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("day_horizontal_pager")
                    ) { page ->
                        val dayNumber = page + 1
                        val isTodayPage = (dayNumber == todayDay)
                        val daySlots = slotsByDay[dayNumber] ?: emptyList()

                        DaySlotList(
                            dayName = DayTabs[page].fullName,
                            dayNumber = dayNumber,
                            daySlots = daySlots,
                            todayEvents = todayEvents,
                            isTodayPage = isTodayPage,
                            use24h = use24HourFormat,
                            statusFlow = viewModel.currentStatus,
                            activeSlotId = activeSlotId,
                            onEditSlot = {
                                editingSlot = it
                                showAddEditDialog = true
                            },
                            onDeleteSlot = {
                                slotToDelete = it
                            },
                            onToggleNotification = { targetSlot ->
                                viewModel.updateSlot(
                                    targetSlot.copy(isNotificationEnabled = !targetSlot.isNotificationEnabled)
                                )
                            },
                            onTestNotification = { targetSlot ->
                                NotificationHelper.showClassNotification(
                                    context = context,
                                    title = targetSlot.title,
                                    venue = targetSlot.venue,
                                    time = "${targetSlot.startTime} - ${targetSlot.endTime}",
                                    faculty = targetSlot.faculty
                                )
                            },
                            onSendImmediateNextNotification = {
                                viewModel.sendImmediateNextClassNotification()
                            },
                            onAddNewSlot = {
                                editingSlot = null
                                showAddEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        AddEditSlotDialog(
            initialSlot = editingSlot,
            defaultDay = selectedDay,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedSlot, applyColorToAll ->
                showAddEditDialog = false
                if (editingSlot == null) {
                    viewModel.addSlot(savedSlot)
                } else {
                    viewModel.updateSlot(savedSlot)
                }
                if (applyColorToAll && savedSlot.color != null) {
                    viewModel.updateColorForSubject(savedSlot.title, savedSlot.color)
                }
            }
        )
    }

    // Confirm Delete Dialog
    slotToDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { slotToDelete = null },
            title = { Text("Delete Class?") },
            text = { Text("Are you sure you want to remove \"${slot.title}\" (${slot.startTime} - ${slot.endTime})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSlot(slot)
                        slotToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { slotToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Reset Schedule Dialog — moved to Settings screen
    if (showClassSelection) {
        com.azu.timetable.ui.components.ClassSelectionDialog(
            directory = classDirectory,
            initialSection = selectedClass,
            onDismiss = { viewModel.setShowClassSelection(false) },
            onClassSelected = {
                viewModel.selectClass(it)
            }
        )
    }

    // Server-driven sunset notice: this app version is below the minimum
    // the server supports, so the server decides what to tell the user.
    sunsetNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSunset() },
            title = { Text("Important") },
            text = { Text(notice.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSunset() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun DaySlotList(
    dayName: String,
    dayNumber: Int,
    daySlots: List<TimetableSlot>,
    todayEvents: List<CalendarEvent>,
    isTodayPage: Boolean,
    use24h: Boolean,
    statusFlow: StateFlow<CurrentScheduleStatus>,
    activeSlotId: Long?,
    onEditSlot: (TimetableSlot) -> Unit,
    onDeleteSlot: (TimetableSlot) -> Unit,
    onToggleNotification: (TimetableSlot) -> Unit,
    onTestNotification: (TimetableSlot) -> Unit,
    onSendImmediateNextNotification: () -> Unit,
    onAddNewSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 92.dp, top = 4.dp)
    ) {
        // Show Current Live Status banner on Today's page
        if (isTodayPage) {
            item(key = "event_banner") {
                TodayEventBanner(events = todayEvents)
            }
            item(key = "status_banner") {
                CurrentStatusCard(
                    statusFlow = statusFlow,
                    onSendNextNotification = onSendImmediateNextNotification
                )
            }
        }

        if (daySlots.isEmpty()) {
            item(key = "empty_state_$dayNumber") {
                EmptyDayView(
                    dayName = dayName,
                    onAddSlot = onAddNewSlot
                )
            }
        } else {
            items(
                items = daySlots,
                key = { it.id },
                contentType = { it.slotType }
            ) { slot ->
                val isSlotActive = isTodayPage && activeSlotId == slot.id

                if (slot.slotType == SlotType.BREAK) {
                    BreakCard(
                        slot = slot,
                        isCurrentActive = isSlotActive,
                        use24h = use24h,
                        onEdit = onEditSlot,
                        onDelete = onDeleteSlot
                    )
                } else {
                    SlotCard(
                        slot = slot,
                        isCurrentActive = isSlotActive,
                        use24h = use24h,
                        subjectColor = slot.color ?: 0L,
                        onEdit = onEditSlot,
                        onDelete = onDeleteSlot,
                        onToggleNotification = onToggleNotification,
                        onTestNotification = onTestNotification
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDayView(
    dayName: String,
    onAddSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.EventBusy,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No classes on $dayName",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enjoy your free time or add custom study sessions and activities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = onAddSlot) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add a Class or Break")
        }
    }
}
