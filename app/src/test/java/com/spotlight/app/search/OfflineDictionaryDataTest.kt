package com.spotlight.app.search

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 校验打包进 assets 的离线词典数据：
 * 词条数量、唯一性、必填字段，以及几个代表性单词是否存在。
 */
class OfflineDictionaryDataTest {

    private val json: JSONObject by lazy {
        val candidates = listOf(
            "src/main/assets/offline_dictionary.json",
            "app/src/main/assets/offline_dictionary.json",
        )
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("找不到 offline_dictionary.json，工作目录：${File(".").absolutePath}")
        JSONObject(file.readText(Charsets.UTF_8))
    }

    @Test
    fun hasEnoughWords() {
        assertTrue("离线词库应至少有 3000 条", json.getInt("count") >= 3000)
    }

    @Test
    fun entriesAreWellFormed() {
        val entries = json.getJSONArray("entries")
        val seen = HashSet<String>()
        for (i in 0 until entries.length()) {
            val row = entries.getJSONArray(i)
            val word = row.getString(0)
            val meaning = row.getString(3)
            assertTrue("音标字段缺失: $word", row.length() >= 5)
            assertTrue("单词为空", word.isNotBlank())
            assertTrue("释义为空: $word", meaning.isNotBlank())
            assertTrue("重复单词: $word", seen.add(word.lowercase()))
        }
        assertEquals(json.getInt("count"), entries.length())
    }

    @Test
    fun containsCommonWords() {
        val entries = json.getJSONArray("entries")
        val words = HashSet<String>()
        for (i in 0 until entries.length()) {
            words += entries.getJSONArray(i).getString(0).lowercase()
        }
        listOf("hello", "apple", "water", "computer", "serendipity", "dictionary").forEach { word ->
            assertTrue("缺少常见词: $word", words.contains(word))
        }
    }
}
