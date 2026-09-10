package com.azu.timetable

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.azu.timetable.data.model.SlotType
import com.azu.timetable.data.model.TimetableSlot
import com.azu.timetable.ui.components.SlotCard
import com.azu.timetable.ui.theme.TimetableTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleSlot = TimetableSlot(
      id = 1,
      dayOfWeek = 1,
      periodNumber = 1,
      title = "Data Structures",
      courseCode = "CS23311",
      faculty = "Ms. R. Gaja Lakshmi",
      venue = "A204",
      startTime = "08:00",
      endTime = "08:50",
      slotType = SlotType.CLASS
    )

    composeTestRule.setContent {
      TimetableTheme {
        SlotCard(
          slot = sampleSlot,
          isCurrentActive = true,
          onEdit = {},
          onDelete = {},
          onToggleNotification = {},
          onTestNotification = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
