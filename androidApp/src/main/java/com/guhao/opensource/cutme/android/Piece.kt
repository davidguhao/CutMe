package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlin.math.roundToInt

class Piece(
    val model: Any?,
    val start: Long = 0, val end: Long) {
    val duration: Long
        get() = end - start

    fun cut(cutPoint: Long): Pair<Piece, Piece> {
        if(cutPoint in 0 until end - start) {
            return Pair(
                Piece(model = model, start = start, end = start + cutPoint),
                Piece(model = model, start = start + cutPoint, end = end))
        } else
            throw NotInValidScope("Can't cut it because it's not in valid scope($start until $end)")
    }

    class NotInValidScope(msg: String): Exception(msg)
}

class PieceState {
    var offset = mutableStateOf(Offset.Zero)
    fun isMoving(): Boolean = offset.value != Offset.Zero
}

@Composable
fun rememberPieceState(): PieceState {
    return remember { PieceState() }
}
@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun Piece(
    piece: Piece,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,

    zoom: Float = 1f,

    state: PieceState = rememberPieceState()
) {
    val actualWidth = width * zoom

    var offset by state.offset
    var dragging by remember { mutableStateOf(false) }
    val moving = state.isMoving()
    Card(
        modifier = Modifier
            .zIndex(if (moving) 1f else 0f)
            .pointerInput(Unit) {
                dragGesturesAfterLongPress(
                    onDrag = { _: PointerInputChange, dragAmount: Offset ->
                        offset += dragAmount
                    },
                    onDragStart = {
                        dragging = true

                        onDragStart.invoke()
                    },
                    onDragEnd = {
                        dragging = false

                        ValueAnimator
                            .ofFloat(offset.x, 0f)
                            .apply {
                                duration = 250
                                addUpdateListener {
                                    offset = offset.copy(x = it.animatedValue as Float)
                                }
                            }
                            .start()
                        ValueAnimator
                            .ofFloat(offset.y, 0f)
                            .apply {
                                duration = 250
                                addUpdateListener {
                                    offset = offset.copy(y = it.animatedValue as Float)
                                }
                            }
                            .start()

                        onDragEnd.invoke()
                    },
                    onDragCancel = {
                        dragging = false

                        ValueAnimator
                            .ofFloat(offset.x, 0f)
                            .apply {
                                duration = 250
                                addUpdateListener {
                                    offset = offset.copy(x = it.animatedValue as Float)
                                }
                            }
                            .start()
                        ValueAnimator
                            .ofFloat(offset.y, 0f)
                            .apply {
                                duration = 250
                                addUpdateListener {
                                    offset = offset.copy(y = it.animatedValue as Float)
                                }
                            }
                            .start()


                        onDragCancel.invoke()
                    }
                )
            }
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .alpha(0.99f)
            .drawWithContent {
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
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp, draggedElevation = 20.dp),
    ) {
        Box {
            AnimatedContent(targetState = selected || dragging, label = "") { halfAlpha ->
                Row(modifier = Modifier
                    .alpha(if (halfAlpha) 0.3f else 1f)
                    .combinedClickable(
                        onClick = {
                            onClick.invoke()
                        },
                        onLongClick = {
                            onLongClick.invoke()
                        }
                    )) {
                    val nFrameShouldShow = (actualWidth.value / 40).toInt().let { if(it == 0) 1 else it}
                    for(i in 0 until nFrameShouldShow) GlideImage(
                        modifier = Modifier
                            .height(70.dp)
                            .width(actualWidth / nFrameShouldShow),
                        contentScale = ContentScale.Crop,
                        model = piece.model,
                        contentDescription = "",
                        requestBuilderTransform = { it.apply {
                            frame((piece.start + piece.duration / nFrameShouldShow * i) * 1000)
                        }}
                    )
                }

            }

        }


    }
}