package com.spotlight.app.core

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** 词典数据来源，三选一，互斥。 */
enum class DictionaryMode(val label: String, val description: String) {
    OFFLINE_ONLY("仅离线", "只用内置离线词库，完全不联网"),
    OFFLINE_FIRST("离线优先", "先显示离线词条，再补充在线中文释义（推荐）"),
    ONLINE_FIRST("在线优先", "直接请求在线中文释义，拿到后再补离线词条"),
}

/** 翻译数据来源，三选一，互斥。 */
enum class TranslationMode(val label: String, val description: String) {
    OFFLINE_ONLY("仅离线", "只用内置词典的单词释义，不请求在线翻译"),
    OFFLINE_FIRST("离线优先", "先显示离线即时译文，再替换成在线翻译结果（推荐）"),
    ONLINE_FIRST("在线优先", "直接请求在线翻译，不显示离线预览"),
}

enum class SearchEngine(val label: String, val queryUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BAIDU("百度", "https://www.baidu.com/s?wd=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s");

    fun buildUrl(query: String): String = queryUrl.format(java.net.URLEncoder.encode(query, "UTF-8"))

    companion object {
        fun fromIndex(index: Int): SearchEngine = values().getOrElse(index) { GOOGLE }
    }
}

/**
 * 全部用户设置。读写都走 SharedPreferences，界面上直接读写即可，
 * 因为设置页每次进入都会重新组合。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = sp.edit().putString(KEY_THEME, value.name).apply()

    var searchEngine: SearchEngine
        get() = SearchEngine.fromIndex(sp.getInt(KEY_ENGINE, 0))
        set(value) = sp.edit().putInt(KEY_ENGINE, value.ordinal).apply()

    var searchAppsEnabled: Boolean
        get() = sp.getBoolean(KEY_APPS, true)
        set(value) = sp.edit().putBoolean(KEY_APPS, value).apply()

    var dictionaryEnabled: Boolean
        get() = sp.getBoolean(KEY_DICT, true)
        set(value) = sp.edit().putBoolean(KEY_DICT, value).apply()

    var translationEnabled: Boolean
        get() = sp.getBoolean(KEY_TRANSLATE, true)
        set(value) = sp.edit().putBoolean(KEY_TRANSLATE, value).apply()

    var calculatorEnabled: Boolean
        get() = sp.getBoolean(KEY_CALC, true)
        set(value) = sp.edit().putBoolean(KEY_CALC, value).apply()

    var webSearchEnabled: Boolean
        get() = sp.getBoolean(KEY_WEB, true)
        set(value) = sp.edit().putBoolean(KEY_WEB, value).apply()

    /** 当前使用的离线词库文件路径；空字符串表示使用内置词库。 */
    var dictionaryPackPath: String
        get() = sp.getString(KEY_DICT_PACK, "") ?: ""
        set(value) = sp.edit().putString(KEY_DICT_PACK, value).apply()

    /** 词典来源模式（仅离线 / 离线优先 / 在线优先），三种模式互斥。 */
    var dictionaryMode: DictionaryMode
        get() = runCatching { DictionaryMode.valueOf(sp.getString(KEY_DICT_MODE, null) ?: "") }
            .getOrDefault(DictionaryMode.OFFLINE_FIRST)
        set(value) = sp.edit().putString(KEY_DICT_MODE, value.name).apply()

    /** 翻译来源模式（仅离线 / 离线优先 / 在线优先），三种模式互斥。 */
    var translationMode: TranslationMode
        get() = runCatching { TranslationMode.valueOf(sp.getString(KEY_TRANSLATE_MODE, null) ?: "") }
            .getOrDefault(TranslationMode.OFFLINE_FIRST)
        set(value) = sp.edit().putString(KEY_TRANSLATE_MODE, value.name).apply()

    /** 空搜索时是否显示「建议」应用栏。 */
    var showSuggestions: Boolean
        get() = sp.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = sp.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    /** 是否把自己注册成系统全局搜索入口（桌面下拉=搜索时会打开 Spotlight）。 */
    var searchAliasEnabled: Boolean
        get() = sp.getBoolean(KEY_SEARCH_ALIAS, true)
        set(value) = sp.edit().putBoolean(KEY_SEARCH_ALIAS, value).apply()

    var floatingHandleEnabled: Boolean
        get() = sp.getBoolean(KEY_FLOATING, false)
        set(value) = sp.edit().putBoolean(KEY_FLOATING, value).apply()

    var edgeSwipeEnabled: Boolean
        get() = sp.getBoolean(KEY_EDGE_SWIPE, true)
        set(value) = sp.edit().putBoolean(KEY_EDGE_SWIPE, value).apply()

    var edgeSwipeHeightDp: Int
        get() = sp.getInt(KEY_EDGE_HEIGHT, 12)
        set(value) = sp.edit().putInt(KEY_EDGE_HEIGHT, value.coerceIn(6, 40)).apply()

    var hapticFeedback: Boolean
        get() = sp.getBoolean(KEY_HAPTIC, true)
        set(value) = sp.edit().putBoolean(KEY_HAPTIC, value).apply()

    var showRecents: Boolean
        get() = sp.getBoolean(KEY_RECENTS, true)
        set(value) = sp.edit().putBoolean(KEY_RECENTS, value).apply()

    var recentPackages: List<String>
        get() = (sp.getString(KEY_RECENT_APPS, "") ?: "")
            .split('\n')
            .filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_RECENT_APPS, value.take(12).joinToString("\n")).apply()

    /** 记录一次应用启动，用于「最近使用」。 */
    fun recordLaunch(packageName: String) {
        val current = recentPackages.toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        recentPackages = current
    }

    fun reset() {
        sp.edit().clear().apply()
    }

    companion object {
        private const val FILE = "spotlight_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ENGINE = "search_engine"
        private const val KEY_APPS = "feature_apps"
        private const val KEY_DICT = "feature_dictionary"
        private const val KEY_TRANSLATE = "feature_translation"
        private const val KEY_CALC = "feature_calculator"
        private const val KEY_WEB = "feature_web"
        private const val KEY_DICT_MODE = "dictionary_mode"
        private const val KEY_TRANSLATE_MODE = "translation_mode"
        private const val KEY_DICT_PACK = "dictionary_pack"
        private const val KEY_SUGGESTIONS = "show_suggestions"
        private const val KEY_SEARCH_ALIAS = "search_alias_enabled"
        private const val KEY_FLOATING = "floating_handle"
        private const val KEY_EDGE_SWIPE = "edge_swipe"
        private const val KEY_EDGE_HEIGHT = "edge_swipe_height"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_RECENTS = "show_recents"
        private const val KEY_RECENT_APPS = "recent_apps"
    }
}
