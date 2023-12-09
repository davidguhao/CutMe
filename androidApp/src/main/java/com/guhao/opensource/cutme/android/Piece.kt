package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlin.math.abs
import kotlin.math.roundToInt


data class DraggingItem(
    val position: Offset,
    val width: Dp,
)

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
@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun Piece(
    piece: Piece,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,

    zoom: Float = 1f,

    draggingItem: DraggingItem?,
    onDraggingItemChange: (DraggingItem?) -> Unit,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    val flying = offset != Offset.Zero
    var currentRect by remember { mutableStateOf(Rect.Zero) }

    val actualWidth = width * zoom
    val pieceHeight = 70.dp

    var translationXForDrag by remember { mutableFloatStateOf(0f) }
    fun isInScope(randomPoint: Offset): Boolean {
        if(flying) return false

        translationXForDrag.let {
            val coverCenter = Offset(
                x = currentRect.center.x - it / 2,
                y = currentRect.center.y
            )

            val xInScope = abs(randomPoint.x - coverCenter.x) < (it + currentRect.width) / 2
            val yInScope = abs(randomPoint.y - coverCenter.y) < currentRect.height / 2

            return xInScope && yInScope
        }
    }

    val draggingInScope = draggingItem?.let { isInScope(it.position) }?: false

    LaunchedEffect(key1 = draggingInScope) {
        val transTarget = if(draggingInScope) {
            // draggingItem!!.width.value * zoom
            20f
        } else 0f

        println("Current draggingItemWidth = $transTarget")
        ValueAnimator.ofFloat(translationXForDrag, transTarget).apply {
            duration = 250
            addUpdateListener { animator ->
                translationXForDrag = animator.animatedValue as Float
                println("CurrentTransValue $translationXForDrag")
            }
        }.start()
    }
    val returnToOldPlace = {
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
    }

    PieceCard(modifier = Modifier
        .padding(start = translationXForDrag.dp)
        .onGloballyPositioned { layoutCoordinates ->
            currentRect = layoutCoordinates.boundsInWindow()
        }
        .pointerInput(Unit) {
            dragGesturesAfterLongPress(

                onDragStart = {
                },
                onDrag = { _: PointerInputChange, dragAmount: Offset ->
                    offset += dragAmount

                    onDraggingItemChange.invoke(
                        DraggingItem(
                            position = currentRect.center + offset,
                            width = width,
                        )
                    )
                },
                onDragEnd = {
                    returnToOldPlace.invoke()
                    onDraggingItemChange.invoke(null)

                },
                onDragCancel = {
                    returnToOldPlace.invoke()
                    onDraggingItemChange.invoke(null)
                }
            )
        }
        .offset { IntOffset(x = offset.x.roundToInt(), y = offset.y.roundToInt()) }
        .zIndex(if (flying) 1f else 0f)
        .graphicsLayer(
            alpha = if (draggingInScope) 0.5f else 0.99f)

    ) {
        AnimatedContent(targetState = selected, label = "") { halfAlpha ->
            Row(
                modifier = Modifier
                    .alpha(if (halfAlpha) 0.2f else 1f)
                    .combinedClickable(
                        onClick = {
                            onClick.invoke()
                        },
                        onLongClick = {
                            onLongClick.invoke()
                        }
                    )
                    .width(actualWidth)

            ) {

                val expectingWidthForEachFrame = 40
                val nFrameShouldShow = (actualWidth.value / expectingWidthForEachFrame).toInt().let { if(it == 0) 1 else it}

                for(i in 0 until nFrameShouldShow) GlideImage(
                    modifier = Modifier
                        .height(pieceHeight)
                        .width(actualWidth / nFrameShouldShow),
                    contentScale = ContentScale.Crop,
                    model = piece.model,
                    contentDescription = "",
                    requestBuilderTransform = {
                        it.frame((piece.start + piece.duration / nFrameShouldShow * i) * 1000)
                    }
                )
            }

        }
    }

}