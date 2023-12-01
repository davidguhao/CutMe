package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.guhao.opensource.cutme.millisTimeFormat
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt


class Piece(
    val model: Any?,
    val duration: Long
)

class Track(
    val pieces: List<Piece>
)

@Composable
fun Track(
    track: Track,
    onTrackChange: (Track) -> Unit,

    selectedSet: Set<Piece>,
    onSelectedSetChange: (Set<Piece>) -> Unit,

    requestAdding: ((List<SelectInfo>) -> Unit) -> Unit,

    zoom: Float = 1f,
    invalidateZoom: () -> Unit,

    longestDuration: Long,
    ) {

    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    /**
     * Every piece will have a calculated width for it.
     */
    var trackLength = 0f
    val piece2Width = HashMap<Piece, Int>().apply {
        track.pieces.forEach { piece ->
            (screenWidthDp * (piece.duration / longestDuration.toFloat())).roundToInt().let {
                this[piece] = it
                trackLength += it
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Spacer(modifier = Modifier.width((screenWidthDp / 2).dp))

        track.pieces.forEach { piece ->

            val selected = selectedSet.contains(piece)
            Piece(
                zoom = zoom,
                piece = piece,
                width = piece2Width[piece]!!.dp,
                selected = selected,
                onClick = {
                    onSelectedSetChange(if (selected) {
                        selectedSet
                            .filter { it != piece }
                            .toSet()
                    } else HashSet(selectedSet).apply { add(piece) })
                },
                onLongClick = {
                    onSelectedSetChange(setOf())
                },
                onDragStart = {
                    invalidateZoom.invoke()
                },
                onDragEnd = {
                },
                onDragCancel = {
                }
            )

        }
        IconButton(
            modifier = Modifier
                .padding(
                    start = 10.dp,
                    top = 10.dp,
                    bottom = 10.dp),
            onClick = {
                requestAdding.invoke { result: List<SelectInfo> ->
                    onTrackChange(Track(track.pieces + result.map { Piece(model = it.path, duration = it.duration?:2000) } ))
                }
            },
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.White)
        }

        Spacer(modifier = Modifier.width((screenWidthDp / 2 - 58).dp))

    }
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
) {
    val actualWidth = width * zoom

    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .zIndex(if (offset != Offset.Zero) 1f else 0f)
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
        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }) {

        Card(
            modifier = Modifier
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
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            AnimatedContent(targetState = selected || dragging, label = "") { halfAlpha ->
                val nFrameShouldShow = (actualWidth.value / 40).toInt().let { if(it == 0) 1 else it}

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
                    for(i in 0 until nFrameShouldShow) GlideImage(
                        modifier = Modifier
                            .height(70.dp)
                            .width(actualWidth / nFrameShouldShow),
                        contentScale = ContentScale.Crop,
                        model = piece.model,
                        contentDescription = "",
                        requestBuilderTransform = { it.apply {

                            val usedResult = frame(piece.duration / nFrameShouldShow * i * 1000)
                        }}
                    )
                }

            }

        }

    }
}


suspend fun PointerInputScope.transformGestures(
    panZoomLock: Boolean = false,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false

        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = true)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange, effectiveRotation)
                    }
//                    event.changes.forEach {
//                        if (it.positionChanged()) {
//                            it.consume()
//                        }
//                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Control(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    onTracksChange: (List<Track>) -> Unit,

    requestAdding: ((List<SelectInfo>) -> Unit) -> Unit
) {
    var selectedSet by remember { mutableStateOf(setOf<Piece>()) }
    val selectionMode = selectedSet.isNotEmpty()

    var zoom by remember { mutableFloatStateOf(1f) }

    Box(modifier = modifier.pointerInput(Unit) {
        transformGestures(
            onGesture = { _, _, gestureZoom, _ ->
                zoom *= gestureZoom
                if(zoom < 1) zoom = 1f
            }
        )

    }) {
        val horizontalScrollState = rememberScrollState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)) {
            items(items = tracks) { track ->
                val longestDuration = let {
                    var res = 0L
                    tracks.forEach { t ->
                        var sum = 0L
                        t.pieces.forEach { p ->
                            sum += p.duration
                        }
                        res = res.coerceAtLeast(sum)
                    }
                    res
                }

                Track(
                    track = track,
                    onTrackChange = { it: Track ->
                        onTracksChange.invoke(ArrayList(tracks).apply {
                            val index = indexOf(track)
                            this[index] = it
                            if(index == lastIndex) add(Track(listOf()))
                        })
                    },

                    selectedSet = selectedSet,
                    onSelectedSetChange = { selectedSet = it },

                    requestAdding = requestAdding,

                    zoom = zoom,
                    invalidateZoom = {
                        ValueAnimator.ofFloat(zoom, 1f).apply {
                            duration = 1000
                            addUpdateListener {
                                zoom = it.animatedValue as Float
                            }
                        }.start()
                    },
                    longestDuration = longestDuration,
                )
            }
        }

        val screenHeightPixel = LocalConfiguration.current.let {
            it.screenHeightDp * it.densityDpi
        }
        Canvas(modifier = Modifier.align(Alignment.TopCenter)) {
            drawLine(
                color = Color.White,
                start = Offset(x = 0f, y = 0f),
                end = Offset(x = 0f, y = screenHeightPixel.toFloat()),)
        }

        Column(modifier = Modifier
            .padding(30.dp)
            .align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enter = fadeIn(), exit = fadeOut(),
                visible = selectionMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    modifier = Modifier.alpha(0.9f)
                ) {
                    val totalDuration = selectedSet.sumOf { it.duration }
                    Text(
                        text = "${totalDuration.millisTimeFormat()}(${selectedSet.size})",
                        color = Color.White)
                }
            }

            FlowRow {
                AnimatedVisibility(
                    visible = selectionMode
                ) {
                    IconButton(onClick = {
                        onTracksChange(ArrayList<Track>().apply {
                            tracks.forEach { track ->
                                val newPieces = track.pieces.filter { piece ->
                                    !selectedSet.contains(piece)
                                }

                                if(newPieces.isNotEmpty()) add(Track(newPieces))
                            }

                            if(isEmpty() || last().pieces.isNotEmpty()) add(Track(listOf()))
                        })

                        selectedSet = setOf()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White)
                    }
                }

                val cutAvailable = selectionMode && selectedSet.size == 1
                AnimatedVisibility(
                    visible = cutAvailable) {
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(
                            painter = painterResource(id = R.drawable.nc_sample_outline_design_scissors),
                            tint = Color.White,
                            contentDescription = "cut")
                    }
                }

            }
        }
    }
}
