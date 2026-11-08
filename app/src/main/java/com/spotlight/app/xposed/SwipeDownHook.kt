package com.spotlight.app.xposed

import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * iOS 式下滑呼出：
 * - 手指从「顶部状态栏以下」的任意位置向下滑 → 呼出 Spotlight；
 * - 从最顶部状态栏区域向下滑 → 交回系统，正常打开通知栏/控制中心；
 * - 点击、横滑、上滑、长按、多指都不拦截。
 *
 * 实现说明：
 * 小米（HyperOS/MIUI）的系统界面里，`ViewRootImpl` 被 ROM 换成了自己的实现，
 * 直接 hook `ViewRootImpl.dispatchInputEvent/dispatchTouchEvent` 收不到事件，
 * 因此改为 hook `android.view.View.dispatchTouchEvent`，只处理「没有父 View 的根布局」
 * （也就是每个窗口的 DecorView），每个触摸事件只会命中一次。
 *
 * 判定为下滑后：先给窗口补发 ACTION_CANCEL 终止桌面/锁屏自己的下拉手势，
 * 再启动 Spotlight 并吞掉本次手势剩余事件。
 */
internal object SwipeDownHook {

    private const val TARGET_PACKAGE = "com.spotlight.app"
    private const val TARGET_ACTIVITY = "com.spotlight.app.ui.SpotlightActivity"
    private const val DIAG_ACTION = "com.spotlight.app.HOOK_DIAG"

    enum class Scope { SYSTEM_UI, LAUNCHER }

    /** 需要 hook 的触摸分发实现类（不同 ROM 的根布局会覆写其中一个）。 */
    private val TOUCH_HOOK_CLASSES = listOf(
        "android.view.ViewGroup",
        "android.view.View",
    )

    /** 安全窗口（密码键盘等）的类名特征，命中就不接管。 */
    private val SECURE_WINDOW_NAME = Regex(
        "(?i)(Bouncer|Password|Pattern|PinKeyboard|KeyguardPin|SimPin|VolumeDialog|UsbDialog|GlobalActions)",
    )

    /** 通知栏 / 状态栏 / 控制中心 / 锁屏的根布局类名，只有这些窗口才接管。 */
    private val SHADE_WINDOW_NAME = Regex(
        "(?i)(NotificationShade|StatusBarWindow|ControlCenter|ControlPanel|KeyguardRoot|KeyguardView)",
    )

    /** 桌面自己的根布局类名。 */
    private val LAUNCHER_WINDOW_NAME = Regex(
        "(?i)(Launcher|Workspace|DragLayer|CellLayout|Home)",
    )

