package com.spotlight.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.spotlight.app.search.AppEntry
import com.spotlight.app.search.Fuzzy
import com.spotlight.app.ui.theme.Dimen
import com.spotlight.app.ui.theme.LocalSpotlightPalette

/** 分区小标题。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val palette = LocalSpotlightPalette.current
    Text(
        text = text,
        color = palette.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
        modifier = modifier.padding(start = 6.dp, bottom = 6.dp),
    )
}

/** iOS 风格卡片容器，负责圆角、描边与内部竖向留白。 */
@Composable
fun SpotlightCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    val cardInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Dimen.cardShape)
            .background(palette.cardBackground)
            .border(0.7.dp, palette.cardBorder, Dimen.cardShape)
            // 吞掉卡片内空白处的点击，避免触发「点空白关闭」
            .clickable(interactionSource = cardInteraction, indication = null) {}
            .padding(vertical = 5.dp),
        content = content,
    )
}

/** 一条可点击的行，按下时有轻微高亮。 */
@Composable
fun SpotlightRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp)
            .clip(Dimen.rowShape)
            .background(if (pressed) palette.pressedOverlay else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Dimen.RowHorizontalPadding, vertical = Dimen.RowVerticalPadding),
        content = content,
    )
}

@Composable
fun DividerLine(modifier: Modifier = Modifier, startPadding: Int = 0) {
    val palette = LocalSpotlightPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding.dp)
            .height(0.7.dp)
            .background(palette.divider),
    )
}

/** 应用图标，缺失时退化为圆角方块。 */
@Composable
fun AppIcon(bitmap: ImageBitmap?, size: Dp = 44.dp, corner: Dp = 12.dp) {
    val palette = LocalSpotlightPalette.current
    val shape = RoundedCornerShape(corner)
    if (bitmap != null) {
        Image(
            painter = remember(bitmap) { BitmapPainter(bitmap) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(palette.fieldBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", color = palette.textSecondary, fontSize = (size.value / 2.4).sp)
        }
    }
}

/** 应用搜索结果行。 */
@Composable
fun AppRow(
    entry: AppEntry,
    icon: ImageBitmap?,
    query: String,
    onClick: () -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    SpotlightRow(onClick = onClick) {
        AppIcon(icon, size = Dimen.AppIcon, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            HighlightedText(
                text = entry.label,
                query = query,
                style = TextStyle(fontSize = 16.sp, color = palette.textPrimary),
                highlightColor = palette.accent,
            )
            Text(
                text = entry.packageName,
                color = palette.textTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 带高亮的文本（模糊匹配命中的字符用主题色 + 加粗）。 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    highlightColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val annotated: AnnotatedString = remember(text, query, highlightColor) {
        val ranges = Fuzzy.highlightRanges(query, text)
        if (ranges.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                ranges.forEach { range ->
                    val start = range.first.coerceIn(0, text.length)
                    val end = (range.last + 1).coerceIn(start, text.length)
                    if (end > start) {
                        addStyle(
                            SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold),
                            start,
                            end,
                        )
                    }
                }
            }
        }
    }
    Text(text = annotated, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** 来源/状态小徽章。 */
@Composable
fun Badge(text: String) {
    val palette = LocalSpotlightPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(palette.badgeBackground)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = palette.textSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

/** 载入中的一行提示。 */
@Composable
fun LoadingRow(text: String) {
    val palette = LocalSpotlightPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Dimen.RowHorizontalPadding + 4.dp, vertical = 10.dp),
    ) {
        CircularProgressIndicator(
            color = palette.accent,
            strokeWidth = 1.8.dp,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = palette.textSecondary, fontSize = 13.sp)
    }
}

/** 无结果占位。 */
@Composable
fun EmptyState(title: String, hint: String) {
    val palette = LocalSpotlightPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 32.dp),
    ) {
        Text(title, color = palette.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = palette.textTertiary, fontSize = 13.sp)
    }
}

/** 图标网格：每行 4 个，用于「最近使用」「建议」。 */
@Composable
fun AppGrid(
    apps: List<AppEntry>,
    iconFor: (String) -> ImageBitmap?,
    onOpen: (AppEntry) -> Unit,
) {
    val palette = LocalSpotlightPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        apps.chunked(4).forEach { rowApps ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowApps.forEach { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpen(app) }
                            .padding(vertical = 4.dp),
                    ) {
                        AppIcon(iconFor(app.packageName), size = 54.dp, corner = 15.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = app.label,
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(4 - rowApps.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 小圆角胶囊按钮，用于示例词。 */
@Composable
fun Chip(text: String, onClick: () -> Unit) {
    val palette = LocalSpotlightPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.fieldBackground)
            .border(0.7.dp, palette.fieldBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = palette.textSecondary, fontSize = 12.sp)
    }
}

/** 空状态的占位尺寸，避免未使用告警。 */
@Composable
fun GridPlaceholder() {
    Box(modifier = Modifier.aspectRatio(1f).fillMaxWidth())
}
