package com.spotlight.app.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 入口。
 *
 * 作用域（模块启动器里会自动勾选，可再手动添加）：
 * - com.android.systemui：锁屏所在的系统界面；
 * - 各类桌面/启动器：桌面下滑呼出。
 *
 * 手势逻辑见 [SwipeDownHook]：顶部状态栏以下任意位置下滑呼出 Spotlight，
 * 最顶部下滑仍然交给系统打开通知栏。
 */
class SpotlightXposed : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        when {
            pkg == SYSTEM_UI_PACKAGE -> {
                XposedBridge.log("Spotlight: 注入系统界面")
                SwipeDownHook.installInProcess("SystemUI", SwipeDownHook.Scope.SYSTEM_UI)
            }

            isLauncherPackage(pkg) -> {
                XposedBridge.log("Spotlight: 注入桌面 $pkg")
                SwipeDownHook.installInProcess(pkg, SwipeDownHook.Scope.LAUNCHER)
            }
        }
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg in KNOWN_LAUNCHERS) return true
        // 兜底：绝大多数桌面包名里带 launcher，或以 .home 结尾
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.endsWith(".home")
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /** 常见桌面，LSPosed 只会加载已安装的包。 */
        private val KNOWN_LAUNCHERS = setOf(
            "com.android.launcher3",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.bbk.launcher2",
            "com.sec.android.app.launcher",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "com.transsion.XOSLauncher",
            "com.ape.launcher3",
            "com.zui.launcher",
            "com.android.launcher3u",
        )
    }
}
