package com.azu.timetable.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azu.timetable.data.model.CalendarEvent
import com.azu.timetable.data.model.DateOverride
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ANCHOR_YEAR = 2026
private const val PAGE_COUNT = 96

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    calendarEvents: List<CalendarEvent>,
    slotsByDay: Map<Int, List<TimetableSlot>>,
    overrides: List<DateOverride>,
    sectionId: String,
    use24h: Boolean,
    gridMode: Boolean,
    onGridModeChange: (Boolean) -> Unit,
    resetSignal: Int = 0,
    onDetailChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedIso by remember { mutableStateOf<String?>(null) }

    val eventsByIso = remember(calendarEvents) { calendarEvents.groupBy { it.date } }

    val now = remember { Calendar.getInstance() }
    val pagerState = rememberPagerState(
        initialPage = monthPage(now.get(Calendar.YEAR), now.get(Calendar.MONTH)),
        pageCount = { PAGE_COUNT }
    )
    val scope = rememberCoroutineScope()

    // Keep the parent screen aware that a day-detail panel is open, so the
    // system back press can close the sheet instead of exiting the app.
    LaunchedEffect(selectedIso) {
        onDetailChange(selectedIso != null)
    }

    // Back while a day detail panel is open closes just the panel.
    BackHandler(enabled = selectedIso != null) {
        selectedIso = null
    }

    // Tap on the bottom "Schedule" tab snaps straight back to the current month.
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            val cal = Calendar.getInstance()
            pagerState.animateScrollToPage(monthPage(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)))
        }
    }

    val (titleYear, titleMonth) = yearMonthOfPage(pagerState.currentPage)
    val monthTitle = remember(titleYear, titleMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
            Calendar.getInstance().apply { clear(); set(titleYear, titleMonth, 1) }.time
        )
    }

    Box(modifier = modifier
        .fillMaxSize()
        .testTag("calendar_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Prev month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedContent(
                    targetState = monthTitle,
                    transitionSpec = {
                        (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(targetOffsetY = { -it / 3 }) + fadeOut(tween(150)))
                    },
                    label = "monthTitle"
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (gridMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("calendar_month_pager")
            ) { page ->
                val (year, month) = yearMonthOfPage(page)
                AnimatedContent(
                    targetState = gridMode,
                    transitionSpec = {
                        fadeIn(tween(durationMillis = 200)) togetherWith fadeOut(tween(durationMillis = 120))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "gridListSwitch"
                ) { isGrid ->
                    if (isGrid) {
                        MonthGrid(
                            year = year,
                            monthZero = month,
                            eventsByIso = eventsByIso,
                            todayIso = todayIso,
                            onClick = { selectedIso = it }
                        )
                    } else {
                        MonthDayList(
                            year = year,
                            monthZero = month,
                            eventsByIso = eventsByIso,
                            todayIso = todayIso,
                            onClick = { selectedIso = it }
                        )
                    }
                }
            }
        }

        Surface(
            onClick = { onGridModeChange(!gridMode) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .size(52.dp)
                .testTag("calendar_view_toggle")
        ) {
            Icon(
                imageVector = if (gridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.CalendarMonth,
                contentDescription = if (gridMode) "List view" else "Grid view",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp)
            )
        }

        AnimatedVisibility(
            visible = selectedIso != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180)),
            modifier = Modifier.fillMaxSize()
        ) {
            val dayWeekDay = weekdayFromIso(selectedIso.orEmpty())
            DayFlyIn(
                iso = selectedIso.orEmpty(),
                events = eventsByIso[selectedIso.orEmpty()] ?: emptyList(),
                daySlots = slotsByDay[dayWeekDay] ?: emptyList(),
                overrides = overrides.filter {
                    it.overrideDate == selectedIso && it.sectionId.equals(sectionId, true)
                },
                use24h = use24h,
                onClose = { selectedIso = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthGrid(
    year: Int,
    monthZero: Int,
    eventsByIso: Map<String, List<CalendarEvent>>,
    todayIso: String,
    onClick: (String) -> Unit
) {
    val monthCal = remember(year, monthZero) {
        Calendar.getInstance().apply {
            clear()
            set(year, monthZero, 1)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (monthCal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0 = Monday first

    val dayCells = buildList<Int?> {
        repeat(lead) { add(null) }
        for (d in 1..daysInMonth) add(d)
        while (size % 7 != 0) add(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        dayCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { dayNum ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp)
                    ) {
                        if (dayNum != null) {
                            val iso = isoFor(year, monthZero, dayNum)
                            CalendarDayCell(
                                dayNum = dayNum,
                                events = eventsByIso[iso] ?: emptyList(),
                                isToday = iso == todayIso,
                                isInPast = iso < todayIso,
                                onClick = { onClick(iso) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Color guide",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EventKind.entries.forEach { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(kindColor(kind))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = labelOf(kind),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNum: Int,
    events: List<CalendarEvent>,
    isToday: Boolean,
    isInPast: Boolean,
    onClick: () -> Unit
) {
    val kinds = remember(events) { events.map(::eventKind).distinct().toList() }
    val prominent = remember(kinds) { prominentKind(kinds) }
    val hasExam = events.any { eventKind(it) == EventKind.EXAM }
    val isSolid = remember(prominent) { isSolidKind(prominent) } || hasExam
    val themeColor = if (hasExam) ExamBlack else kindColor(prominent)
    val isTest = isTestKind(prominent)
    val shape = if (isTest) JaggedShape() else RoundedCornerShape(13.dp)

    val containerColor = when {
        events.isEmpty() && isInPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        events.isEmpty() -> MaterialTheme.colorScheme.surfaceVariant
        isSolid -> themeColor
        else -> themeColor.copy(alpha = 0.18f)
    }
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(durationMillis = 220),
        label = "cellColor"
    )
    val numberColor = when {
        isToday -> { if (isSolid) Color.White else MaterialTheme.colorScheme.primary }
        isInPast && events.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isSolid -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dotColor: (EventKind) -> Color = if (isSolid) { { Color.White } } else { { kindColor(it) } }

    Surface(
        shape = shape,
        color = animatedContainerColor,
        border = when {
            isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            events.isNotEmpty() && !isSolid -> BorderStroke(1.dp, themeColor.copy(alpha = 0.4f))
            else -> null
        },
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$dayNum",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = numberColor
            )
            if (events.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    kinds.take(3).forEach { kind ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(dotColor(kind))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun MonthDayList(
    year: Int,
    monthZero: Int,
    eventsByIso: Map<String, List<CalendarEvent>>,
    todayIso: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthCal = remember(year, monthZero) {
        Calendar.getInstance().apply {
            clear()
            set(year, monthZero, 1)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val monthPrefix = remember(year, monthZero) { isoFor(year, monthZero, 1).take(7) }
    val todayRow = remember(todayIso, monthPrefix, daysInMonth) {
        if (todayIso.startsWith(monthPrefix)) {
            (todayIso.substring(8).toIntOrNull() ?: -1) - 1
        } else {
            -1
        }
    }
    // Start scrolled so the current day sits near the centre of the viewport (~4 rows visible above it).
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (todayRow in 0 until daysInMonth) {
            (todayRow - 4).coerceAtLeast(0)
        } else 0
    )

    LazyColumn(
        modifier = modifier
            .background(Color(0xFF08080B))
            .testTag("calendar_day_list"),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(daysInMonth) { index ->
            val day = index + 1
            val iso = isoFor(year, monthZero, day)
            DayListTile(
                dayNum = day,
                iso = iso,
                events = eventsByIso[iso] ?: emptyList(),
                isToday = iso == todayIso,
                onClick = { onClick(iso) }
            )
        }
    }
}

@Composable
private fun DayListTile(
    dayNum: Int,
    iso: String,
    events: List<CalendarEvent>,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val kinds = remember(events) { events.map(::eventKind).distinct().toList() }
    val prominent = remember(kinds) { prominentKind(kinds) }
    val hasExam = events.any { eventKind(it) == EventKind.EXAM }
    val hasEvents = events.isNotEmpty()
    val isTest = isTestKind(prominent)
    val themeColor = if (hasExam) ExamBlack else kindColor(prominent)
    val fullColor = hasExam || isSolidKind(prominent)

    val tileColor = when {
        fullColor -> themeColor
        hasEvents -> themeColor.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val animatedTileColor by animateColorAsState(
        targetValue = tileColor,
        animationSpec = tween(durationMillis = 220),
        label = "listTileColor"
    )
    val shape = if (isTest) {
        JaggedShape(teeth = 24, verticalTeeth = 12, depth = 14f)
    } else {
        RoundedCornerShape(13.dp)
    }
    val blockShape = if (isTest) JaggedShape(teeth = 7, verticalTeeth = 7) else RoundedCornerShape(14.dp)

    Surface(
        onClick = onClick,
        shape = shape,
        color = animatedTileColor,
        border = if (isToday) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(blockShape)
                    .background(
                        color = if (hasEvents) themeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$dayNum",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (hasEvents) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fullDateFromIso(iso),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (fullColor) Color.White else MaterialTheme.colorScheme.onSurface
                )
                if (events.isEmpty()) {
                    Text(
                        text = "No academic events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    events.take(2).forEach { ev ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (fullColor) Color.White else kindColor(eventKind(ev)))
                            )
                            Text(
                                text = ev.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fullColor) Color.White else kindColor(eventKind(ev)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (events.size > 2) {
                        Text(
                            text = "+${events.size - 2} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fullColor) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isToday) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun DayFlyIn(
    iso: String,
    events: List<CalendarEvent>,
    daySlots: List<TimetableSlot>,
    overrides: List<DateOverride>,
    use24h: Boolean,
    onClose: () -> Unit
) {
    val parsed = parseIso(iso)
    val isHolidayDay = events.any { it.isHoliday }

    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val scrollState = rememberScrollState()
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val scrimAlpha = (1f - (dragOffsetPx / (screenHeightPx * 0.45f))).coerceIn(0f, 1f)

    // Dragging anywhere on the sheet follows the finger: while the content is
    // still scrollable upward it scrolls like normal, but once it hits the top
    // further downward drags slide the whole sheet instead.
    val sheetNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val dy = available.y
                if (dy <= 0f) return Offset.Zero
                val new = minOf(dragOffsetPx + dy, screenHeightPx)
                val step = new - dragOffsetPx
                dragOffsetPx = new
                return Offset(0f, step)
            }
        }
    }

    // On finger-up, settle the sheet: dismiss if dragged past the threshold.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }.collect { inProgress ->
            if (inProgress || dragOffsetPx == 0f) return@collect
            if (dragOffsetPx > screenHeightPx * 0.2f) {
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = screenHeightPx,
                    animationSpec = tween(durationMillis = 180)
                ) { value, _ -> dragOffsetPx = value }
                onClose()
            } else {
                animate(
                    initialValue = dragOffsetPx,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.75f)
                ) { value, _ -> dragOffsetPx = value }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .testTag("calendar_scrim")
        )

        val panelMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = panelMaxHeight)
                .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                .testTag("calendar_day_panel"),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .nestedScroll(sheetNestedScroll)
                    .verticalScroll(scrollState)
                    .padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 44.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (parsed != null) {
                                SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(parsed.time)
                            } else {
                                iso
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isHolidayDay) {
                            Text(
                                text = "Holiday",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = kindColor(EventKind.HOLIDAY)
                            )
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("calendar_close")) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ACADEMIC EVENTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (events.isEmpty()) {
                        Text(
                            text = "No academic events",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        events.forEach { ev ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(kindColor(eventKind(ev)))
                                )
                                Text(
                                    text = ev.details,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = kindColor(eventKind(ev)),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Class Schedule",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                if (daySlots.isEmpty()) {
                    Text(
                        text = if (isHolidayDay) {
                            "No classes — it's a holiday"
                        } else {
                            "No classes scheduled"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                } else {
                    daySlots.forEach { slot ->
                        if (slot.slotType == SlotType.BREAK) {
                            BreakDashDivider()
                        } else {
                            ClassSlotRow(slot = slot, struck = isHolidayDay, use24h = use24h)
                        }
                    }
                }

                if (overrides.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Text(
                        text = "Overrides",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    overrides.forEach { ov ->
                        val label = if (ov.isCancelled) {
                            "Cancelled • ${ov.courseCode.ifBlank { ov.activity }}"
                        } else {
                            ov.period?.let { "P$it • ${ov.courseCode}" } ?: ov.activity
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ov.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (ov.isCancelled) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassSlotRow(slot: TimetableSlot, struck: Boolean, use24h: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatTimeString(slot.startTime, use24h)} - ${formatTimeString(slot.endTime, use24h)} • ${slot.venue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(slotColor(slot.color))
        )
    }
}

@Composable
private fun BreakDashDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        )
        Text(
            text = "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        )
    }
}

private fun monthPage(year: Int, monthZero: Int): Int =
    (year - ANCHOR_YEAR) * 12 + monthZero

private fun yearMonthOfPage(page: Int): Pair<Int, Int> =
    ANCHOR_YEAR + (page / 12) to (page % 12)

private fun isoFor(year: Int, monthZero: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, monthZero + 1, day)

private fun parseIso(iso: String): Calendar? = try {
    val parts = iso.split("-")
    Calendar.getInstance().apply {
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        clear(Calendar.HOUR_OF_DAY)
        clear(Calendar.MINUTE)
        clear(Calendar.SECOND)
        clear(Calendar.MILLISECOND)
    }
} catch (e: Exception) {
    null
}

private fun weekdayFromIso(iso: String): Int =
    (parseIso(iso)?.get(Calendar.DAY_OF_WEEK) ?: 1).let { (it + 5) % 7 + 1 }

private fun fullDateFromIso(iso: String): String {
    val parsed = parseIso(iso) ?: return iso
    return SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(parsed.time)
}

private class JaggedShape(
    private val teeth: Int = 7,
    private val verticalTeeth: Int = 7,
    private val depth: Float = 11f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Generic(path)
        val tw = w / teeth
        val tv = h / verticalTeeth

        // top edge: zigzag teeth pointing up
        var x = 0f
        path.moveTo(0f, depth)
        while (x < w - 1f) {
            val peak = minOf(w, x + tw / 2f)
            val valley = minOf(w, x + tw)
            path.lineTo(peak, 0f)
            path.lineTo(valley, depth)
            x += tw
        }

        // right edge: zigzag teeth pointing right
        var y = depth
        path.lineTo(w, depth)
        while (y < h - 1f) {
            val peak = minOf(h, y + tv / 2f)
            val valley = minOf(h, y + tv)
            path.lineTo(w, peak)
            path.lineTo(w - depth, valley)
            y += tv
        }

        // bottom edge: zigzag teeth pointing down
        x = w
        path.lineTo(w - depth, h)
        while (x > 1f) {
            val peak = maxOf(0f, x - tw / 2f)
            val valley = maxOf(0f, x - tw)
            path.lineTo(peak, h)
            path.lineTo(valley, h - depth)
            x -= tw
        }

        // left edge: zigzag teeth pointing left
        y = h
        path.lineTo(depth, h)
        while (y > 1f) {
            val peak = maxOf(0f, y - tv / 2f)
            val valley = maxOf(0f, y - tv)
            path.lineTo(0f, peak)
            path.lineTo(depth, valley)
            y -= tv
        }

        path.close()
        return Outline.Generic(path)
    }
}

private fun slotColor(colorLong: Long?): Color {
    val value = colorLong ?: 0xFF7B68EE
    return Color(value)
}