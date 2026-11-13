package androidx.compose.material.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

object Icons {
    object Filled
    object Outlined
    object Rounded
    object TwoTone
    object Sharp

    val Default = Filled

    object AutoMirrored {
        object Filled
        object Outlined
        val Default = Filled
    }
}

internal inline fun buildMaterialIcon(
    name: String,
    autoMirror: Boolean = false,
    block: PathBuilder.() -> Unit
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply {
        addPath(
            pathData = PathBuilder().apply(block).nodes,
            pathFillType = PathFillType.NonZero,
            name = "",
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f
        )
    }.build()
}
