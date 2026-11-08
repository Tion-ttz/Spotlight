package com.spotlight.app.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.spotlight.app.core.DictionaryMode
import com.spotlight.app.core.Prefs
import com.spotlight.app.core.SearchEngine
import com.spotlight.app.core.ThemeMode
import com.spotlight.app.core.TranslationMode
import com.spotlight.app.search.OfflineDictionary
import com.spotlight.app.trigger.FloatingButtonService
import com.spotlight.app.trigger.HookDiagnostics
import com.spotlight.app.ui.theme.LocalSpotlightPalette
import com.spotlight.app.ui.theme.SpotlightTheme

/**
 * 设置界面。所有开关直接写 SharedPreferences，Spotlight 下次打开即生效。
 * [onThemeChange] 用于切换主题后立即刷新本页外观。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChange: (ThemeMode) -> Unit = {}) {
    val context = LocalContext.current
    val palette = LocalSpotlightPalette.current
    val prefs = remember { Prefs(context) }
    var tick by remember { mutableIntStateOf(0) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showDictionaryModeDialog by remember { mutableStateOf(false) }
    var showTranslationModeDialog by remember { mutableStateOf(false) }
    var showPackDialog by remember { mutableStateOf(false) }

    val importPackLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val path = importDictionaryPack(context, uri)
            if (path != null) {
                prefs.dictionaryPackPath = path
                tick++
            }
        }
    }
    val wordCount = remember { OfflineDictionary(context).size() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refresh() {
        tick++
    }

    // 读取一次，tick 变化时重算
    val themeMode = remember(tick) { prefs.themeMode }
    val engine = remember(tick) { prefs.searchEngine }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.textPrimary,
                )
            }
            Text(
                text = "设置",
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("外观")
            Group {
                ValueRow(
                    title = "主题",
                    value = when (themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.DARK -> "深色"
                        ThemeMode.LIGHT -> "浅色"
                    },
                    onClick = {
                        val next = when (themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.SYSTEM
                        }
                        prefs.themeMode = next
                        onThemeChange(next)
                        refresh()
                    },
                )
                Divider()
                ValueRow(
                    title = "默认搜索引擎",
                    value = engine.label,
                    onClick = { showEngineDialog = true },
                )
            }

            SectionTitle("搜索内容")
            Group {
                SwitchRow(
                    title = "搜索应用",
                    subtitle = "支持拼音与首字母，例如 wx 找到微信",
                    checked = prefs.searchAppsEnabled,
                    onCheckedChange = { prefs.searchAppsEnabled = it; refresh() },
                )
                Divider()
                SwitchRow(
                    title = "词典",
                    subtitle = "内置 $wordCount 条离线词条",
                    checked = prefs.dictionaryEnabled,
                    onCheckedChange = { prefs.dictionaryEnabled = it; refresh() },
                )
                Divider()
                SwitchRow(
                    title = "翻译",
                    subtitle = "中英互译",
                    checked = prefs.translationEnabled,
                    onCheckedChange = { prefs.translationEnabled = it; refresh() },
                )
                Divider()
                SwitchRow(
                    title = "计算器",
                    subtitle = "输入算式即时显示结果",
                    checked = prefs.calculatorEnabled,
                    onCheckedChange = { prefs.calculatorEnabled = it; refresh() },
                )
                Divider()
                SwitchRow(
                    title = "网页搜索",
                    subtitle = "结果不足时可在浏览器搜索",
                    checked = prefs.webSearchEnabled,
                    onCheckedChange = { prefs.webSearchEnabled = it; refresh() },
                )
                Divider()
                SwitchRow(
                    title = "建议栏",
                    subtitle = "空搜索时显示「建议」应用栏",
                    checked = prefs.showSuggestions,
                    onCheckedChange = { prefs.showSuggestions = it; refresh() },
                )
            }

            SectionTitle("离线 / 在线")
            Group {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = "词典与翻译各自独立选择来源，三种模式互斥；选「仅离线」时完全不联网。",
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                    )
                }
                Divider()
                ValueRow(
                    title = "离线词库",
                    value = dictionaryPackLabel(prefs),
                    onClick = { showPackDialog = true },
                )
                Divider()
                ValueRow(
                    title = "词典来源",
                    value = prefs.dictionaryMode.label,
                    onClick = { showDictionaryModeDialog = true },
                )
                Divider()
                ValueRow(
                    title = "翻译来源",
                    value = prefs.translationMode.label,
                    onClick = { showTranslationModeDialog = true },
                )
            }

            SectionTitle("桌面下拉 / 全局搜索")
            Group {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = "小米 / 红米（HyperOS、MIUI）",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "系统设置 → 桌面 → 桌面下拉 → 选「搜索」。本应用已注册为系统搜索入口，" +
                            "选「搜索」后从桌面下滑就会直接打开 Spotlight，不需要 LSPosed。",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "如果你的 ROM 提示“没有原版搜索”，先确认下面的开关是开着的，" +
                            "再去桌面设置里重新选一次「搜索」。",
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                    )
                }
                Divider()
                SwitchRow(
                    title = "接管桌面下拉搜索",
                    subtitle = if (prefs.searchAliasEnabled) {
                        "已注册为系统搜索入口（SEARCH / WEB_SEARCH / GLOBAL_SEARCH）"
                    } else {
                        "已关闭：搜索动作不会再打开 Spotlight"
                    },
                    checked = prefs.searchAliasEnabled,
                    onCheckedChange = { enabled ->
                        prefs.searchAliasEnabled = enabled
                        com.spotlight.app.core.SearchIntegration.apply(context, enabled)
                        refresh()
                    },
                )
                Divider()
                ValueRow(
                    title = "打开桌面设置",
                    value = "",
                    onClick = { openHomeSettings(context) },
                )
            }

            SectionTitle("呼出方式")
            Group {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = "① LSPosed 下滑手势（推荐）",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "在 LSPosed 中启用本模块，作用域同时勾选「系统界面（com.android.systemui）」" +
                            "和你的桌面/启动器（如 Pixel Launcher、系统桌面），然后重启系统界面或重启手机。",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "和 iPhone 一样：在桌面或锁屏，除最顶部状态栏以外的任意位置向下滑，即可呼出 Spotlight；" +
                            "从最顶部下滑仍然打开通知栏。",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                    )
                }
                Divider()
                SwitchRow(
                    title = "悬浮把手",
                    subtitle = if (overlayGranted) {
                        "屏幕顶部显示一个小把手，点按即可呼出"
                    } else {
                        "需要「显示在其他应用的上层」权限"
                    },
                    checked = prefs.floatingHandleEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(context)) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            prefs.floatingHandleEnabled = enabled
                            if (enabled) startFloatingHandle(context) else stopFloatingHandle(context)
                            refresh()
                        }
                    },
                )
                Divider()
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = "② 通知栏快捷磁贴",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "下拉通知栏 → 编辑快捷开关 → 把「Spotlight」拖到常用区域；" +
                            "也可以把 Spotlight 设为助理应用，长按 Home 键呼出。",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            SectionTitle("手势自检")
            Group {
                val diag = remember(tick) { HookDiagnostics.snapshot(context) }
                val homePackage = remember {
                    context.packageManager
                        .resolveActivity(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                        )
                        ?.activityInfo
                        ?.packageName
                }
                DiagRow(
                    title = "当前桌面",
                    value = homePackage ?: "未识别",
                    detail = if (homePackage != null && homePackage != "com.android.systemui") {
                        "LSPosed 作用域里要勾选这个包名"
                    } else {
                        ""
                    },
                )
                Divider()
                DiagRow(
                    title = "系统界面手势",
                    value = diag.systemUi?.let { formatTime(it.time) } ?: "未检测到",
                    detail = diag.systemUi?.detail ?: "还没收到系统界面进程的触摸事件",
                )
                Divider()
                DiagRow(
                    title = "桌面手势",
                    value = diag.launcher?.let { formatTime(it.time) } ?: "未检测到",
                    detail = diag.launcher?.detail ?: "还没收到桌面进程的触摸事件",
                )
                Divider()
                DiagRow(
                    title = "最近一次呼出",
                    value = diag.lastTrigger?.let { formatTime(it.time) } ?: "无",
                    detail = diag.lastTrigger?.detail.orEmpty(),
                )
                Divider()
                DiagRow(
                    title = "被过滤的窗口",
                    value = diag.lastIgnored?.let { formatTime(it.time) } ?: "无",
                    detail = diag.lastIgnored?.detail.orEmpty(),
                )
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "看不到「已安装」就说明模块没注入到对应进程：确认 LSPosed 已启用模块、" +
                            "作用域勾了系统界面和桌面，然后重启系统界面/桌面再回来刷新。",
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "刷新",
                        color = palette.accent,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { refresh() }
                            .padding(start = 10.dp),
                    )
                }
            }

            SectionTitle("其他")
            Group {
                SwitchRow(
                    title = "显示最近使用",
                    subtitle = "空搜索时展示最近打开的应用",
                    checked = prefs.showRecents,
                    onCheckedChange = { prefs.showRecents = it; refresh() },
                )
                Divider()
                ValueRow(
                    title = "清除最近使用记录",
                    value = "",
                    onClick = {
                        prefs.recentPackages = emptyList()
                        refresh()
                    },
                )
                Divider()
                ValueRow(
                    title = "恢复默认设置",
                    value = "",
                    onClick = {
                        prefs.reset()
                        stopFloatingHandle(context)
                        refresh()
                    },
                )
            }

            SectionTitle("关于")
            Group {
                ValueRow(title = "版本", value = "1.0", onClick = {})
                Divider()
                ValueRow(title = "离线词条", value = "$wordCount 条", onClick = {})
                Divider()
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = "词典与翻译来自公开接口，仅供学习使用。",
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("默认搜索引擎") },
            text = {
                Column {
                    SearchEngine.values().forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    prefs.searchEngine = item
                                    showEngineDialog = false
                                    refresh()
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                        ) {
                            RadioButton(
                                selected = item == engine,
                                onClick = {
                                    prefs.searchEngine = item
                                    showEngineDialog = false
                                    refresh()
                                },
                            )
                            Text(item.label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEngineDialog = false }) { Text("完成") }
            },
        )
    }

    if (showDictionaryModeDialog) {
        ModeDialog(
            title = "词典来源",
            options = DictionaryMode.entries.map { it.label to it.description },
            selectedIndex = prefs.dictionaryMode.ordinal,
            onSelect = { index ->
                prefs.dictionaryMode = DictionaryMode.entries[index]
                showDictionaryModeDialog = false
                refresh()
            },
            onDismiss = { showDictionaryModeDialog = false },
        )
    }

    if (showTranslationModeDialog) {
        ModeDialog(
            title = "翻译来源",
            options = TranslationMode.entries.map { it.label to it.description },
            selectedIndex = prefs.translationMode.ordinal,
            onSelect = { index ->
                prefs.translationMode = TranslationMode.entries[index]
                showTranslationModeDialog = false
                refresh()
            },
            onDismiss = { showTranslationModeDialog = false },
        )
    }
    if (showPackDialog) {
        val packs = listDictionaryPacks(context)
        AlertDialog(
            onDismissRequest = { showPackDialog = false },
            title = { Text("离线词库") },
            text = {
                Column {
                    Text(
                        text = "可以导入第三方词典文件（JSON，格式与内置词库一致：entries 数组，每项 [单词, 音标, 词性, 释义, 例句]）。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    packs.forEach { (label, path) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    prefs.dictionaryPackPath = path
                                    showPackDialog = false
                                    refresh()
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                        ) {
                            RadioButton(
                                selected = prefs.dictionaryPackPath == path,
                                onClick = {
                                    prefs.dictionaryPackPath = path
                                    showPackDialog = false
                                    refresh()
                                },
                            )
                            Text(label, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            importPackLauncher.launch(
                                arrayOf("application/json", "application/octet-stream", "*/*"),
                            )
                        },
                    ) {
                        Text("导入词典文件…")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPackDialog = false }) { Text("关闭") }
            },
        )
    }
}

