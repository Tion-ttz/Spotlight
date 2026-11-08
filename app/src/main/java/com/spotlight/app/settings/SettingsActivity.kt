package com.spotlight.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.spotlight.app.core.Prefs
import com.spotlight.app.core.ThemeMode
import com.spotlight.app.ui.theme.SpotlightTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val prefs = remember { Prefs(this) }
            var mode by remember { mutableStateOf(prefs.themeMode) }
            SpotlightTheme(themeMode = mode) {
                SettingsScreen(
                    onBack = { finish() },
                    onThemeChange = { mode = it },
                )
            }
        }
    }
}
