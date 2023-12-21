package com.guhao.opensource.cutme.android

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
import androidx.compose.ui.zIndex
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
    targetPosAnimationConcatenation: Offset?,
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }

    var originalAnimationConcatenationPadding by remember { mutableIntStateOf(0) }
    LaunchedEffect(key1 = originalPosAnimationConcatenation) {
        if(originalPosAnimationConcatenation == null || draggingItem != null) return@LaunchedEffect

        ValueAnimator.ofInt(originalPosAnimationConcatenation, 0).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { originalAnimationConcatenationPadding = it.animatedValue as Int }
        }.start()
    }

    var targetAnimationConcatenationOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(key1 = targetPosAnimationConcatenation) {
        if(targetPosAnimationConcatenation == null || draggingItem != null) return@LaunchedEffect

        val startPosOffset = targetPosAnimationConcatenation.let {
            val x = it.x - currentRect.center.x
            val y = it.y - currentRect.center.y
            Offset(x, y)
        }

        ValueAnimator.ofFloat(startPosOffset.x, 0f).apply {
            duration = ANIMATION_DURATION
            addUpdateListener {
                targetAnimationConcatenationOffset = targetAnimationConcatenationOffset.copy(x = it.animatedValue as Float)
            }
        }.start()

        ValueAnimator.ofFloat(startPosOffset.y, 0f).apply {
            duration = ANIMATION_DURATION
            addUpdateListener {
                targetAnimationConcatenationOffset = targetAnimationConcatenationOffset.copy(y = it.animatedValue as Float)
            }
        }.start()

    }
    var draggingOffset by draggingOffsetState


    val actualWidth = width * zoom
    var scrollingCompensationXRemoveProcess by remember { mutableFloatStateOf(0f) }

    val draggingCausedFlying = draggingOffset != Offset.Zero
    val offset = IntOffset(
        x = (targetAnimationConcatenationOffset.x + draggingOffset.x + piecesPaddingCompensationX + scrollingCompensationX - if(draggingCausedFlying) scrollingCompensationXRemoveProcess else 0f).roundToInt(),
        y = (targetAnimationConcatenationOffset.y + draggingOffset.y).roundToInt()
    )
    val flying = offset != IntOffset.Zero

    val currentTotalCompensation by remember { mutableFloatStateOf(piecesPaddingCompensationX + scrollingCompensationX) }.also {
        it.floatValue = piecesPaddingCompensationX + scrollingCompensationX
    }

    val returnToOldPlace = {
        if(hasDroppingTarget.invoke()) {
            draggingOffset = Offset.Zero // Flash back
        } else {
            ValueAnimator
                .ofFloat(draggingOffset.x, 0f)
                .apply {
                    duration = ANIMATION_DURATION
                    addUpdateListener {
                        draggingOffset = draggingOffset.copy(x = it.animatedValue as Float)
                    }
                }
                .start()

            ValueAnimator.ofFloat(scrollingCompensationXRemoveProcess, currentTotalCompensation).apply {
                duration = ANIMATION_DURATION
                addUpdateListener { scrollingCompensationXRemoveProcess = it.animatedValue as Float }
            }.start()

            ValueAnimator.ofFloat(draggingOffset.y, 0f).apply {
                duration = ANIMATION_DURATION
                addUpdateListener { draggingOffset = draggingOffset.copy(y = it.animatedValue as Float) }
            }.start()
        }
    }

    TranslationXDraggingItemDetector(
        modifier = Modifier
            .zIndex(if (flying) 1f else 0f),

        draggingItem = draggingItem,
        enabled = enableDraggingDetector && !flying,
        onOffsetChange = onPiecesPaddingCompensationXChange,
        onDraggingInScopeChange = onDraggingInScopeChange
    ) { shouldPadding ->

        var currentCompensation by remember { mutableFloatStateOf(0f) }
        currentCompensation = piecesPaddingCompensationX + scrollingCompensationX - scrollingCompensationXRemoveProcess

        val screenWidth = LocalConfiguration.current.screenWidthDp
        val density = LocalDensity.current.density
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
                    duration = 80
                    addUpdateListener { animator ->
                        scale = animator.animatedValue as Float

                        // About end
                        if(scale - overScaleTarget < 0.01f) {
                            ValueAnimator.ofFloat(scale, target).apply {
                                duration = 70
                                addUpdateListener { scale = it.animatedValue as Float }
                            }.start()
                        }
                    }
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
                        scrollingCompensationXRemoveProcess = 0f
                    },
                    onDrag = { _: PointerInputChange, dragAmount: Offset ->
                        draggingOffset += dragAmount

                        onDraggingItemChange.invoke(
                            DraggingItemChangeReason.UPDATE,
                            DraggingItem(
                                position = currentRect.center + draggingOffset.let { it.copy(x = it.x + currentCompensation) },
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
            )
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
}