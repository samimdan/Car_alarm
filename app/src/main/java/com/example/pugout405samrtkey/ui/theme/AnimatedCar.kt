package com.example.pugout405samrtkey.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import kotlin.math.min

import android.graphics.PathMeasure as AndroidPathMeasure
data class CarContour(
    val path: Path,
    val length: Float
)
@Composable
fun AnimatedCar(
    isLocked: Boolean,
    modifier: Modifier = Modifier

) {
    val contours = remember(Car) { extractCarContours(Car) }
    val totalLength = remember(contours) { contours.sumOf { it.length.toDouble() }.toFloat() }
    val reverseContours = remember(contours) { contours.asReversed() }
    val infinite = rememberInfiniteTransition(label = "car")
    val drawProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 20000
                0f at 0 using LinearEasing
                1f at 10000 using LinearEasing
                1f at 20000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "draw_progress"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val carColor by animateColorAsState(
        targetValue = if (isLocked) Color(0xFF00BFFF) else Color(0xFF00FFA3),
        animationSpec = tween(600),
        label = "color"
    )
    val carScale by animateFloatAsState(
        targetValue = if (isLocked) 0.97f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Animated outline of a Peugeot 405"
        }
    ) {
        if (totalLength <= 0f || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }

        val viewportWidth = Car.viewportWidth
        val viewportHeight = Car.viewportHeight
        val fitScale = min(size.width / viewportWidth, size.height / viewportHeight) * carScale
        val drawnWidth = viewportWidth * fitScale
        val drawnHeight = viewportHeight * fitScale
        val left = (size.width - drawnWidth) / 2f
        val top = (size.height - drawnHeight) / 2f
        val visibleLength = totalLength * drawProgress
        var traversedLength = 0f

        withTransform({
            translate(left, top)
            scale(fitScale, fitScale, pivot = Offset.Zero)
        }) {
            reverseContours.forEach { contour ->
                val segmentLength = (visibleLength - traversedLength)
                    .coerceIn(0f, contour.length)
                traversedLength += contour.length

                if (segmentLength > 0f) {
                    val segment = android.graphics.Path()
                    val measure = AndroidPathMeasure(contour.path.asAndroidPath(), false)
                    measure.getSegment(
                        contour.length - segmentLength,
                        contour.length,
                        segment,
                        true
                    )
                    val composeSegment = segment.asComposePath()

                    drawPath(
                        path = composeSegment,
                        color = carColor.copy(alpha = glow * 0.28f),
                        style = Stroke(
                            width = 8.dp.toPx() / fitScale,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    drawPath(
                        path = composeSegment,
                        color = carColor.copy(alpha = glow),
                        style = Stroke(
                            width = 2.5.dp.toPx() / fitScale,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}
fun extractCarContours(
    vector: ImageVector
): List<CarContour> {

    val contours = mutableListOf<CarContour>()

    for (node in vector.root) {

        if (node !is VectorPath) continue

        val originalPath = PathParser()
            .addPathNodes(node.pathData)
            .toPath()

        val measure = AndroidPathMeasure(
            originalPath.asAndroidPath(),
            false
        )

        do {

            val length = measure.length

            if (length > 0f) {

                val contour = android.graphics.Path()

                val closed = measure.isClosed()

                measure.getSegment(
                    0f,
                    length,
                    contour,
                    true
                )

                if (closed) {
                    contour.close()
                }

                contours.add(
                    CarContour(
                        path = contour.asComposePath(),
                        length = length
                    )
                )
            }

        } while (measure.nextContour())
    }

    return contours
}

private val Car: ImageVector
    get() {
        if (_Car != null) return _Car!!

        _Car = ImageVector.Builder(
            name = "Car",
            defaultWidth = 587.884.dp,
            defaultHeight = 164.852.dp,
            viewportWidth = 587.884f,
            viewportHeight = 164.852f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(352.579f, 68.304f)
                curveTo(397.739f, 66.634f, 522.459f, 52.524f, 573.199f, 108.924f)
                curveTo(574.049f, 109.864f, 575.429f, 110.104f, 576.559f, 109.534f)
                curveTo(578.179f, 108.724f, 578.639f, 106.614f, 577.499f, 105.204f)
                curveTo(528.099f, 44.524f, 405.729f, 57.104f, 391.149f, 58.844f)
                curveTo(390.239f, 58.944f, 389.349f, 58.604f, 388.729f, 57.934f)
                curveTo(346.109f, 11.654f, 309.849f, 5.804f, 242.829f, 1.614f)
                curveTo(175.289f, -2.606f, 63.847f, 49.734f, 41.477f, 53.104f)
                curveTo(24.047f, 55.744f, 10.967f, 54.274f, 6.047f, 53.494f)
                curveTo(4.677f, 53.274f, 3.367f, 54.074f, 2.917f, 55.374f)
                curveTo(1.997f, 58.024f, 1.157f, 62.634f, 3.907f, 67.204f)
                curveTo(8.127f, 74.214f, 306.989f, 69.994f, 352.579f, 68.304f)
                close()
                moveTo(253.409f, 12.604f)
                curveTo(336.579f, 13.364f, 369.719f, 43.464f, 379.499f, 55.224f)
                curveTo(380.789f, 56.774f, 380.049f, 59.004f, 378.339f, 59.664f)
                curveTo(379.919f, 56.644f, 374.389f, 52.304f, 372.179f, 51.084f)
                lineTo(372.049f, 51.014f)
                curveTo(368.869f, 49.534f, 363.789f, 48.184f, 360.259f, 49.304f)
                curveTo(357.999f, 50.564f, 357.599f, 54.024f, 358.239f, 56.344f)
                curveTo(358.879f, 58.354f, 361.199f, 59.334f, 363.089f, 59.744f)
                curveTo(363.282f, 59.784f, 363.472f, 59.824f, 363.659f, 59.864f)
                lineTo(258.749f, 59.864f)
                curveTo(257.319f, 59.864f, 256.109f, 58.804f, 255.929f, 57.384f)
                lineTo(250.579f, 15.804f)
                curveTo(250.369f, 14.094f, 251.699f, 12.584f, 253.409f, 12.604f)
                close()
                moveTo(37.967f, 53.594f)
                curveTo(46.157f, 56.184f, 54.637f, 57.544f, 63.137f, 58.564f)
                curveTo(88.677f, 61.544f, 109.349f, 60.354f, 135.049f, 60.694f)
                curveTo(168.789f, 61.024f, 204.189f, 60.404f, 237.969f, 59.744f)
                curveTo(168.829f, 57.024f, 124.879f, 59.444f, 108.169f, 55.644f)
                curveTo(105.469f, 55.024f, 105.839f, 40.554f, 108.409f, 39.524f)
                curveTo(123.829f, 33.344f, 164.849f, 20.994f, 189.219f, 16.384f)
                curveTo(216.199f, 11.284f, 226.249f, 14.184f, 234.759f, 12.994f)
                curveTo(236.389f, 12.774f, 237.859f, 13.964f, 237.979f, 15.604f)
                lineTo(240.909f, 56.704f)
                curveTo(241.029f, 58.394f, 239.659f, 59.804f, 237.969f, 59.744f)
                curveTo(203.729f, 62.414f, 169.369f, 63.464f, 135.019f, 63.224f)
                curveTo(107.139f, 62.734f, 84.117f, 63.704f, 56.607f, 58.664f)
                curveTo(54.787f, 58.334f, 52.057f, 57.754f, 50.297f, 57.324f)
                curveTo(46.097f, 56.414f, 41.977f, 55.114f, 37.967f, 53.594f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(57.567f, 75.904f)
                curveTo(57.567f, 75.904f, 49.117f, 94.474f, 40.677f, 98.694f)
                curveTo(32.237f, 102.914f, 0.157f, 98.694f, 0.157f, 98.694f)
                lineTo(34.347f, 94.054f)
                lineTo(42.787f, 81.394f)
                lineTo(0.157f, 73.374f)
                curveTo(0.157f, 73.374f, 49.117f, 76.744f, 57.567f, 75.904f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(72.637f, 120.584f)
                curveTo(78.367f, 120.574f, 84.067f, 121.384f, 89.557f, 122.964f)
                curveTo(90.267f, 123.184f, 93.967f, 124.434f, 94.587f, 124.624f)
                curveTo(96.017f, 125.194f, 97.997f, 126.114f, 99.427f, 126.704f)
                curveTo(99.917f, 126.904f, 100.857f, 127.354f, 101.319f, 127.614f)
                curveTo(101.319f, 127.614f, 104.999f, 129.594f, 104.999f, 129.594f)
                curveTo(107.189f, 130.704f, 109.889f, 132.724f, 112.019f, 134.024f)
                curveTo(116.409f, 137.204f, 120.719f, 140.834f, 124.579f, 144.614f)
                curveTo(126.169f, 146.334f, 128.659f, 148.854f, 130.119f, 150.614f)
                curveTo(121.029f, 144.674f, 111.999f, 138.934f, 102.379f, 134.784f)
                curveTo(101.669f, 134.484f, 98.547f, 133.164f, 97.887f, 132.884f)
                curveTo(97.327f, 132.694f, 95.697f, 132.134f, 95.137f, 131.934f)
                curveTo(91.047f, 130.434f, 86.427f, 129.354f, 82.147f, 128.614f)
                curveTo(57.297f, 124.584f, 35.607f, 138.634f, 20.377f, 157.374f)
                curveTo(29.167f, 136.854f, 49.787f, 120.684f, 72.637f, 120.584f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(466.889f, 107.924f)
                curveTo(472.609f, 107.914f, 478.319f, 108.724f, 483.809f, 110.294f)
                curveTo(484.519f, 110.524f, 488.219f, 111.764f, 488.839f, 111.964f)
                curveTo(490.259f, 112.534f, 492.249f, 113.444f, 493.679f, 114.044f)
                curveTo(494.169f, 114.244f, 495.109f, 114.684f, 495.569f, 114.954f)
                curveTo(495.569f, 114.954f, 499.249f, 116.934f, 499.249f, 116.934f)
                curveTo(501.439f, 118.044f, 504.139f, 120.054f, 506.269f, 121.364f)
                curveTo(510.659f, 124.534f, 514.969f, 128.174f, 518.829f, 131.954f)
                curveTo(520.419f, 133.674f, 522.899f, 136.194f, 524.369f, 137.954f)
                curveTo(515.269f, 132.014f, 506.239f, 126.264f, 496.629f, 122.114f)
                curveTo(495.919f, 121.824f, 492.799f, 120.504f, 492.139f, 120.224f)
                curveTo(491.579f, 120.034f, 489.949f, 119.474f, 489.389f, 119.274f)
                curveTo(485.299f, 117.774f, 480.679f, 116.684f, 476.399f, 115.954f)
                curveTo(451.549f, 111.924f, 429.859f, 125.974f, 414.619f, 144.704f)
                curveTo(423.419f, 124.194f, 444.039f, 108.024f, 466.889f, 107.924f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(346.239f, 147.664f)
                curveTo(293.959f, 142.964f, 241.249f, 142.544f, 188.809f, 143.394f)
                curveTo(173.019f, 143.504f, 143.389f, 126.614f, 129.139f, 118.244f)
                curveTo(124.899f, 115.624f, 120.619f, 113.054f, 116.789f, 109.834f)
                curveTo(119.909f, 113.804f, 123.809f, 117.084f, 127.649f, 120.344f)
                curveTo(142.139f, 132.124f, 169.889f, 151.354f, 188.809f, 151.924f)
                curveTo(241.259f, 152.774f, 293.959f, 152.354f, 346.239f, 147.664f)
                close()
            }
        }.build()

        return _Car!!
}

private var _Car: ImageVector? = null
