package com.spotlight.app.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 一个可被 Spotlight 搜索到的桌面应用。 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val pinyin: String = "",
)

/**
 * 应用索引 + 图标缓存。索引在首次查询时于 IO 线程构建，之后常驻内存；
 * 安装/卸载应用后调用 invalidate() 重建。
 */
class AppRepository(private val context: Context) {

    private val mutex = Mutex()
    private val icons = LruCache<String, ImageBitmap>(160)

    @Volatile
    private var cached: List<AppEntry>? = null

    suspend fun apps(): List<AppEntry> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) { loadApps() }.also { cached = it }
        }
    }

    fun invalidate() {
        cached = null
    }

    fun icon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawable.toBitmap(width = 144, height = 144).asImageBitmap()
            icons.put(packageName, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                if (activity.packageName == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: activity.packageName
                AppEntry(
                    packageName = activity.packageName,
                    label = label,
                    pinyin = Pinyin.of(label),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** 用系统 ICU 把中文应用名转成拼音，让 "wx" 也能搜到「微信」。 */
object Pinyin {

    private val transliterator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { android.icu.text.Transliterator.getInstance("Han-Latin/Names; Latin-ASCII") }
                .getOrNull()
        } else {
            null
        }
    }

    fun of(text: String): String {
        if (!text.any { it.code in 0x4E00..0x9FFF }) return ""
        val t = transliterator ?: return ""
        return runCatching {
            t.transliterate(text).lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()
        }.getOrDefault("")
    }
}

/** 轻量模糊匹配：完全匹配 > 前缀 > 词首 > 包含 > 子序列。 */
object Fuzzy {

    fun score(query: String, target: String): Int {
        if (query.isEmpty()) return 0
        val q = query.lowercase().trim()
        val t = target.lowercase()
        if (q.isEmpty() || t.isEmpty()) return 0

        // 多关键词：全部命中才算，取最低分。
        if (q.contains(' ') && !t.contains(' ')) {
            val parts = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return 0
            val scores = parts.map { scoreSingle(it, t) }
            if (scores.any { it == 0 }) return 0
            return scores.min()
        }
        return scoreSingle(q, t)
    }

    private fun scoreSingle(q: String, t: String): Int {
        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - (t.length - q.length).coerceAtMost(80)
        val wordStart = t.indexOf(" $q")
        if (wordStart >= 0) return 860 - wordStart.coerceAtMost(60)
        val contained = t.indexOf(q)
        if (contained >= 0) return 780 - contained.coerceAtMost(160)
        val subsequence = subsequenceScore(q, t)
        return if (subsequence > 0) subsequence else 0
    }

    private fun subsequenceScore(q: String, t: String): Int {
        if (q.isEmpty()) return 0
        var qi = 0
        var gaps = 0
        var lastIndex = -1
        for (i in t.indices) {
            if (qi < q.length && t[i] == q[qi]) {
                if (lastIndex >= 0 && i - lastIndex > 1) gaps += i - lastIndex - 1
                lastIndex = i
                qi++
                if (qi == q.length) break
            }
        }
        if (qi < q.length) return 0
        return (640 - gaps * 8 - (t.length - q.length)).coerceAtLeast(120)
    }

    /** 命中的字符区间，用于在结果里加粗高亮。 */
    fun highlightRanges(query: String, target: String): List<IntRange> {
        val q = query.lowercase().trim()
        val t = target.lowercase()
        if (q.isEmpty()) return emptyList()
        val direct = t.indexOf(q)
        if (direct >= 0) return listOf(direct until direct + q.length)
        val ranges = mutableListOf<IntRange>()
        var qi = 0
        var start = -1
        for (i in t.indices) {
            if (qi < q.length && t[i] == q[qi]) {
                if (start < 0) start = i
                qi++
                if (qi == q.length) {
                    ranges += start..i
                    break
                }
            } else if (start >= 0) {
                ranges += start..(i - 1)
                start = -1
            }
        }
        return ranges
    }
}
