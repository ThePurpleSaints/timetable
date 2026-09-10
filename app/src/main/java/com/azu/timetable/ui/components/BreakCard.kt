package com.azu.timetable.ui.components

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azu.timetable.data.model.TimetableSlot

import kotlin.math.roundToInt

class BumpyMaterialShape(
    private val bumpSpanDp: Float = 22f,
    private val bumpHeightDp: Float = 3f,
    private val cornerRadiusDp: Float = 14f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Generic(Path())

        val bumpHeight = with(density) { bumpHeightDp.dp.toPx() }
        val targetSpan = with(density) { bumpSpanDp.dp.toPx() }
        val corner = with(density) { cornerRadiusDp.dp.toPx() }

        val left = bumpHeight
        val top = bumpHeight
        val right = w - bumpHeight
        val bottom = h - bumpHeight

        val path = Path().apply {
            val usableW = (right - left - 2 * corner).coerceAtLeast(10f)
            val usableH = (bottom - top - 2 * corner).coerceAtLeast(10f)

            val countX = (usableW / targetSpan).roundToInt().coerceAtLeast(2)
            val stepX = usableW / countX

            val countY = (usableH / targetSpan).roundToInt().coerceAtLeast(1)
            val stepY = usableH / countY

            moveTo(left + corner, top)

            // Top bumps (bulge outward/upward)
            for (i in 0 until countX) {
                val startX = left + corner + i * stepX
                val endX = startX + stepX
                cubicTo(
                    startX + stepX * 0.25f, top - bumpHeight * 1.35f,
                    endX - stepX * 0.25f, top - bumpHeight * 1.35f,
                    endX, top
                )
            }

            // Top-right corner
            quadraticBezierTo(right, top, right, top + corner)

            // Right bumps (bulge outward/rightward)
            for (i in 0 until countY) {
                val startY = top + corner + i * stepY
                val endY = startY + stepY
                cubicTo(
                    right + bumpHeight * 1.35f, startY + stepY * 0.25f,
                    right + bumpHeight * 1.35f, endY - stepY * 0.25f,
                    right, endY
                )
            }

            // Bottom-right corner
            quadraticBezierTo(right, bottom, right - corner, bottom)

            // Bottom bumps (bulge outward/downward, right to left)
            for (i in 0 until countX) {
                val startX = right - corner - i * stepX
                val endX = startX - stepX
                cubicTo(
                    startX - stepX * 0.25f, bottom + bumpHeight * 1.35f,
                    endX + stepX * 0.25f, bottom + bumpHeight * 1.35f,
                    endX, bottom
                )
            }

            // Bottom-left corner
            quadraticBezierTo(left, bottom, left, bottom - corner)

            // Left bumps (bulge outward/leftward, bottom to top)
            for (i in 0 until countY) {
                val startY = bottom - corner - i * stepY
                val endY = startY - stepY
                cubicTo(
                    left - bumpHeight * 1.35f, startY - stepY * 0.25f,
                    left - bumpHeight * 1.35f, endY + stepY * 0.25f,
                    left, endY
                )
            }

            // Top-left corner
            quadraticBezierTo(left, top, left + corner, top)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun BreakCard(
    slot: TimetableSlot,
    isCurrentActive: Boolean = false,
    use24h: Boolean = false,
    onEdit: (TimetableSlot) -> Unit,
    onDelete: (TimetableSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isLunch = remember(slot.title) { slot.title.contains("Lunch", ignoreCase = true) }
    
    val formattedStart = remember(slot.startTime, use24h) { formatTimeString(slot.startTime, use24h) }
    val formattedEnd = remember(slot.endTime, use24h) { formatTimeString(slot.endTime, use24h) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("break_card_${slot.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left timeline column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Text(
                text = formattedStart,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )

            Spacer(modifier = Modifier.height(4.dp))

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

        // Bumpy Material Card matching the active app theme
        val breakBgColor = MaterialTheme.colorScheme.secondaryContainer
        val breakTitleColor = MaterialTheme.colorScheme.onSecondaryContainer
        val breakSubColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        val iconBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        val iconTintColor = MaterialTheme.colorScheme.primary
        val bumpyShape = remember { BumpyMaterialShape() }
        
        Card(
            modifier = Modifier
                .weight(1f)
                .testTag("bumpy_break_card_${slot.id}"),
            shape = bumpyShape,
            colors = CardDefaults.cardColors(
                containerColor = breakBgColor,
                contentColor = breakTitleColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentActive) 3.dp else 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBgColor,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLunch) Icons.Filled.Restaurant else Icons.Filled.FreeBreakfast,
                            contentDescription = "Break Icon",
                            tint = iconTintColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = slot.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.15.sp
                            ),
                            color = breakTitleColor
                        )

                        if (isCurrentActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "NOW",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val locationText = if (slot.venue.isNotBlank()) "• ${slot.venue}" else "• Cafeteria"
                    Text(
                        text = "$formattedStart - $formattedEnd $locationText",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = breakSubColor
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("break_menu_button_${slot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = breakSubColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Break") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit(slot)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
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
    }
}
