package com.spotlight.app

import android.app.Application
import com.spotlight.app.core.Prefs
import com.spotlight.app.core.SearchIntegration
import com.spotlight.app.search.TranslationCache

class SpotlightApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        TranslationCache.init(this)
        // 启动时同步「全局搜索入口」开关，保证与设置页一致
        runCatching {
            val prefs = Prefs(this)
            SearchIntegration.apply(this, prefs.searchAliasEnabled)
        }
    }

    companion object {
        lateinit var instance: SpotlightApp
            private set
    }
}