    /** 需要特殊处理：这些窗口类型（桌面窗口）允许接管。 */
    fun installInProcess(tag: String, scope: Scope) {
        val state = GestureState(scope, tag)
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                // 只处理窗口根布局：它的 parent 是 ViewRootImpl（不是 View），
                // 子 View 的 parent 都是 ViewGroup，直接放过。
                if (view.parent is View) return
                val event = param.args.getOrNull(0) as? MotionEvent ?: return
                if (state.onTouch(event, view)) {
                    param.result = true
                }
            }
        }
        for (className in TOUCH_HOOK_CLASSES) {
            try {
                val clazz = XposedHelpers.findClass(className, null)
                val methods = XposedBridge.hookAllMethods(clazz, "dispatchTouchEvent", hook)
                XposedBridge.log("Spotlight: $tag hook $className.dispatchTouchEvent -> ${methods.size}")
            } catch (t: Throwable) {
                XposedBridge.log("Spotlight: $tag hook $className 失败 ${t.message}")
            }
        }

        if (scope == Scope.LAUNCHER) {
            hookLauncherShadeExpansion(state, tag)
            hookLauncherSearchIntents(state, tag)
        }
    }

    /**
     * 很多桌面在下滑时调用 `StatusBarManager.expandNotificationsPanel()` 让系统展开通知栏。
     * 拦下这次调用，改开 Spotlight。
     */
    private fun hookLauncherShadeExpansion(state: GestureState, tag: String) {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (state.onShadeExpansionRequested()) {
                    param.result = null
                    XposedBridge.log("Spotlight: $tag 拦截了桌面展开通知栏的请求")
                }
            }
        }
        try {
            val managerClass = XposedHelpers.findClass("android.app.StatusBarManager", null)
            XposedBridge.hookAllMethods(managerClass, "expandNotificationsPanel", hook)
            XposedBridge.hookAllMethods(managerClass, "expandSettingsPanel", hook)
        } catch (t: Throwable) {
            XposedBridge.log("Spotlight: $tag 通知栏拦截不可用 ${t.message}")
        }
    }

    /**
     * 有些 ROM 的「桌面下拉 → 搜索」会启动一个搜索 Activity（小米全局搜索、Google 搜索等）。
     * 把这些搜索跳转改成打开 Spotlight。
     */
    private fun hookLauncherSearchIntents(state: GestureState, tag: String) {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val intent = param.args.getOrNull(0) as? Intent ?: return
                if (!isSearchIntent(intent)) return
                if (state.launchFromCache()) {
                    param.result = null
                    XposedBridge.log("Spotlight: $tag 已把搜索跳转改成 Spotlight")
                }
            }
        }
        try {
            val contextImpl = XposedHelpers.findClass("android.app.ContextImpl", null)
            XposedBridge.hookAllMethods(contextImpl, "startActivity", hook)
        } catch (t: Throwable) {
            XposedBridge.log("Spotlight: $tag 搜索跳转拦截不可用 ${t.message}")
        }
    }

    private fun isSearchIntent(intent: Intent): Boolean {
        val component = intent.component
        if (component?.packageName == TARGET_PACKAGE) return false
        if (intent.action in SEARCH_ACTIONS) return true
        val className = component?.className?.lowercase().orEmpty()
        val packageName = component?.packageName?.lowercase().orEmpty()
        return className.contains("search") ||
            packageName.contains("globalsearch") ||
            packageName.contains("quicksearch")
    }

    private val SEARCH_ACTIONS = setOf(
        Intent.ACTION_SEARCH,
        Intent.ACTION_WEB_SEARCH,
        "android.search.action.GLOBAL_SEARCH",
        "android.intent.action.SEARCH_LONG_PRESS",
    )

    private class GestureState(private val scope: Scope, private val tag: String) {

        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var armed = false
        private var fired = false
        private var swallowing = false
        private var cancelling = false

        private var statusBarHeight = -1
        private var threshold = 0f
        private var edgeMargin = 0f
        private var screenWidth = 0
        private var screenHeight = 0
        private var edgeGuard = 0f
        private var bottomGuard = 0f

        private var lastDiagAt = 0L
        private var lastDiagKind = ""
        private var lastLogAt = 0L

        private var lastDy = 0f
        private var lastMoveTime = 0L
        private var lastStartedBelowTop = false
        private var cachedContext: Context? = null
        private var lastProbeAt = 0L

        fun onTouch(event: MotionEvent, root: View): Boolean {
            if (cancelling) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ensureMetrics(root)
                    val accepted = event.pointerCount == 1 &&
                        isGestureSurface(root) &&
                        !isNearSystemGestureEdge(event.x, event.y)
                    armed = accepted
                    fired = false
                    swallowing = false
                    lastDy = 0f
                    lastStartedBelowTop = false
                    downX = event.x
                    downY = event.y
                    downTime = event.eventTime
                    cachedContext = root.context
                    diagnose(root.context, kindFor(accepted), "root=${root.javaClass.simpleName}")
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (swallowing) return true
                    if (!armed || fired) return fired
                    val dy = event.y - downY
                    val dx = kotlin.math.abs(event.x - downX)
                    val startedBelowStatusBar = downY > statusBarHeight + edgeMargin
                    lastDy = dy
                    lastMoveTime = System.currentTimeMillis()
                    lastStartedBelowTop = startedBelowStatusBar
                    // 更严格的判定，减少误触：
                    // 1) 必须明显是竖直方向（横向分量很小）；
                    // 2) 滑动距离和速度都要够；
                    // 3) 起点不能贴左右边缘/底部（避开返回手势和上滑手势）。
                    val mostlyVertical = dy > dx * 2.2f
                    val farEnough = dy > threshold
                    val quickEnough = event.eventTime - downTime < 900
                    if (startedBelowStatusBar && mostlyVertical && farEnough && quickEnough) {
                        fired = true
                        swallowing = true
                        cancelOngoingGesture(root, event)
                        diagnose(root.context, "trigger", "from=$tag dy=$dy dx=$dx")
                        launchWith(root.context)
                        XposedBridge.log("Spotlight: [$tag] 触发下滑 dy=$dy dx=$dx")
                        return true
                    }
                    return false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val consume = fired || swallowing
                    armed = false
                    fired = false
                    swallowing = false
                    return consume
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    armed = false
                    return false
                }
            }
            return false
        }

        /** 用最近一次触摸缓存下来的 Context 打开 Spotlight（供搜索跳转拦截使用）。 */
        fun launchFromCache(): Boolean {
            val context = cachedContext ?: return false
            diagnose(context, "trigger", "via=search from=$tag")
            launchWith(context)
            return true
        }

        /** 桌面请求展开通知栏/控制中心时调用。 */
        fun onShadeExpansionRequested(): Boolean {
            val context = cachedContext ?: return false
            val recent = System.currentTimeMillis() - lastMoveTime < 1500L
            if (!lastStartedBelowTop || !recent || lastDy < threshold * 0.5f) return false
            fired = true
            swallowing = true
            diagnose(context, "trigger", "via=expansion from=$tag")
            launchWith(context)
            return true
        }

        private fun kindFor(accepted: Boolean): String = when {
            !accepted -> "ignored"
            scope == Scope.SYSTEM_UI -> "systemui"
            else -> "launcher"
        }

        private fun isGestureSurface(root: View): Boolean {
            if (containsSecureClass(root, 0)) return false
            return when (scope) {
                // 系统界面：只认通知栏/状态栏/锁屏根布局，避免接管侧滑返回、音量条等窗口
                Scope.SYSTEM_UI -> containsClassMatching(root, 0, SHADE_WINDOW_NAME)
                // 桌面：根布局必须是桌面自己的视图（PopupWindow 也有 DecorView，但通常不含这些类名）
                Scope.LAUNCHER -> containsClassMatching(root, 0, LAUNCHER_WINDOW_NAME) ||
                    root.javaClass.simpleName == "DecorView"
            }
        }

        /** 起点贴近屏幕左右边缘或底部手势区时不接管，避免和系统返回/上滑手势冲突。 */
        private fun isNearSystemGestureEdge(x: Float, y: Float): Boolean {
            if (screenWidth <= 0 || screenHeight <= 0) return false
            if (x < edgeGuard || x > screenWidth - edgeGuard) return true
            return y > screenHeight - bottomGuard
        }

        private fun containsSecureClass(view: View, depth: Int): Boolean =
            containsClassMatching(view, depth, SECURE_WINDOW_NAME)

        private fun containsClassMatching(view: View, depth: Int, pattern: Regex): Boolean {
            if (depth > 4) return false
            if (pattern.containsMatchIn(view.javaClass.simpleName)) return true
            val group = view as? android.view.ViewGroup ?: return false
            for (i in 0 until group.childCount) {
                if (containsClassMatching(group.getChildAt(i), depth + 1, pattern)) return true
            }
            return false
        }

        private fun ensureMetrics(root: View) {
            if (statusBarHeight >= 0) return
            val resources = root.context.resources
            val metrics = resources.displayMetrics
            val density = metrics.density
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight = if (id > 0) resources.getDimensionPixelSize(id) else (24 * density).toInt()
            threshold = 64 * density
            edgeMargin = 6 * density
            edgeGuard = 28 * density
            bottomGuard = 56 * density
        }

        /** 给窗口补发 ACTION_CANCEL，终止桌面/锁屏已经开始的拖拽。 */
        private fun cancelOngoingGesture(root: View, event: MotionEvent) {
            try {
                val cancel = MotionEvent.obtain(
                    event.downTime,
                    event.eventTime,
                    MotionEvent.ACTION_CANCEL,
                    event.x,
                    event.y,
                    event.metaState,
                )
                cancelling = true
                try {
                    root.dispatchTouchEvent(cancel)
                } finally {
                    cancelling = false
                }
                cancel.recycle()
            } catch (t: Throwable) {
                cancelling = false
                XposedBridge.log("Spotlight: 取消原手势失败 ${t.message}")
            }
        }

        private fun launchWith(context: Context) {
            try {
                val intent = Intent().apply {
                    setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                context.startActivity(intent)
            } catch (t: Throwable) {
                XposedBridge.log("Spotlight: 启动失败 ${t.message}")
            }
        }

        private fun diagnose(context: Context, kind: String, detail: String) {
            val now = System.currentTimeMillis()
            if (kind == lastDiagKind && now - lastDiagAt < 3000L) return
            lastDiagKind = kind
            lastDiagAt = now
            try {
                val intent = Intent(DIAG_ACTION).apply {
                    setPackage(TARGET_PACKAGE)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra("kind", kind)
                    putExtra("detail", detail)
                    putExtra("time", now)
                }
                context.sendBroadcast(intent)
            } catch (_: Throwable) {
                // 诊断失败不影响主流程
            }
        }
    }
}
