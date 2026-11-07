package com.spotlight.app.search

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

/** 一次翻译结果。sourceLang/targetLang 用 "en"、"zh-CN" 这类标签。 */
data class Translation(
    val query: String,
    val text: String,
    val sourceLang: String,
    val targetLang: String,
    val provider: String,
)

/**
 * 翻译结果的磁盘缓存：同一句话第二次出现时直接命中，不再等网络。
 * 放在 SharedPreferences 里，重启应用依然有效。
 */
object TranslationCache {

    private const val FILE = "spotlight_translation_cache"
    private const val MAX_ENTRIES = 400

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    fun keyFor(sourceLang: String, targetLang: String, text: String): String {
        val raw = "$sourceLang|$targetLang|$text"
        return try {
            MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            Integer.toHexString(raw.hashCode())
        }
    }

    fun get(key: String): String? = prefs?.getString(key, null)

    fun put(key: String, value: String) {
        val store = prefs ?: return
        if (store.all.size > MAX_ENTRIES) {
            store.edit().clear().apply()
        }
        store.edit().putString(key, value).apply()
    }
}

/**
 * 使用 MyMemory 免费接口做中英互译（无需 key，匿名每天有额度）。
 * 中文输入自动译成英文，其它输入译成中文。
 *
 * 速度优化：
 * - 内存 LruCache + 磁盘缓存，重复查询零延迟；
 * - 超时缩短到 4 秒，失败快速返回；
 * - 失败结果短暂缓存，避免连续输入时反复请求同一个词。
 */
object Translator {

    private val cache = android.util.LruCache<String, Translation>(128)
    private val failures = HashMap<String, Long>()
    private const val FAILURE_TTL_MS = 60_000L

    fun containsChinese(text: String): Boolean =
        text.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }

    /** 判断一段文本是否真的落在目标语言里。 */
    private fun matchesTargetLanguage(text: String, target: String): Boolean {
        return when {
            target.startsWith("zh") -> containsChinese(text)
            target == "en" -> text.any { it in 'a'..'z' || it in 'A'..'Z' }
            else -> true
        }
    }

    fun hasLetters(text: String): Boolean = text.any { it.isLetter() }

    suspend fun translate(
        query: String,
        forceTarget: String? = null,
    ): Translation? {
        val text = query.trim()
        if (text.isEmpty() || text.length > 500) return null
        if (!hasLetters(text)) return null

        val source = if (containsChinese(text)) "zh-CN" else "en"
        val target = forceTarget ?: if (source == "zh-CN") "en" else "zh-CN"
        if (source == target) return null

        val key = "$source|$target|$text"
        cache.get(key)?.let { return it }

        val cached = TranslationCache.get(TranslationCache.keyFor(source, target, text))
        if (!cached.isNullOrBlank()) {
            return Translation(
                query = text,
                text = cached,
                sourceLang = source,
                targetLang = target,
                provider = "MyMemory",
            ).also { cache.put(key, it) }
        }

        val failedAt = failures[key]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_TTL_MS) {
            return null
        }

        val url = "https://api.mymemory.translated.net/get?q=${Http.encode(text)}&langpair=$source|$target"
        val body = Http.getText(url, timeoutMs = 4000)
        if (body == null) {
            failures[key] = System.currentTimeMillis()
            return null
        }
        val result = try {
            val root = JSONObject(body)
            val status = root.optInt("responseStatus", 0)
            val translated = root.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            if (status != 200 || translated.isBlank()) return null
            if (translated.startsWith("PLEASE SELECT", ignoreCase = true)) return null
            // MyMemory 偶尔回显原文，视为失败
            if (translated.trim().equals(text, ignoreCase = true)) return null
            // 目标语言校验：译成中文必须含汉字，译成英文必须含拉丁字母，
            // 否则说明接口返回了错误语种（例如把英文译成了菲律宾语），直接丢弃。
            if (!matchesTargetLanguage(translated, target)) return null
            Translation(
                query = text,
                text = translated.trim(),
                sourceLang = source,
                targetLang = target,
                provider = "MyMemory",
            )
        } catch (_: Exception) {
            null
        }
        if (result != null) {
            cache.put(key, result)
            TranslationCache.put(TranslationCache.keyFor(source, target, text), result.text)
        } else {
            failures[key] = System.currentTimeMillis()
        }
        return result
    }
}

/** 语言标签 -> 界面展示名。 */
fun languageLabel(tag: String): String = when (tag) {
    "en" -> "英文"
    "zh-CN" -> "中文"
    "zh-TW" -> "繁体中文"
    "ja" -> "日文"
    "ko" -> "韩文"
    else -> tag
}