/** 词库文件放在应用私有目录下，导入后即可在设置里切换。 */
private fun dictionaryDir(context: Context): java.io.File =
    java.io.File(context.filesDir, "dicts").apply { mkdirs() }

private fun listDictionaryPacks(context: Context): List<Pair<String, String>> {
    val builtin = "内置词库（${com.spotlight.app.search.OfflineDictionary(context).size()} 词）" to ""
    val imported = dictionaryDir(context)
        .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?.map { "${it.nameWithoutExtension}（${it.length() / 1024} KB）" to it.absolutePath }
        .orEmpty()
    return listOf(builtin) + imported
}

private fun dictionaryPackLabel(prefs: Prefs): String {
    val path = prefs.dictionaryPackPath
    if (path.isBlank()) return "内置词库"
    return java.io.File(path).nameWithoutExtension
}

private fun importDictionaryPack(context: Context, uri: android.net.Uri): String? {
    return runCatching {
        val target = java.io.File(dictionaryDir(context), "imported_${System.currentTimeMillis()}.json")
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        if (target.length() == 0L) null else target.absolutePath
    }.getOrNull()
}
@Composable
private fun ModeDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, (label, description) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(index) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) })
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 15.sp)
                            Text(
                                text = description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    val palette = LocalSpotlightPalette.current
    Text(
        text = text,
        color = palette.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun DiagRow(title: String, value: String, detail: String) {
    val palette = LocalSpotlightPalette.current
    val missing = value == "未检测到" || value == "无" || value == "未识别"
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = if (missing) palette.textTertiary else palette.accent,
                fontSize = 12.sp,
            )
        }
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = palette.textTertiary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatTime(time: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(time))

/** 尽量打开「桌面设置」，小米的桌面设置入口不是标准 Activity，先试几个候选。 */
private fun openHomeSettings(context: Context) {
    val candidates = listOf(
        Intent("com.miui.home.settings"),
        Intent("miui.intent.action.LAUNCHER_SETTINGS"),
        Intent(Settings.ACTION_HOME_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolvable = runCatching {
            context.packageManager.resolveActivity(intent, 0) != null
        }.getOrDefault(false)
        if (!resolvable) continue
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (opened) return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}


@Composable
private fun Group(content: @Composable () -> Unit) {
    val palette = LocalSpotlightPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardBackground)
            .border(0.7.dp, palette.cardBorder, RoundedCornerShape(18.dp)),
        content = { content() },
    )
}

@Composable
private fun Divider() {
    val palette = LocalSpotlightPalette.current
    HorizontalDivider(
        color = palette.divider,
        thickness = 0.7.dp,
        modifier = Modifier.padding(start = 14.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = palette.textTertiary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val palette = LocalSpotlightPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(title, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, color = palette.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

internal fun startFloatingHandle(context: Context) {
    val intent = Intent(context, FloatingButtonService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

internal fun stopFloatingHandle(context: Context) {
    runCatching { context.stopService(Intent(context, FloatingButtonService::class.java)) }
}

/** 供 Activity 复用：整体套主题。 */
@Composable
fun SpotlightSettingsContent(onBack: () -> Unit, onThemeChange: (ThemeMode) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    SpotlightTheme(themeMode = prefs.themeMode) {
        SettingsScreen(onBack = onBack, onThemeChange = onThemeChange)
    }
}
