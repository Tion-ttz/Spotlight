package com.spotlight.app.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 全局搜索入口的开关。
 *
 * `GlobalSearchAlias` 声明了 ACTION_SEARCH / WEB_SEARCH / GLOBAL_SEARCH，
 * 打开后系统（含小米桌面「桌面下拉 → 搜索」）会把 Spotlight 当成搜索应用拉起；
 * 关闭后这些入口不再指向 Spotlight。
 */
object SearchIntegration {

    private const val ALIAS = "com.spotlight.app.ui.GlobalSearchAlias"

    fun apply(context: Context, enabled: Boolean) {
        val packageManager = context.packageManager
        val component = ComponentName(context.packageName, ALIAS)
        val desired = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(component) != desired) {
            runCatching {
                packageManager.setComponentEnabledSetting(
                    component,
                    desired,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    fun isEnabled(context: Context): Boolean {
        val component = ComponentName(context.packageName, ALIAS)
        return when (context.packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            // 没设置过：以 manifest 的默认（启用）为准
            else -> true
        }
    }
}
