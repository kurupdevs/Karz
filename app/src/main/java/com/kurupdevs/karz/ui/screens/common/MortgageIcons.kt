package com.kurupdevs.karz.ui.screens.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Local vector icons for glyphs the component library does not ship
 * (calendar, document). Tint recolors them via Icon(tint = ...).
 */
object MortgageIcons {

    val Doc: ImageVector by lazy {
        ImageVector.Builder(
            name = "Doc", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 2f)
                horizontalLineTo(14f)
                lineTo(20f, 8f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(7f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(14f, 3.5f)
                verticalLineTo(8f)
                horizontalLineTo(18.5f)
                close()
            }
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Calendar", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5f, 6f)
                horizontalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(8f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(3f, 10f)
                horizontalLineTo(21f)
                verticalLineTo(11.5f)
                horizontalLineTo(3f)
                close()
                moveTo(7.5f, 2.5f)
                horizontalLineTo(9.5f)
                verticalLineTo(6f)
                horizontalLineTo(7.5f)
                close()
                moveTo(14.5f, 2.5f)
                horizontalLineTo(16.5f)
                verticalLineTo(6f)
                horizontalLineTo(14.5f)
                close()
            }
        }.build()
    }
}
