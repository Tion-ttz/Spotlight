package com.spotlight.app.search

import android.content.Context
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject

/** 离线词条：英文 -> 中文，同时可反查。 */
data class OfflineWord(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meaning: String,
    val example: String,
)

/**
 * 离线词典。默认读内置 assets/offline_dictionary.json；
 * 如果用户导入了其它词库文件（[packPath]），则优先使用该文件。
 */
class OfflineDictionary(context: Context, private val packPath: String? = null) {

    private val appContext = context.applicationContext

    private val entries: List<OfflineWord> by lazy { load() }

    private val cache = LruCache<String, List<OfflineWord>>(64)

    fun lookup(query: String, limit: Int = 6): List<OfflineWord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        cache.get(q)?.let { return it }

        val scored = ArrayList<Pair<Int, OfflineWord>>()
        for (entry in entries) {
            val score = scoreEntry(q, entry)
            if (score > 0) scored += score to entry
        }
        val result = scored
            .sortedWith(compareByDescending<Pair<Int, OfflineWord>> { it.first }.thenBy { it.second.word.length })
            .take(limit)
            .map { it.second }
        cache.put(q, result)
        return result
    }

    fun exact(query: String): OfflineWord? {
        val q = query.trim().lowercase()
        return entries.firstOrNull { it.word.lowercase() == q }
    }

    fun size(): Int = entries.size

    private fun scoreEntry(q: String, entry: OfflineWord): Int {
        val word = entry.word.lowercase()
        var score = 0
        if (word == q) score = 1000
        else if (word.startsWith(q)) score = 860 - (word.length - q.length).coerceAtMost(60)
        else if (word.contains(q)) score = 720 - word.indexOf(q).coerceAtMost(60)
        else if (entry.meaning.contains(q)) score = 640
        else if (entry.meaning.lowercase().contains(q)) score = 620
        else if (q.length >= 2 && entry.example.lowercase().contains(q)) score = 400

        // 中文查询：优先中文释义完全命中
        if (score > 0 && entry.meaning.replace(" ", "") == q.replace(" ", "")) score += 80
        return score
    }

    private fun load(): List<OfflineWord> {
        return try {
            val text = readPackText()
            val root = JSONObject(text)
            val array: JSONArray = root.optJSONArray("entries") ?: return emptyList()
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val row = array.optJSONArray(i) ?: continue
                    val word = row.optString(0).trim()
                    if (word.isEmpty()) continue
                    add(
                        OfflineWord(
                            word = word,
                            phonetic = row.optString(1),
                            partOfSpeech = row.optString(2),
                            meaning = row.optString(3),
                            example = row.optString(4, ""),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 优先读取用户导入的词库文件，读不到再退回内置词库。 */
    private fun readPackText(): String {
        if (!packPath.isNullOrBlank()) {
            val file = java.io.File(packPath)
            if (file.exists() && file.length() > 0) {
                runCatching { return file.readText(Charsets.UTF_8) }
            }
        }
        return appContext.assets.open("offline_dictionary.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }
}

data class OnlineDefinition(val definition: String, val example: String?)

data class OnlineSense(val partOfSpeech: String, val definitions: List<OnlineDefinition>)

data class OnlineWord(
    val word: String,
    val phonetic: String?,
    val senses: List<OnlineSense>,
    /** 释义来源，界面据此显示徽章，例如「有道词典」。 */
    val provider: String = "在线词典",
)

/** 有道词典 jsonapi：免费、无需 key，返回英汉双解释义。 */
object YoudaoDictionary {

    private val posPattern = Regex("^([a-zA-Z]{1,8}\\.)\\s*(.+)$")

    suspend fun lookup(query: String): OnlineWord? {
        val url = "https://dict.youdao.com/jsonapi?q=${Http.encode(query)}"
        val body = Http.getText(url, timeoutMs = 4000) ?: return null
        return parse(body, query)
    }

    /** 解析 jsonapi 返回体，方便单元测试。 */
    internal fun parse(body: String, query: String): OnlineWord? {
        return try {
            val root = JSONObject(body)
            parseEc(root, query) ?: parseFanyi(root)
        } catch (_: Exception) {
            null
        }
    }

    /** 词条：ec.word[0] 里有音标和按词性分组的释义。 */
    private fun parseEc(root: JSONObject, query: String): OnlineWord? {
        val words = root.optJSONObject("ec")?.optJSONArray("word") ?: return null
        if (words.length() == 0) return null
        val entry = words.optJSONObject(0) ?: return null
        val phonetic = entry.optString("usphone").takeIf { it.isNotBlank() }
            ?: entry.optString("ukphone").takeIf { it.isNotBlank() }
        val trs = entry.optJSONArray("trs") ?: return null

        val senses = mutableListOf<OnlineSense>()
        for (i in 0 until trs.length()) {
            val tr = trs.optJSONObject(i)?.optJSONArray("tr") ?: continue
            val definitions = mutableListOf<OnlineDefinition>()
            var partOfSpeech = ""
            for (j in 0 until tr.length()) {
                val items = tr.optJSONObject(j)
                    ?.optJSONObject("l")
                    ?.optJSONArray("i") ?: continue
                for (k in 0 until items.length()) {
                    var text = items.optString(k).trim()
                    if (text.isEmpty()) continue
                    val match = posPattern.find(text)
                    if (match != null) {
                        partOfSpeech = match.groupValues[1]
                        text = match.groupValues[2]
                    }
                    definitions += OnlineDefinition(text, null)
                }
            }
            if (definitions.isNotEmpty()) senses += OnlineSense(partOfSpeech, definitions)
        }
        if (senses.isEmpty()) return null
        return OnlineWord(word = query, phonetic = phonetic, senses = senses, provider = "有道词典")
    }

    /** 短语/句子：jsonapi 的 fanyi.tran 给出整句译文。 */
    private fun parseFanyi(root: JSONObject): OnlineWord? {
        val tran = root.optJSONObject("fanyi")?.optString("tran")?.takeIf { it.isNotBlank() } ?: return null
        return OnlineWord(
            word = "",
            phonetic = null,
            senses = listOf(OnlineSense("", listOf(OnlineDefinition(tran, null)))),
            provider = "有道翻译",
        )
    }
}

/** dictionaryapi.dev：免费英文释义 API，作为有道不可用时的兜底。 */
object DictionaryApi {

    suspend fun lookup(word: String): OnlineWord? {
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${Http.encode(word)}"
        val body = Http.getText(url) ?: return null
        return try {
            val array = JSONArray(body)
            if (array.length() == 0) return null
            val first = array.getJSONObject(0)
            val phonetic = first.optString("phonetic").takeIf { it.isNotBlank() }
                ?: first.optJSONArray("phonetics")?.let { phonetics ->
                    (0 until phonetics.length())
                        .map { phonetics.optJSONObject(it)?.optString("text") ?: "" }
                        .firstOrNull { it.isNotBlank() }
                }
            val senses = mutableListOf<OnlineSense>()
            val meanings = first.optJSONArray("meanings")
            if (meanings != null) {
                for (i in 0 until meanings.length()) {
                    val meaning = meanings.optJSONObject(i) ?: continue
                    val partOfSpeech = meaning.optString("partOfSpeech")
                    val defs = mutableListOf<OnlineDefinition>()
                    val definitions = meaning.optJSONArray("definitions") ?: continue
                    for (j in 0 until definitions.length()) {
                        val def = definitions.optJSONObject(j) ?: continue
                        val text = def.optString("definition").takeIf { it.isNotBlank() } ?: continue
                        defs += OnlineDefinition(text, def.optString("example").takeIf { it.isNotBlank() })
                        if (defs.size >= 3) break
                    }
                    if (defs.isNotEmpty()) senses += OnlineSense(partOfSpeech, defs)
                    if (senses.size >= 3) break
                }
            }
            if (senses.isEmpty()) null
            else OnlineWord(
                word = first.optString("word", word),
                phonetic = phonetic,
                senses = senses,
                provider = "Dictionary API",
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** 合并离线 + 在线两路查词结果。 */
class DictionaryRepository(context: Context, private val prefs: com.spotlight.app.core.Prefs) {

    private val offline = OfflineDictionary(context, prefs.dictionaryPackPath)
    private val onlineCache = LruCache<String, OnlineWord?>(64)

    fun offlineMatches(query: String): List<OfflineWord> = offline.lookup(query)

    /** 只查本地，立即返回，不等网络。 */
    fun offlineLookup(query: String): DictionaryLookup {
        val local = offline.lookup(query)
        val exact = local.firstOrNull()?.takeIf { it.word.equals(query.trim(), ignoreCase = true) }
        return DictionaryLookup(
            query = query,
            offline = local,
            online = null,
            primary = exact ?: local.firstOrNull(),
        )
    }

    /** 只查在线（有道优先，失败回退 dictionaryapi.dev）。 */
    suspend fun onlineLookup(query: String): OnlineWord? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) return null
        val key = trimmed.lowercase()
        onlineCache.get(key)?.let { return it }
        val result = YoudaoDictionary.lookup(trimmed) ?: DictionaryApi.lookup(trimmed)
        if (result != null) onlineCache.put(key, result)
        return result
    }

    fun shouldQueryOnline(query: String): Boolean {
        val trimmed = query.trim()
        return prefs.dictionaryMode != com.spotlight.app.core.DictionaryMode.OFFLINE_ONLY &&
            trimmed.length >= 2 &&
            trimmed.length <= 40
    }

    fun wordCount(): Int = offline.size()
}

data class DictionaryLookup(
    val query: String,
    val offline: List<OfflineWord>,
    val online: OnlineWord?,
    val primary: OfflineWord?,
) {
    val isEmpty: Boolean get() = offline.isEmpty() && online == null
}
