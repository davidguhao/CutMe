package com.guhao.opensource.cutme.android

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun PieceCard(
    modifier: Modifier = Modifier, // have to specify the alpha = 0.99f in normal cases.
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        modifier = modifier.drawWithContent {
            drawContent()

            val radius = size.height / 14
            val threePosition = size.center.y.let { centerY ->
                listOf(centerY, centerY / 2, centerY / 2 * 3)
            }
            val strokeWidth = 4f
            // Left 3
            threePosition.forEach { y ->
                drawArc(
                    color = Color.Transparent,
                    startAngle = 270f,
                    sweepAngle = 180f,
                    useCenter = false,
                    size = Size(width = radius * 2, height = radius * 2),

                    topLeft = Offset(x = -radius, y = y - radius),
                    blendMode = BlendMode.Src,
                )
                drawArc(
                    color = Color.White,
                    startAngle = 270f,
                    sweepAngle = 180f,
                    useCenter = false,
                    size = Size(width = radius * 2, height = radius * 2),

                    topLeft = Offset(x = -radius, y = y - radius),
                    blendMode = BlendMode.Src,
                    style = Stroke(width = strokeWidth)
                )
            }
            // Right 3
            threePosition.forEach { y ->
                drawArc(
                    color = Color.Transparent,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    size = Size(width = radius * 2, height = radius * 2),

                    topLeft = Offset(x = size.width - radius, y = y - radius),
                    blendMode = BlendMode.Src,
                )
                drawArc(
                    color = Color.White,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    size = Size(width = radius * 2, height = radius * 2),

                    topLeft = Offset(x = size.width - radius, y = y - radius),
                    blendMode = BlendMode.Src,
                    style = Stroke(width = strokeWidth)
                )
            }
        },
        shape = RoundedCornerShape(0.dp),
        elevation = elevation,
        content = content
    )
}