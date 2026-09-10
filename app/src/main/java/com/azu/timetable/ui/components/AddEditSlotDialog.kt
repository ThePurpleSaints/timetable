package com.azu.timetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditSlotDialog(
    initialSlot: TimetableSlot?,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (TimetableSlot, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialSlot?.title ?: "") }
    var courseCode by remember { mutableStateOf(initialSlot?.courseCode ?: "") }
    var faculty by remember { mutableStateOf(initialSlot?.faculty ?: "") }
    var venue by remember { mutableStateOf(initialSlot?.venue ?: "A204") }
    var startTime by remember { mutableStateOf(initialSlot?.startTime ?: "08:00") }
    var endTime by remember { mutableStateOf(initialSlot?.endTime ?: "08:50") }
    var selectedDay by remember { mutableIntStateOf(initialSlot?.dayOfWeek ?: defaultDay) }
    var periodNumber by remember { mutableIntStateOf(initialSlot?.periodNumber ?: 1) }
    var slotType by remember { mutableStateOf(initialSlot?.slotType ?: SlotType.CLASS) }
    var notificationEnabled by remember { mutableStateOf(initialSlot?.isNotificationEnabled ?: true) }
    var selectedColor by remember { mutableStateOf(initialSlot?.color ?: 0L) }
    var applyColorToAll by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSlot == null) "Add Schedule Item" else "Edit Schedule Item",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Day selector chips
                Text(
                    text = "Day of Week",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    days.forEachIndexed { index, dayName ->
                        val dayNum = index + 1
                        FilterChip(
                            selected = selectedDay == dayNum,
                            onClick = { selectedDay = dayNum },
                            label = { Text(dayName) }
                        )
                    }
                }

                // Type selector
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SlotType.values().forEach { type ->
                        FilterChip(
                            selected = slotType == type,
                            onClick = {
                                slotType = type
                                if (type == SlotType.BREAK && title.isBlank()) {
                                    title = "Break"
                                    venue = "Campus"
                                }
                            },
                            label = {
                                Text(
                                    when (type) {
                                        SlotType.CLASS -> "Class"
                                        SlotType.LAB -> "Laboratory"
                                        SlotType.BREAK -> "Break"
                                        SlotType.ACTIVITY -> "Activity"
                                        SlotType.MENTORING -> "Mentoring"
                                    }
                                )
                            }
                        )
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = it.isBlank()
                    },
                    label = { Text(if (slotType == SlotType.BREAK) "Break Name (e.g. Lunch)" else "Subject / Class Title") },
                    isError = titleError,
                    leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slot_title_input"),
                    singleLine = true
                )

                // Course Code and Faculty (if not break)
                if (slotType != SlotType.BREAK) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = courseCode,
                            onValueChange = { courseCode = it },
                            label = { Text("Code (e.g. CS23311)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = if (periodNumber > 0) periodNumber.toString() else "",
                            onValueChange = { periodNumber = it.toIntOrNull() ?: 1 },
                            label = { Text("Period #") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = faculty,
                        onValueChange = { faculty = it },
                        label = { Text("Faculty In-Charge") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Venue
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue / Room") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Timing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (HH:mm)") },
                        leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (HH:mm)") },
                        leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Reminder switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Class Reminder",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Notify before this period starts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { notificationEnabled = it }
                    )
                }

                // Color Picker
                if (slotType != SlotType.BREAK) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Color Theme",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colors = listOf(
                                0L to MaterialTheme.colorScheme.surfaceVariant,
                                1L to MaterialTheme.colorScheme.primaryContainer,
                                2L to MaterialTheme.colorScheme.secondaryContainer,
                                3L to MaterialTheme.colorScheme.tertiaryContainer,
                                4L to MaterialTheme.colorScheme.errorContainer
                            )

                            colors.forEach { (colorId, colorValue) ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorValue)
                                        .clickable { selectedColor = colorId },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColor == colorId) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(20.dp),
                                            tint = if (colorId == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedColor != (initialSlot?.color ?: 0L)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { applyColorToAll = !applyColorToAll }
                            ) {
                                Checkbox(
                                    checked = applyColorToAll,
                                    onCheckedChange = { applyColorToAll = it }
                                )
                                Text(
                                    text = "Apply to all slots with this subject",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val newSlot = (initialSlot ?: TimetableSlot(
                        title = title,
                        startTime = startTime,
                        endTime = endTime,
                        dayOfWeek = selectedDay
                    )).copy(
                        title = title.trim(),
                        courseCode = courseCode.trim(),
                        faculty = faculty.trim(),
                        venue = venue.trim(),
                        startTime = startTime.trim(),
                        endTime = endTime.trim(),
                        dayOfWeek = selectedDay,
                        periodNumber = periodNumber,
                        slotType = slotType,
                        isNotificationEnabled = notificationEnabled,
                        color = if (slotType == SlotType.BREAK) null else selectedColor
                    )
                    onSave(newSlot, applyColorToAll)
                },
                modifier = Modifier.testTag("save_slot_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
