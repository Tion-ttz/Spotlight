package com.spotlight.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 统一的设计尺寸。iOS Spotlight 的关键比例是「顶部搜索框 + 大圆角分组卡片」，
 * 这里集中管理，避免各个 composable 里散落魔法数字。
 */
object Dimen {
    val ScreenHorizontalPadding = 20.dp
    val SearchFieldHeight = 52.dp
    val SearchFieldCorner = 14.dp
    val CardCorner = 18.dp
    val RowCorner = 14.dp
    val AppIcon = 44.dp
    val SectionSpacing = 18.dp
    val ItemSpacing = 2.dp
    val RowHorizontalPadding = 14.dp
    val RowVerticalPadding = 10.dp

    val cardShape = RoundedCornerShape(CardCorner)
    val fieldShape = RoundedCornerShape(SearchFieldCorner)
    val rowShape = RoundedCornerShape(RowCorner)
}
