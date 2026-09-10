package com.azu.timetable.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azu.timetable.ui.CurrentScheduleStatus
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CurrentStatusCard(
    statusFlow: StateFlow<CurrentScheduleStatus>,
    onSendNextNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by statusFlow.collectAsState()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("current_status_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSlotValue = status.currentSlot
                    val nextSlotValue = status.nextSlot
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                currentSlotValue != null -> "HAPPENING NOW"
                                nextSlotValue != null -> "NEXT UP • ${nextSlotValue.startTime}"
                                else -> "STATUS"
                            },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val activeOrNextTitle = currentSlotValue?.title
                        ?: nextSlotValue?.title
                        ?: status.statusMessage.ifBlank { "Today's lessons are finished" }

                    Text(
                        text = activeOrNextTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val subtitle = when {
                        currentSlotValue != null -> {
                            val v = if (currentSlotValue.venue.isNotBlank()) "Room ${currentSlotValue.venue}" else "On Campus"
                            val f = if (currentSlotValue.faculty.isNotBlank()) " • ${currentSlotValue.faculty}" else ""
                            "$v$f (${status.timeRemainingMinutes}m left)"
                        }
                        nextSlotValue != null -> {
                            val v = if (nextSlotValue.venue.isNotBlank()) "Room ${nextSlotValue.venue}" else "On Campus"
                            val f = if (nextSlotValue.faculty.isNotBlank()) " • ${nextSlotValue.faculty}" else ""
                            "$v$f"
                        }
                        else -> "Semester III • CSE G"
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Elegant circular action badge with notification trigger
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSendNextNotification() }
                        .testTag("send_notification_badge")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                status.currentSlot != null -> Icons.Default.PlayCircle
                                else -> Icons.Default.NotificationsActive
                            },
                            contentDescription = "Trigger Reminder",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (status.currentSlot != null) {
                Spacer(modifier = Modifier.height(14.dp))
                // Progress bar showing elapsed time in current class
                LinearProgressIndicator(
                    progress = { status.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.25f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
