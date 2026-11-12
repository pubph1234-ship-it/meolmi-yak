package app.motioncoach.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small, app-owned vectors: the APK does not ship an entire extended icon catalog. */
object CoachIcons {
    private fun icon(name: String, data: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(data).toNodes(), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    val Waves = icon("Waves", "M2 7 C6 1 8 13 12 7 S18 1 22 7 M2 12 C6 6 8 18 12 12 S18 6 22 12 M2 17 C6 11 8 23 12 17 S18 11 22 17")
    val Home = icon("Home", "M3 11 L12 3 L21 11 M5 10 V21 H10 V14 H14 V21 H19 V10")
    val History = icon("History", "M3 10 A9 9 0 1 1 5 18 M3 4 V10 H9 M12 7 V12 L16 14")
    val Settings = icon("Settings", "M9 12 A3 3 0 1 1 15 12 A3 3 0 1 1 9 12 M12 2 V5 M12 19 V22 M2 12 H5 M19 12 H22 M5 5 L7 7 M17 17 L19 19 M5 19 L7 17 M17 7 L19 5 M5 12 A7 7 0 1 1 19 12 A7 7 0 1 1 5 12")
    val Route = icon("Route", "M6 20 V16 C6 10 18 15 18 9 V5 M3 20 A3 3 0 1 1 9 20 M15 4 A3 3 0 1 1 21 4 A3 3 0 1 1 15 4")
    val AutoGraph = icon("AutoGraph", "M3 3 V21 H22 M6 16 L11 11 L15 13 L21 6 M16 6 H21 V11")
    val FavoriteBorder = icon("Care", "M12 21 L4 13 C-3 5 7 -1 12 6 C17 -1 27 5 20 13 Z")
    val PlayArrow = icon("Play", "M7 3 L21 12 L7 21 Z")
    val TurnLeft = icon("TurnLeft", "M19 21 V11 Q19 7 15 7 H4 M9 2 L4 7 L9 12")
    val TurnRight = icon("TurnRight", "M5 21 V11 Q5 7 9 7 H20 M15 2 L20 7 L15 12")
    val SentimentSatisfiedAlt = icon("Okay", "M3 12 A9 9 0 1 1 21 12 A9 9 0 1 1 3 12 M8 9 H8.1 M16 9 H16.1 M7 14 Q12 20 17 14")
    val SentimentNeutral = icon("Uneasy", "M3 12 A9 9 0 1 1 21 12 A9 9 0 1 1 3 12 M8 9 H8.1 M16 9 H16.1 M8 16 H16")
    val LocalCafe = icon("Rest", "M4 8 H17 V16 Q17 20 13 20 H8 Q4 20 4 16 Z M17 9 H19 Q23 9 22 13 Q22 16 17 16 M7 2 V5 M12 2 V5")
}
