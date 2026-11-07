package com.spotlight.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotlight.app.R
import com.spotlight.app.core.SearchEngine
import com.spotlight.app.search.DictionaryLookup
import com.spotlight.app.search.OfflineWord
import com.spotlight.app.search.Translation
import com.spotlight.app.search.languageLabel
import com.spotlight.app.ui.theme.Dimen
import com.spotlight.app.ui.theme.LocalSpotlightPalette
import kotlinx.coroutines.delay

/**
 * Spotlight 主界面：顶部搜索框 + 分组结果卡片。
 * 内容全部来自 [SpotlightViewModel]，这里只负责渲染与交互。
 */
@Composable
fun SpotlightScreen(
    viewModel: SpotlightViewModel,
    initialQuery: String?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val palette = LocalSpotlightPalette.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChanged(initialQuery)
        }
        delay(120)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    val engine = viewModel.prefs.searchEngine
    val showWebSearch = viewModel.prefs.webSearchEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.scrimTop, palette.scrimBottom),
                )
            ),
    ) {
        // 点击空白处关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Dimen.ScreenHorizontalPadding),
        ) {
            Spacer(Modifier.height(44.dp))
            SpotlightSearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChanged,
                focusRequester = focusRequester,
                onClear = { viewModel.onQueryChanged("") },
                onOpenSettings = onOpenSettings,
                onSearchAction = { keyboard?.hide() },
            )
            Spacer(Modifier.height(16.dp))

            val query = state.query.trim()
            if (query.isEmpty()) {
                EmptyQueryContent(
                    state = state,
                    iconFor = viewModel::icon,
                    onOpen = { entry ->
                        if (viewModel.launch(entry)) onDismiss()
                    },
                    onExample = viewModel::onQueryChanged,
                    onDismiss = onDismiss,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        // 只占内容高度，露出的空白区域由背后的遮罩接管关闭
                        .fillMaxWidth()
                        .wrapContentHeight()
                        // 点击结果列表的空白处也能关闭
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val results = state.results

                    if (results.calculation != null) {
                        item {
                            SectionHeader(stringResource(R.string.calculator_result))
                            CalculationCard(
                                expression = query,
                                result = results.calculation,
                                onCopy = { copyToClipboard(context, results.calculation) },
                            )
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    if (results.apps.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.section_apps))
                            SpotlightCard {
                                results.apps.forEachIndexed { index, result ->
                                    if (index > 0) DividerLine(startPadding = 66)
                                    AppRow(
                                        entry = result.entry,
                                        icon = viewModel.icon(result.entry.packageName),
                                        query = query,
                                        onClick = {
                                            if (viewModel.launch(result.entry)) onDismiss()
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    if (results.dictionaryLoading && results.dictionary == null) {
                        item {
                            SectionHeader(stringResource(R.string.section_dictionary))
                            SpotlightCard { LoadingRow(stringResource(R.string.dictionary_loading)) }
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    val dictionary = results.dictionary
                    if (dictionary != null && !dictionary.isEmpty) {
                        item {
                            SectionHeader(stringResource(R.string.section_dictionary))
                            DictionaryCard(
                                lookup = dictionary,
                                onWordClick = viewModel::onQueryChanged,
                            )
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    if (results.translationLoading && results.translation == null) {
                        item {
                            SectionHeader(stringResource(R.string.section_translation))
                            SpotlightCard { LoadingRow(stringResource(R.string.translation_loading)) }
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    val translation = results.translation
                    if (translation != null) {
                        item {
                            SectionHeader(stringResource(R.string.section_translation))
                            TranslationCard(
                                translation = translation,
                                onCopy = { copyToClipboard(context, translation.text) },
                            )
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    if (showWebSearch) {
                        item {
                            SectionHeader(stringResource(R.string.section_web))
                            SpotlightCard {
                                WebSearchRow(
                                    engine = engine,
                                    query = query,
                                    onClick = { openWebSearch(context, engine, query) },
                                )
                            }
                            Spacer(Modifier.height(Dimen.SectionSpacing))
                        }
                    }

                    if (!results.hasAnything && !results.dictionaryLoading && !results.translationLoading) {
                        item {
                            EmptyState(
                                title = stringResource(R.string.no_results),
                                hint = stringResource(R.string.no_results_hint),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SpotlightSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearchAction: () -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimen.SearchFieldHeight)
            .clip(Dimen.fieldShape)
            .background(palette.fieldBackground)
            .border(0.7.dp, palette.fieldBorder, Dimen.fieldShape)
            .padding(start = 14.dp, end = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 17.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            color = palette.textTertiary,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable { if (query.isEmpty()) onOpenSettings() else onClear() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (query.isEmpty()) Icons.Filled.Settings else Icons.Filled.Close,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun EmptyQueryContent(
    state: SpotlightUiState,
    iconFor: (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    onOpen: (com.spotlight.app.search.AppEntry) -> Unit,
    onExample: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        if (!state.appsReady) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在载入应用…", color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            if (state.recentApps.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.recent_apps))
                    AppGrid(apps = state.recentApps, iconFor = iconFor, onOpen = onOpen)
                    Spacer(Modifier.height(Dimen.SectionSpacing))
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.suggested_apps))
                    AppGrid(apps = state.suggestions, iconFor = iconFor, onOpen = onOpen)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.try_dictionary))
            val example1 = stringResource(R.string.example_word_1)
            val example2 = stringResource(R.string.example_word_2)
            val example3 = stringResource(R.string.example_word_3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(example1) { onExample(example1) }
                Chip(example2) { onExample(example2) }
                Chip(example3) { onExample(example3) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.empty_hint),
                color = palette.textTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CalculationCard(expression: String, result: String, onCopy: () -> Unit) {
    val palette = LocalSpotlightPalette.current
    SpotlightCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = expression,
                color = palette.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "= $result",
                    color = palette.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextAction(stringResource(R.string.copy), onCopy)
            }
        }
    }
}

@Composable
private fun DictionaryCard(lookup: DictionaryLookup, onWordClick: (String) -> Unit) {
    val palette = LocalSpotlightPalette.current
    val primary = lookup.primary
    val context = LocalContext.current
    SpotlightCard {
        if (primary != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = primary.word,
                        color = palette.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction(stringResource(R.string.copy)) {
                        copyToClipboard(context, dictionaryCopyText(lookup))
                    }
                    Spacer(Modifier.width(6.dp))
                    Badge(stringResource(R.string.dictionary_source_offline))
                }
                if (primary.phonetic.isNotBlank()) {
                    Text(
                        text = "/${primary.phonetic}/",
                        color = palette.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    if (primary.partOfSpeech.isNotBlank()) {
                        Text(
                            text = primary.partOfSpeech,
                            color = palette.textTertiary,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(
                        text = primary.meaning,
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (primary.example.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = primary.example,
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }

        val online = lookup.online
        if (online != null) {
            DividerLine()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "在线释义",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction(stringResource(R.string.copy)) {
                        copyToClipboard(context, dictionaryCopyText(lookup))
                    }
                    Spacer(Modifier.width(6.dp))
                    Badge(online.provider)
                }
                if (!online.phonetic.isNullOrBlank() && primary == null) {
                    Text(
                        text = "/${online.phonetic}/",
                        color = palette.accent,
                        fontSize = 13.sp,
                    )
                }
                online.senses.take(3).forEach { sense ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sense.partOfSpeech,
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                    )
                    sense.definitions.take(2).forEach { definition ->
                        Text(
                            text = "· ${definition.definition}",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        if (!definition.example.isNullOrBlank()) {
                            Text(
                                text = definition.example,
                                color = palette.textTertiary,
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(start = 10.dp, top = 1.dp),
                            )
                        }
                    }
                }
            }
        }

        val others = lookup.offline.filter { it.word != primary?.word }.take(4)
        if (others.isNotEmpty()) {
            DividerLine()
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                others.forEach { word: OfflineWord ->
                    SpotlightRow(onClick = { onWordClick(word.word) }) {
                        Text(
                            text = word.word,
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(110.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = word.meaning,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationCard(translation: Translation, onCopy: () -> Unit) {
    val palette = LocalSpotlightPalette.current
    SpotlightCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${languageLabel(translation.sourceLang)} → ${languageLabel(translation.targetLang)}",
                    color = palette.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Badge(
                    stringResource(
                        if (translation.provider == SpotlightViewModel.OFFLINE_PROVIDER) {
                            R.string.translation_source_offline
                        } else {
                            R.string.translation_source_online
                        },
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = translation.text,
                color = palette.textPrimary,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = translation.provider,
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                TextAction(stringResource(R.string.copy), onCopy)
            }
        }
    }
}

@Composable
private fun WebSearchRow(engine: SearchEngine, query: String, onClick: () -> Unit) {
    val palette = LocalSpotlightPalette.current
    SpotlightRow(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(Dimen.rowShape)
                .background(palette.fieldBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.web_search_for, query),
                color = palette.textPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = engine.label,
                color = palette.textTertiary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val palette = LocalSpotlightPalette.current
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = palette.accent, fontSize = 12.sp)
    }
}

/** 把词典卡片的内容拼成可复制的纯文本。 */
private fun dictionaryCopyText(lookup: DictionaryLookup): String {
    val builder = StringBuilder()
    val primary = lookup.primary
    if (primary != null) {
        builder.append(primary.word)
        if (primary.phonetic.isNotBlank()) builder.append(" /").append(primary.phonetic).append('/')
        builder.append('\n')
        if (primary.partOfSpeech.isNotBlank()) builder.append(primary.partOfSpeech).append(' ')
        builder.append(primary.meaning)
        if (primary.example.isNotBlank()) builder.append('\n').append(primary.example)
    }
    val online = lookup.online
    if (online != null) {
        if (builder.isNotEmpty()) builder.append("\n\n")
        online.senses.forEach { sense ->
            if (sense.partOfSpeech.isNotBlank()) builder.append(sense.partOfSpeech).append(' ')
            sense.definitions.forEach { definition ->
                builder.append("· ").append(definition.definition).append('\n')
            }
        }
    }
    return builder.toString().trim()
}

private fun copyToClipboard(context: Context, text: String) {    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("Spotlight", text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun openWebSearch(context: Context, engine: SearchEngine, query: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(engine.buildUrl(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, R.string.no_results_hint, Toast.LENGTH_SHORT).show()
    }
}
