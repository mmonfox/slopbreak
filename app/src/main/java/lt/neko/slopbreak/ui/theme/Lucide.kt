package lt.neko.slopbreak.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val LUCIDE_STROKE = 1.75f

internal fun lucide(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (path in paths) {
        builder.addPath(
            PathParser().parsePathString(path).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LUCIDE_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

object Lucide {
    val Ban by lazy { lucide("ban", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20", "m4.9 4.9 14.2 14.2") }
    val Check by lazy { lucide("check", "M20 6 9 17l-5-5") }
    val X by lazy { lucide("x", "M18 6 6 18", "m6 6 12 12") }
}
