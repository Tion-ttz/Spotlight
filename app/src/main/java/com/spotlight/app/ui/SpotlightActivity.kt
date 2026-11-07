package com.spotlight.app.ui

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.spotlight.app.settings.SettingsActivity
import com.spotlight.app.ui.theme.LocalSpotlightPalette
import com.spotlight.app.ui.theme.SpotlightTheme

/**
 * Spotlight 主 Activity。透明背景 + 半透明遮罩，视觉上悬浮在桌面/锁屏之上。
 * 由 Launcher、LSPosed 手势、悬浮把手、快捷磁贴或助理键拉起。
 */
class SpotlightActivity : ComponentActivity() {

    private val viewModel: SpotlightViewModel by viewModels()
    private var initialQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        enableBackdropBlur()
        initialQuery = extractQuery(intent)

        setContent {
            SpotlightTheme(themeMode = viewModel.prefs.themeMode) {
                val palette = LocalSpotlightPalette.current
                LaunchedEffect(palette.isDark) {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !palette.isDark
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightNavigationBars = !palette.isDark
                }
                SpotlightScreen(
                    viewModel = viewModel,
                    initialQuery = initialQuery,
                    onDismiss = { finish() },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {        super.onNewIntent(intent)
        setIntent(intent)
        val query = extractQuery(intent)
        if (!query.isNullOrBlank()) {
            initialQuery = query
            viewModel.onQueryChanged(query)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDynamic()
    }

    /**
     * Android 12+ 支持跨窗口背景模糊：给窗口开启 BLUR_BEHIND，
     * 半透明遮罩就会呈现 iOS Spotlight 那种毛玻璃质感，壁纸仍然可见。
     */
    private fun enableBackdropBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val manager = getSystemService(WindowManager::class.java) ?: return
            if (!manager.isCrossWindowBlurEnabled) return
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 60 }
        }
    }

    private fun extractQuery(intent: Intent?): String? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_SEARCH,
            Intent.ACTION_WEB_SEARCH,
            ACTION_GLOBAL_SEARCH,
            ACTION_SEARCH_LONG_PRESS,
            -> intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: intent.getStringExtra(EXTRA_QUERY)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(SearchManager.QUERY)
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val EXTRA_QUERY = "com.spotlight.app.extra.QUERY"
        private const val ACTION_GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH"
        private const val ACTION_SEARCH_LONG_PRESS = "android.intent.action.SEARCH_LONG_PRESS"

        /** 供手势/磁贴/悬浮把手复用的启动 Intent。 */
        fun intent(context: android.content.Context, query: String? = null): Intent {
            return Intent(context, SpotlightActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                if (!query.isNullOrBlank()) putExtra(EXTRA_QUERY, query)
            }
        }
    }
}
