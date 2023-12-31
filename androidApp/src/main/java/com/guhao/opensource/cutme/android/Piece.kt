package com.guhao.opensource.cutme.android

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import androidx.core.animation.addListener
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlin.coroutines.cancellation.CancellationException
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


enum class DraggingItemChangeReason {
    UPDATE, END, CANCEL
}
fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
    changes.firstOrNull { it.id == pointerId }?.pressed != true
suspend fun AwaitPointerEventScope.awaitLongPressOrCancellationMine(
    pointerId: PointerId
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the long press is cancelled.
    }

    val initialDown =
        currentEvent.changes.firstOrNull { it.id == pointerId } ?: return null

    var longPress: PointerInputChange? = null
    var currentDown = initialDown
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    return try {
        // wait for first tap up or long press
        withTimeout(longPressTimeout) {
            var finished = false
            while (!finished) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.all { it.changedToUpIgnoreConsumed() }) {
                    // All pointers are up
                    finished = true
                }

                if (
                    event.changes.any {
                        it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding)
                    }
                ) {
                    finished = true // Canceled
                }

                // Check for cancel by position consumption. We can look on the Final pass of
                // the existing pointer event because it comes after the Main pass we checked
                // above.
                val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
                if (consumeCheck.changes.any { it.isConsumed }) {
                    finished = true
                }
                if (event.isPointerUp(currentDown.id)) {
                    val newPressed = event.changes.firstOrNull { it.pressed }
                    if (newPressed != null) {
                        currentDown = newPressed
                        longPress = currentDown
                    } else {
                        // should technically never happen as we checked it above
                        finished = true
                    }
                    // Pointer (id) stayed down.
                } else {
                    longPress = event.changes.firstOrNull { it.id == currentDown.id }
                }
            }
        }
        null
    } catch (_: PointerEventTimeoutCancellationException) {
        longPress ?: initialDown
    }
}


suspend inline fun AwaitPointerEventScope.awaitDragOrUpMine(
    pointerId: PointerId,
    hasDragged: (PointerInputChange) -> Boolean
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        val dragEvent = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.firstOrNull { it.pressed }
            if (otherDown == null) {
                // This is the last "up"
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (hasDragged(dragEvent)) {
            return dragEvent
        }
    }
}
suspend fun AwaitPointerEventScope.awaitDragOrCancellationMine(
    pointerId: PointerId,
): PointerInputChange? {
    if (currentEvent.isPointerUp(pointerId)) {
        return null // The pointer has already been lifted, so the gesture is canceled
    }
    val change = awaitDragOrUpMine(pointerId) { it.positionChangedIgnoreConsumed() }
    return if (change?.isConsumed == false) change else null
}

suspend fun AwaitPointerEventScope.dragMine(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): Boolean {
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrCancellationMine(pointer) ?: return false

        if (change.changedToUpIgnoreConsumed()) {
            return true
        }

        onDrag(change)
        pointer = change.id
    }
}
suspend fun PointerInputScope.dragGesturesAfterLongPress(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
            val drag = awaitLongPressOrCancellationMine(down.id)
            if (drag != null) {
                onDragStart.invoke(drag.position)
                if (
                    dragMine(drag.id) {
                        onDrag(it, it.positionChange())
                        it.consume()
                    }
                ) {
                    // consume up if we quit drag gracefully with the up
                    currentEvent.changes.forEach {
                        if (it.changedToUp()) it.consume()
                    }
                    onDragEnd()
                } else {
                    onDragCancel()
                }
            }
        } catch (c: CancellationException) {
            onDragCancel()
            throw c
        }
    }
}

val pieceHeight = 70.dp

enum class ScaleState {
    ORIGINAL, SELECTED, FLYING
}
const val ANIMATION_DURATION = 250L // In milliseconds

@Composable
fun OriginalPosConcatAnimation(
    originalPosAnimationConcatenation: Int?,
    onPaddingChange: (Int) -> Unit,
    draggingItem: DraggingItem?,

    onAnimationFinish: () -> Unit
) {
    if(originalPosAnimationConcatenation != null && draggingItem == null) {
        val current = remember(key1 = originalPosAnimationConcatenation) {
            originalPosAnimationConcatenation.also {
                onPaddingChange.invoke(it) // Flash to the starting point!
            }
        }
        LaunchedEffect(key1 = current) {
            ValueAnimator.ofInt(current, 0).apply {
                duration = ANIMATION_DURATION
                addUpdateListener {
                    onPaddingChange.invoke(it.animatedValue as Int)
                }
                addListener(
                    onEnd = {
                        onAnimationFinish.invoke()
                    },
                    onCancel = {
                        onAnimationFinish.invoke()
                    }
                )
            }.start()
        }
    }
}

