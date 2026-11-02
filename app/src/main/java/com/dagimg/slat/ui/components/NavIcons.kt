package com.dagimg.slat.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val STROKE = 1.8f
private const val TONE = 0.42f

data class NavIcon(
    val outline: ImageVector,
    val filled: ImageVector,
)

object NavIcons {
    val Clipboard = navIcon("Clipboard", shape = { board() }, detail = { clipAndLines() })
    val Settings = navIcon("Settings", shape = { gear() })
}

private fun navIcon(
    name: String,
    shape: PathBuilder.() -> Unit,
    detail: PathBuilder.() -> Unit = {},
): NavIcon = NavIcon(icon(name, false, shape, detail), icon("${name}Filled", true, shape, detail))

private fun icon(
    name: String,
    toned: Boolean,
    shape: PathBuilder.() -> Unit,
    detail: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(name, 24.dp, 24.dp, 24f, 24f)
        .apply {
            if (toned) path(fill = SolidColor(Color.Black), fillAlpha = TONE, pathFillType = PathFillType.EvenOdd, pathBuilder = shape)
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = {
                    shape()
                    detail()
                },
            )
        }.build()

private fun PathBuilder.board() {
    moveTo(7.5f, 5.25f)
    horizontalLineTo(16.5f)
    quadTo(19f, 5.25f, 19f, 7.75f)
    verticalLineTo(18f)
    quadTo(19f, 20.5f, 16.5f, 20.5f)
    horizontalLineTo(7.5f)
    quadTo(5f, 20.5f, 5f, 18f)
    verticalLineTo(7.75f)
    quadTo(5f, 5.25f, 7.5f, 5.25f)
    close()
}

private fun PathBuilder.clipAndLines() {
    moveTo(10.3f, 3.1f)
    horizontalLineTo(13.7f)
    quadTo(15f, 3.1f, 15f, 4.4f)
    verticalLineTo(6f)
    quadTo(15f, 7.3f, 13.7f, 7.3f)
    horizontalLineTo(10.3f)
    quadTo(9f, 7.3f, 9f, 6f)
    verticalLineTo(4.4f)
    quadTo(9f, 3.1f, 10.3f, 3.1f)
    close()
    moveTo(8.75f, 11.5f)
    horizontalLineTo(15.25f)
    moveTo(8.75f, 15.25f)
    horizontalLineTo(12.75f)
}

private fun PathBuilder.gear() {
    val teeth = 8
    val step = 2 * PI / teeth
    val tip = step * 0.18
    val base = step * 0.29
    val outer = 9.4f
    val inner = 7.3f

    fun x(
        r: Float,
        a: Double,
    ) = (12 + r * cos(a)).toFloat()

    fun y(
        r: Float,
        a: Double,
    ) = (12 + r * sin(a)).toFloat()

    for (i in 0 until teeth) {
        val c = i * step - PI / 2
        if (i == 0) {
            moveTo(x(inner, c - base), y(inner, c - base))
        } else {
            arcTo(inner, inner, 0f, false, true, x(inner, c - base), y(inner, c - base))
        }
        lineTo(x(outer, c - tip), y(outer, c - tip))
        arcTo(outer, outer, 0f, false, true, x(outer, c + tip), y(outer, c + tip))
        lineTo(x(inner, c + base), y(inner, c + base))
    }
    arcTo(inner, inner, 0f, false, true, x(inner, -PI / 2 - base), y(inner, -PI / 2 - base))
    close()
    moveTo(9f, 12f)
    arcTo(3f, 3f, 0f, true, true, 15f, 12f)
    arcTo(3f, 3f, 0f, true, true, 9f, 12f)
    close()
}
