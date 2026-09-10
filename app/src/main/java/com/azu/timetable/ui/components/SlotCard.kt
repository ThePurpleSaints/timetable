package com.azu.timetable.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot
import com.azu.timetable.ui.theme.ActivityColorDark
import com.azu.timetable.ui.theme.ActivityColorLight
import com.azu.timetable.ui.theme.ActivityOnColorDark
import com.azu.timetable.ui.theme.ActivityOnColorLight
import com.azu.timetable.ui.theme.LabColorDark
import com.azu.timetable.ui.theme.LabColorLight
import com.azu.timetable.ui.theme.LabOnColorDark
import com.azu.timetable.ui.theme.LabOnColorLight
import com.azu.timetable.ui.theme.MentoringColorDark
import com.azu.timetable.ui.theme.MentoringColorLight
import com.azu.timetable.ui.theme.MentoringOnColorDark
import com.azu.timetable.ui.theme.MentoringOnColorLight

@Composable
fun SlotCard(
    slot: TimetableSlot,
    isCurrentActive: Boolean = false,
    use24h: Boolean = false,
    subjectColor: Long = 0L,
    onEdit: (TimetableSlot) -> Unit,
    onDelete: (TimetableSlot) -> Unit,
    onToggleNotification: (TimetableSlot) -> Unit,
    onTestNotification: (TimetableSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // Accent colors based on slot type (memoized)
    val (typeBadgeBg, typeBadgeText, typeIcon) = remember(slot.slotType, isDark) {
        when (slot.slotType) {
            SlotType.LAB -> Triple(
                if (isDark) LabColorDark else LabColorLight,
                if (isDark) LabOnColorDark else LabOnColorLight,
                Icons.Default.Biotech
            )
            SlotType.ACTIVITY -> Triple(
                if (isDark) ActivityColorDark else ActivityColorLight,
                if (isDark) ActivityOnColorDark else ActivityOnColorLight,
                Icons.Default.Groups
            )
            SlotType.MENTORING -> Triple(
                if (isDark) MentoringColorDark else MentoringColorLight,
                if (isDark) MentoringOnColorDark else MentoringOnColorLight,
                Icons.Default.School
            )
            else -> Triple(
                if (isDark) ColorHelper.SecondaryContainerDark else ColorHelper.SecondaryContainerLight,
                if (isDark) ColorHelper.OnSecondaryContainerDark else ColorHelper.OnSecondaryContainerLight,
                Icons.Default.Book
            )
        }
    }

    val formattedStart = remember(slot.startTime, use24h) { formatTimeString(slot.startTime, use24h) }
    val formattedEnd = remember(slot.endTime, use24h) { formatTimeString(slot.endTime, use24h) }

    // Timeline row layout
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("slot_card_${slot.id}"),
        verticalAlignment = Alignment.Top
    ) {
        // Left timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(48.dp)
                .padding(top = 14.dp)
        ) {
            Text(
                text = formattedStart,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Indicator dot
            Box(
                modifier = Modifier
                    .size(if (isCurrentActive) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formattedEnd,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Main Dark Card
        val containerColor = when (subjectColor) {
            1L -> MaterialTheme.colorScheme.primaryContainer
            2L -> MaterialTheme.colorScheme.secondaryContainer
            3L -> MaterialTheme.colorScheme.tertiaryContainer
            4L -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = when (subjectColor) {
            1L -> MaterialTheme.colorScheme.onPrimaryContainer
            2L -> MaterialTheme.colorScheme.onSecondaryContainer
            3L -> MaterialTheme.colorScheme.onTertiaryContainer
            4L -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        val variantColor = when (subjectColor) {
            1L, 2L, 3L, 4L -> contentColor.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = if (isCurrentActive) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            },
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (isCurrentActive) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header Row: Badges and Action Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Category Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = typeBadgeBg,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = typeIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = typeBadgeText
                                    )
                                    Text(
                                        text = when (slot.slotType) {
                                            SlotType.LAB -> "Lab"
                                            SlotType.ACTIVITY -> "Activity"
                                            SlotType.MENTORING -> "Mentoring"
                                            else -> if (slot.periodNumber > 0) "Period ${slot.periodNumber}" else "Lecture"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = typeBadgeText
                                    )
                                }
                            }

                            if (slot.courseCode.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = slot.courseCode,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (isCurrentActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Actions (Notification toggle & Overflow menu)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleNotification(slot) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                val notifColor = if (slot.isNotificationEnabled) {
                                    if (subjectColor > 0L) variantColor else MaterialTheme.colorScheme.primary
                                } else {
                                    if (subjectColor > 0L) variantColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                                }
                                Icon(
                                    imageVector = if (slot.isNotificationEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                    contentDescription = if (slot.isNotificationEnabled) "Mute reminder" else "Enable reminder",
                                    tint = notifColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("slot_menu_button_${slot.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Slot Options",
                                        tint = variantColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Class") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onEdit(slot)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Send Test Notification") },
                                        leadingIcon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onTestNotification(slot)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Class") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onDelete(slot)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title
                    Text(
                        text = slot.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Details: Venue and Faculty
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (slot.venue.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Venue",
                                    modifier = Modifier.size(13.dp),
                                    tint = if (subjectColor > 0L) variantColor else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Room ${slot.venue}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = variantColor
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (slot.faculty.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Faculty",
                                    modifier = Modifier.size(13.dp),
                                    tint = variantColor.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = slot.faculty,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = variantColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private object ColorHelper {
    val SecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFF4A4458)
    val OnSecondaryContainerDark = androidx.compose.ui.graphics.Color(0xFFE8DEF8)
    val SecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFFE8DEF8)
    val OnSecondaryContainerLight = androidx.compose.ui.graphics.Color(0xFF1D192B)
}

fun formatTimeString(timeStr: String, use24h: Boolean): String {
    if (use24h) return timeStr
    try {
        val parts = timeStr.split(":")
        if (parts.size < 2) return timeStr
        var hour = parts[0].toInt()
        val minute = parts[1]
        val amPm = if (hour >= 12) "PM" else "AM"
        if (hour > 12) hour -= 12
        if (hour == 0) hour = 12
        return String.format(java.util.Locale.getDefault(), "%02d:%s %s", hour, minute, amPm)
    } catch (e: Exception) {
        return timeStr
    }
}
