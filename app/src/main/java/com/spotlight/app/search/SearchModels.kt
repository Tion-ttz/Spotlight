package com.spotlight.app.search

/** 一条应用搜索结果，带匹配分数用于排序。 */
data class AppResult(val entry: AppEntry, val score: Int)

/** 一次搜索聚合出的全部内容。 */
data class SpotlightResults(
    val query: String = "",
    val apps: List<AppResult> = emptyList(),
    val dictionary: DictionaryLookup? = null,
    val translation: Translation? = null,
    val calculation: String? = null,
    val dictionaryLoading: Boolean = false,
    val translationLoading: Boolean = false,
) {
    val hasAnything: Boolean
        get() = apps.isNotEmpty() || dictionary?.isEmpty == false ||
            translation != null || calculation != null
}

/** 应用模糊搜索（纯内存，可在主线程完成）。 */
object AppSearch {

    fun search(query: String, apps: List<AppEntry>, limit: Int = 8): List<AppResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val results = ArrayList<AppResult>(limit)
        for (app in apps) {
            var score = Fuzzy.score(q, app.label)
            if (score == 0 && app.pinyin.isNotEmpty()) {
                // 拼音/首字母：wx -> weixin
                score = pinyinScore(q, app.pinyin)
            }
            if (score > 0) results += AppResult(app, score)
        }
        return results
            .sortedWith(compareByDescending<AppResult> { it.score }.thenBy { it.entry.label.length })
            .take(limit)
    }

    private fun pinyinScore(q: String, pinyin: String): Int {
        val compact = pinyin.replace(" ", "")
        if (q.length > compact.length) return 0
        if (compact.startsWith(q)) return 830 - (compact.length - q.length).coerceAtMost(60)
        val initials = pinyin.split(" ").mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
        if (initials.startsWith(q)) return 800
        if (compact.contains(q)) return 700
        return 0
    }
}
