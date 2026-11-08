package com.spotlight.app.ui

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlight.app.core.Prefs
import com.spotlight.app.search.AppEntry
import com.spotlight.app.search.AppRepository
import com.spotlight.app.search.AppSearch
import com.spotlight.app.search.Calculator
import com.spotlight.app.search.DictionaryRepository
import com.spotlight.app.search.SpotlightResults
import com.spotlight.app.search.Translation
import com.spotlight.app.search.Translator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpotlightUiState(
    val query: String = "",
    val appsReady: Boolean = false,
    val results: SpotlightResults = SpotlightResults(),
    val recentApps: List<AppEntry> = emptyList(),
    val suggestions: List<AppEntry> = emptyList(),
)

/**
 * Spotlight 主界面的状态机：应用匹配同步完成，词典/翻译走防抖异步请求。
 */
class SpotlightViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val prefs = Prefs(context)

    private val appRepository = AppRepository(context)
    private val dictionaryRepository = DictionaryRepository(context, prefs)

    private val _state = MutableStateFlow(SpotlightUiState())
    val state: StateFlow<SpotlightUiState> = _state.asStateFlow()

    private var allApps: List<AppEntry> = emptyList()
    private var dictionaryJob: Job? = null
    private var translationJob: Job? = null

    init {
        viewModelScope.launch {
            allApps = appRepository.apps()
            _state.update {
                it.copy(
                    appsReady = true,
                    recentApps = recentEntries(),
                    suggestions = suggestions(),
                )
            }
        }
    }

    fun icon(packageName: String): ImageBitmap? = appRepository.icon(packageName)

    fun onQueryChanged(raw: String) {
        val query = raw.take(80)
        if (query == _state.value.query) return
        dictionaryJob?.cancel()
        translationJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    query = query,
                    results = SpotlightResults(query = query),
                )
            }
            return
        }

        val calc = if (prefs.calculatorEnabled && Calculator.looksLikeExpression(trimmed)) {
            Calculator.evaluate(trimmed)
        } else {
            null
        }
        val apps = if (prefs.searchAppsEnabled) AppSearch.search(trimmed, allApps) else emptyList()
        val lookupDictionary = shouldLookupWord(trimmed)
        // 离线结果同步算出，立即显示；网络释义在后台补充
        val offlineLookup = if (lookupDictionary) dictionaryRepository.offlineLookup(trimmed) else null
        val willQueryOnline = lookupDictionary && dictionaryRepository.shouldQueryOnline(trimmed)
        val dictionaryLoading = willQueryOnline && offlineLookup?.isEmpty != false
        val translationMode = prefs.translationMode
        // 单个英文单词且离线词典已收录时，词典卡片已经给了中文释义，不再重复翻译
        val dictionaryCoversWord = offlineLookup?.primary != null &&
            !trimmed.contains(' ') &&
            !Translator.containsChinese(trimmed)
        val lookupOnlineTranslation = prefs.translationEnabled &&
            !dictionaryCoversWord &&
            translationMode != com.spotlight.app.core.TranslationMode.OFFLINE_ONLY &&
            translationEligible(trimmed)
        // 离线优先：先用内置词典给出「即时译文」，网络结果回来后再替换
        val localPreview = if (
            prefs.translationEnabled &&
            !dictionaryCoversWord &&
            translationMode != com.spotlight.app.core.TranslationMode.ONLINE_FIRST
        ) {
            localTranslationPreview(trimmed)
        } else {
            null
        }

        _state.update {
            it.copy(
                query = query,
                results = SpotlightResults(
                    query = trimmed,
                    apps = apps,
                    calculation = calc,
                    dictionary = offlineLookup,
                    translation = localPreview,
                    dictionaryLoading = dictionaryLoading,
                    translationLoading = lookupOnlineTranslation,
                ),
            )
        }

        if (willQueryOnline) {
            dictionaryJob = viewModelScope.launch {
                // 先让离线结果上屏，再补在线释义；在线失败也不影响已有内容
                delay(120)
                val online = dictionaryRepository.onlineLookup(trimmed)
                _state.update { current ->
                    if (current.query.trim() != trimmed) current
                    else current.copy(
                        results = current.results.copy(
                            dictionary = (current.results.dictionary ?: offlineLookup)?.copy(online = online),
                            dictionaryLoading = false,
                        ),
                    )
                }
            }
        }

        if (lookupOnlineTranslation) {
            translationJob = viewModelScope.launch {
                delay(240)
                val translation = Translator.translate(trimmed)
                _state.update { current ->
                    if (current.query.trim() != trimmed) current
                    else current.copy(
                        results = current.results.copy(
                            // 在线翻译失败时保留离线预览，不要突然消失
                            translation = translation ?: current.results.translation,
                            translationLoading = false,
                        ),
                    )
                }
            }
        }
    }

    /** 用离线词典拼一个即时译文，等网络翻译回来再覆盖。 */
    private fun localTranslationPreview(query: String): Translation? {
        if (query.length > 24) return null
        val wordCount = query.count { it == ' ' } + 1
        if (wordCount > 2) return null
        val matches = dictionaryRepository.offlineMatches(query)
        if (matches.isEmpty()) return null
        if (Translator.containsChinese(query)) {
            val hit = matches.first()
            return Translation(
                query = query,
                text = hit.word,
                sourceLang = "zh-CN",
                targetLang = "en",
                provider = OFFLINE_PROVIDER,
            )
        }
        val hit = matches.firstOrNull { it.word.equals(query, ignoreCase = true) } ?: matches.first()
        if (!Translator.containsChinese(hit.meaning)) return null
        return Translation(
            query = query,
            text = hit.meaning,
            sourceLang = "en",
            targetLang = "zh-CN",
            provider = OFFLINE_PROVIDER,
        )
    }

    private fun shouldLookupWord(query: String): Boolean {
        if (!prefs.dictionaryEnabled) return false
        if (query.length < 2) return false
        if (query.length > 40) return false
        // 只查单个词/短语，避免把长句丢给词典
        return query.count { it == ' ' } <= 2
    }

    private fun translationEligible(query: String): Boolean {
        if (query.length < 2 || query.length > 120) return false
        return Translator.hasLetters(query)
    }

    fun refreshDynamic() {
        _state.update { it.copy(recentApps = recentEntries(), suggestions = suggestions()) }
    }

    fun clearRecents() {
        prefs.recentPackages = emptyList()
        refreshDynamic()
    }

    fun launch(entry: AppEntry): Boolean {
        prefs.recordLaunch(entry.packageName)
        return appRepository.launch(entry.packageName)
    }

    fun wordCount(): Int = dictionaryRepository.wordCount()

    private fun recentEntries(): List<AppEntry> {
        if (!prefs.showRecents) return emptyList()
        val byPackage = allApps.associateBy { it.packageName }
        return prefs.recentPackages.mapNotNull { byPackage[it] }.take(8)
    }

    private fun suggestions(): List<AppEntry> {
        if (!prefs.showSuggestions) return emptyList()
        val recents = recentEntries().map { it.packageName }.toSet()
        return allApps
            .asSequence()
            .filter { it.packageName !in recents }
            .take(6)
            .toList()
    }

    companion object {
        /** 离线词典即时译文的来源标记，界面据此显示「离线词典」徽章。 */
        const val OFFLINE_PROVIDER = "离线词典"
    }
}