@Composable
fun TargetPosConcatAnimation(
    targetPosAnimationConcatenation: Offset?,
    currentRect: Rect,
    draggingItem: DraggingItem?,
    onOffsetChange: (Offset) -> Unit,
    onAnimationFinish: () -> Unit
) {
    fun Offset.calCurrentOffset(center: Offset): Offset {
        return if(center != Offset.Zero) {
            Offset(
                x = this.x - center.x,
                y = this.y - center.y
            )
        } else {
            // So if we can't know its accurate position, we just can't do anything on it...
            Offset.Zero
        }
    }

    if(targetPosAnimationConcatenation != null && draggingItem == null) {
        val currentOffset = remember(key1 = targetPosAnimationConcatenation, key2 = currentRect) {
            targetPosAnimationConcatenation.calCurrentOffset(currentRect.center).also {
                onOffsetChange.invoke(it)
            }
        }

        LaunchedEffect(key1 = currentOffset) {
            ValueAnimator.ofObject(OffsetValueAnimatorEvaluator(),
                currentOffset, Offset.Zero).apply {
                duration = ANIMATION_DURATION
                addUpdateListener {
                    onOffsetChange.invoke(it.animatedValue as Offset)
                }
                addListener(
                    onEnd = {
                        onAnimationFinish.invoke()
                    })
            }.start()
        }
    }
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
    onDraggingItemChange: (DraggingItemChangeReason, DraggingItem?) -> Unit,

    scrollingCompensationX: Int,

    piecesPaddingCompensationX: Float,
    onPiecesPaddingCompensationXChange: (Float) -> Unit,

    draggingOffsetState: MutableState<Offset> = remember { mutableStateOf(Offset.Zero) },
    onDraggingInScopeChange: (Boolean) -> Unit,
    enableDraggingDetector: Boolean,

    hasDroppingTarget: () -> Boolean,

    originalPosAnimationConcatenation: Int?,
    onOriginalPosAnimationConcatenationFinish: () -> Unit,
    targetPosAnimationConcatenation: Offset?,
    onTargetPosAnimationConcatenationFinish: () -> Unit,
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }

    var originalAnimationConcatenationPadding by remember { mutableIntStateOf(0) }
    OriginalPosConcatAnimation(
        originalPosAnimationConcatenation = originalPosAnimationConcatenation,
        onPaddingChange = { originalAnimationConcatenationPadding = it },
        draggingItem = draggingItem,
        onAnimationFinish = onOriginalPosAnimationConcatenationFinish
    )

    var targetAnimationConcatenationOffset by remember { mutableStateOf(Offset.Zero) }
    TargetPosConcatAnimation(
        targetPosAnimationConcatenation = targetPosAnimationConcatenation,
        currentRect = currentRect,
        draggingItem = draggingItem,
        onOffsetChange = { targetAnimationConcatenationOffset = it },
        onAnimationFinish = onTargetPosAnimationConcatenationFinish
    )

    var draggingOffset by draggingOffsetState

    var scrollingCompensationXRemoveProcess by remember { mutableIntStateOf(0) }
    val totalCompensationXForDraggingItem = if(draggingOffset != Offset.Zero) piecesPaddingCompensationX + scrollingCompensationX - scrollingCompensationXRemoveProcess else 0f
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    offset = IntOffset(
        x = (targetAnimationConcatenationOffset.x + draggingOffset.x + totalCompensationXForDraggingItem).roundToInt(),
        y = (targetAnimationConcatenationOffset.y + draggingOffset.y).roundToInt()
    )
    val flying = offset != IntOffset.Zero

    val latestScrollingCompensationX by remember { mutableIntStateOf(scrollingCompensationX) }.also {
        it.intValue = scrollingCompensationX
    }
    val returnToOldPlace = {
        if(hasDroppingTarget.invoke()) {
            draggingOffset = Offset.Zero // Flash back
        } else {
            ValueAnimator.ofObject(OffsetValueAnimatorEvaluator(),
                draggingOffset, Offset.Zero).apply {
                duration = ANIMATION_DURATION
                addUpdateListener {
                    draggingOffset = it.animatedValue as Offset
                }
            }.start()

            if(latestScrollingCompensationX > 0) ValueAnimator.ofInt(scrollingCompensationXRemoveProcess, latestScrollingCompensationX).apply {
                duration = ANIMATION_DURATION
                addUpdateListener {
                    scrollingCompensationXRemoveProcess = it.animatedValue as Int
                }
                addListener(
                    onEnd = {
                        scrollingCompensationXRemoveProcess = 0
                    },
                )
            }.start()
        }
    }

    val actualWidth = width * zoom
    TranslationXDraggingItemDetector(
        modifier = Modifier
            .zIndex(if (flying) 1f else 0f),

        draggingItem = draggingItem,
        enabled = enableDraggingDetector && !flying,
        onDraggingInScopeChange = onDraggingInScopeChange,
    ) { shouldPadding ->

        val density = LocalDensity.current.density

        onPiecesPaddingCompensationXChange.invoke(- shouldPadding.value * density)

        val screenWidth = LocalConfiguration.current.screenWidthDp
        val centerX = screenWidth * density / 2
        val isProgressLineOver = currentRect.left < centerX && currentRect.right > centerX

        val scaleState = if(flying) {
                if(hasDroppingTarget.invoke()) {
                    ScaleState.ORIGINAL
                } else
                    ScaleState.FLYING
            } else if(isProgressLineOver) ScaleState.ORIGINAL
            else if(selected) ScaleState.SELECTED else ScaleState.ORIGINAL

        var scale by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(key1 = scaleState) {
            fun scaleTo(target: Float) {
                val overScaleExtent = 0.1f
                val overScaleTarget = if(scale <= target) target + overScaleExtent else target - overScaleExtent

                ValueAnimator.ofFloat(scale, overScaleTarget).apply {
                    duration = 100
                    addUpdateListener { animator ->
                        scale = animator.animatedValue as Float
                    }
                    addListener(
                        onEnd = {
                            ValueAnimator.ofFloat(scale, target).apply {
                                duration = 70
                                addUpdateListener { scale = it.animatedValue as Float }
                            }.start()
                        }
                    )
                }.start()
            }

            when(scaleState) {
                ScaleState.ORIGINAL -> scaleTo(1f)
                ScaleState.SELECTED -> scaleTo(0.85f)
                ScaleState.FLYING -> scaleTo(1.15f)
            }
        }
        PieceCard(modifier = Modifier
            .onGloballyPositioned {
                currentRect = it.boundsInWindow()
            } // I need to know its position even if it is out of the window we are using.
            .padding(start = shouldPadding + originalAnimationConcatenationPadding.dp)
            .pointerInput(Unit) {
                dragGesturesAfterLongPress(
                    onDragStart = {

                    },
                    onDrag = { _: PointerInputChange, dragAmount: Offset ->
                        draggingOffset += dragAmount

                        val draggingPos = currentRect.center + offset.toOffset()
                        onDraggingItemChange.invoke(
                            DraggingItemChangeReason.UPDATE,
                            DraggingItem(
                                position = draggingPos,
                                width = currentRect.width.toDp(),
                            )
                        )

                    },
                    onDragEnd = {
                        returnToOldPlace.invoke()
                        onDraggingItemChange.invoke(
                            DraggingItemChangeReason.END,
                            null
                        )
                    },
                    onDragCancel = {
                        returnToOldPlace.invoke()
                        onDraggingItemChange.invoke(
                            DraggingItemChangeReason.CANCEL,
                            null
                        )
                    }
                )
            }
            .offset {
                offset
            } // Pixel
            .graphicsLayer(
                alpha = 0.99f,
                scaleX = scale,
                scaleY = scale
            ),
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

                    for(i in 0 until nFrameShouldShow) {
                        if(piece.model != null) {
                            GlideImage(
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
                        } else {
                            Icon(
                                modifier = Modifier.height(pieceHeight)
                                    .graphicsLayer(rotationZ = 45f)
                                    .width(actualWidth / nFrameShouldShow),
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Blank piece",
                                tint = Color.White
                            )
                        }
                    }
                }

            }
        }
    }
}