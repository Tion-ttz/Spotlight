package com.spotlight.app.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * 接收 LSPosed 模块（运行在系统界面/桌面进程里）发来的手势诊断事件，
 * 用于在设置页显示「手势有没有装上、有没有被窗口类型过滤掉」。
 *
 * 模块侧通过显式广播 `com.spotlight.app.HOOK_DIAG` 发送，不发任何隐私内容，
 * 只有：事件类型、进程角色、窗口类型、时间。
 */
class HookDiagnosticsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        HookDiagnostics.record(context, kind, time, detail)
    }

    companion object {
        const val ACTION = "com.spotlight.app.HOOK_DIAG"
        const val EXTRA_KIND = "kind"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_TIME = "time"
    }
}

/** 诊断数据的读写。设置页只读，模块进程只写。 */
object HookDiagnostics {

    private const val FILE = "spotlight_hook_diag"
    private const val KEY_SYSTEM_UI = "systemui"
    private const val KEY_LAUNCHER = "launcher"
    private const val KEY_TRIGGER = "trigger"
    private const val KEY_IGNORED = "ignored"

    data class Event(val time: Long, val detail: String)

    data class Snapshot(
        val systemUi: Event?,
        val launcher: Event?,
        val lastTrigger: Event?,
        val lastIgnored: Event?,
    ) {
        val moduleLooksActive: Boolean get() = systemUi != null || launcher != null
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(context: Context, kind: String, time: Long, detail: String) {
        val key = when (kind) {
            "systemui" -> KEY_SYSTEM_UI
            "launcher" -> KEY_LAUNCHER
            "trigger" -> KEY_TRIGGER
            "ignored" -> KEY_IGNORED
            else -> return
        }
        prefs(context).edit().putString(key, "$time;;$detail").apply()
    }

    fun snapshot(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            systemUi = parse(p.getString(KEY_SYSTEM_UI, null)),
            launcher = parse(p.getString(KEY_LAUNCHER, null)),
            lastTrigger = parse(p.getString(KEY_TRIGGER, null)),
            lastIgnored = parse(p.getString(KEY_IGNORED, null)),
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun parse(raw: String?): Event? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(";;", limit = 2)
        val time = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return Event(time, parts.getOrNull(1).orEmpty())
    }
}
