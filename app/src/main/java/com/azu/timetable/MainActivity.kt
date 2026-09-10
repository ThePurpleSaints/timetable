package com.azu.timetable

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.azu.timetable.ui.TimetableScreen
import com.azu.timetable.ui.TimetableViewModel
import com.azu.timetable.ui.theme.TimetableTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TimetableViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable high refresh rate (90Hz / 120Hz / 144Hz) if supported by device
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.post {
                    try {
                        val currentDisplay = display
                        val maxMode = currentDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
                        if (maxMode != null && maxMode.refreshRate > 60f) {
                            val params = window.attributes
                            params.preferredDisplayModeId = maxMode.modeId
                            window.attributes = params
                        }
                    } catch (_: Exception) {
                        // Fallback gracefully if display mode is locked
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = window.windowManager.defaultDisplay.supportedModes
                val maxMode = modes?.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate > 60f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Exception) {
            // Ignore display mode switch errors
        }

        setContent {
            val appThemeColor by viewModel.appThemeColor.collectAsState()
            TimetableTheme(
                appThemeColor = appThemeColor
            ) {
                TimetableScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
