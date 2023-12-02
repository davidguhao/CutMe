package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.guhao.opensource.cutme.millisTimeFormat
import com.guhao.opensource.cutme.millisTimeStandardFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong

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

@Composable
fun ProgressHintLine(modifier: Modifier) {
    val screenHeightPixel = LocalConfiguration.current.let {
        it.screenHeightDp * it.densityDpi
    }

    Canvas(modifier = modifier) {
        drawLine(
            color = Color.White,
            start = Offset(x = 0f, y = 0f),
            end = Offset(x = 0f, y = screenHeightPixel.toFloat()),)
    }
}
@Composable
fun ProgressHintText(current: Long, modifier: Modifier) {
    Text(
        modifier = modifier,
        color = Color.White,
        text = current.millisTimeStandardFormat())
}

class ControlState {
    val progressState = ScrollState(initial = 0)
    fun calCurrentMillis(duration: Long): Long {
        return if(progressState.maxValue == 0)
            0
        else
            (duration * progressState.let { it.value / it.maxValue.toFloat() }).roundToLong()
    }
}
@Composable
fun rememberControlState(): ControlState {
    return remember { ControlState() }
}
fun List<Track>.longestDuration(): Long {
    var res = 0L
    forEach { t ->
        res = res.coerceAtLeast(t.pieces.sumOf { it.duration })
    }
    return res
}
@Composable
fun Control(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    onTracksChange: (List<Track>) -> Unit,

    requestAdding: ((List<SelectInfo>) -> Unit) -> Unit,
    controlState: ControlState = rememberControlState()
) {
    var selectedPiecesSet by remember { mutableStateOf(setOf<Piece>()) }
    BackHandler(enabled = selectedPiecesSet.isNotEmpty()) {
        selectedPiecesSet = setOf()
    }
    var zoom by remember { mutableFloatStateOf(1f) }

    Box(modifier = modifier.pointerInput(Unit) {
        transformGestures(
            onGesture = { _, _, gestureZoom, _ ->
                zoom *= gestureZoom
                if(zoom < 1) zoom = 1f
            }
        )
    }) {
        val horizontalScrollState = controlState.progressState
        val longestDuration = tracks.longestDuration()

        LazyColumn(
            modifier = Modifier
                .padding(top = 50.dp)
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)) {
            items(items = tracks) { track ->
                Track(
                    track = track,
                    onTrackChange = { it: Track ->
                        onTracksChange.invoke(ArrayList(tracks).apply {
                            val index = indexOf(track)
                            this[index] = it
                            if(index == lastIndex) add(Track(listOf()))
                        })
                    },

                    selectedSet = selectedPiecesSet,
                    onSelectedSetChange = { selectedPiecesSet = it },

                    requestAdding = requestAdding,

                    zoom = zoom,
                    onZoomChange = { expectedZoom ->
                        ValueAnimator.ofFloat(zoom, expectedZoom).apply {
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

        val currentGlobalProgressInMillis = controlState.calCurrentMillis(longestDuration) // in milliseconds
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            ProgressHintText(modifier = Modifier.align(CenterHorizontally), current = currentGlobalProgressInMillis)
            Spacer(modifier = Modifier.height(10.dp))
            ProgressHintLine(modifier = Modifier.align(CenterHorizontally))
        }


        BottomTools(modifier = Modifier
            .padding(30.dp)
            .align(Alignment.BottomCenter),
            selectedPiecesSet = selectedPiecesSet,
            onSelectedPiecesSetChange = {
                selectedPiecesSet = it
            },
            tracks = tracks,
            onTracksChange = onTracksChange,
            currentGlobalProgressInMillis = currentGlobalProgressInMillis,
            requestAdding = requestAdding
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BottomTools(
    modifier: Modifier,
    selectedPiecesSet: Set<Piece>,
    onSelectedPiecesSetChange: (Set<Piece>) -> Unit,

    tracks: List<Track>,
    onTracksChange: (List<Track>) -> Unit,

    currentGlobalProgressInMillis: Long,
    requestAdding: ((List<SelectInfo>) -> Unit) -> Unit

) {
    val selectionMode = selectedPiecesSet.isNotEmpty()

    Column(modifier = modifier) {
        AnimatedVisibility(
            modifier = Modifier.align(CenterHorizontally),
            enter = fadeIn(), exit = fadeOut(),
            visible = selectionMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier.alpha(0.9f)
            ) {
                val totalDuration = selectedPiecesSet.sumOf { it.duration }
                Text(
                    text = "${totalDuration.millisTimeFormat()}(${selectedPiecesSet.size})",
                    color = Color.White)
            }
        }

        AnimatedVisibility(
            visible = selectionMode
        ) {
            FlowRow {
                /**
                 * Index of track and pieces
                 */
                fun indexOfSelectedPiece(): List<Pair<Int, Int>> {
                    val result = ArrayList<Pair<Int, Int>>()

                    selectedPiecesSet.forEach { currentSelectedPiece ->
                        val trackIndex: Int
                        var pieceIndex: Int
                        for((index, track) in tracks.withIndex()) {
                            if(track.pieces.indexOf(currentSelectedPiece).also { pieceIndex = it} > -1) {
                                trackIndex = index
                                result.add(Pair(trackIndex, pieceIndex))
                                break // to continue next
                            }
                        }
                    }

                    return result
                }
                IconButton(onClick = {
                    onTracksChange(ArrayList<Track>().apply {
                        tracks.forEach { track ->
                            val newPieces = track.pieces.filter { piece ->
                                !selectedPiecesSet.contains(piece)
                            }

                            if(newPieces.isNotEmpty()) add(Track(newPieces))
                        }

                        if(isEmpty() || last().pieces.isNotEmpty()) add(Track(listOf()))
                    })

                    onSelectedPiecesSetChange(setOf())
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White)
                }

                IconButton(onClick = {
                    val nextTracks = ArrayList(tracks).apply {
                        indexOfSelectedPiece().forEach {
                            val trackIndex = it.first
                            val pieceIndex = it.second

                            val pieces = this[trackIndex].pieces

                            try {

                                val cutPoint = currentGlobalProgressInMillis -
                                        pieces.subList(0, pieceIndex).sumOf { p -> p.duration }

                                pieces[pieceIndex].cut(cutPoint).let {
                                    this[trackIndex] = Track(
                                        pieces = ArrayList(pieces).apply {
                                            removeAt(pieceIndex)
                                            addAll(pieceIndex, listOf(it.first, it.second))
                                        }
                                    )
                                }
                            } catch(e: Piece.NotInValidScope) {
                                e.printStackTrace()
                            }
                        }
                    }
                    onSelectedPiecesSetChange(setOf())
                    onTracksChange(nextTracks)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.nc_sample_outline_design_scissors),
                        tint = Color.White,
                        contentDescription = "cut")
                }

                AnimatedVisibility(visible = selectedPiecesSet.size == 1) {
                    IconButton(onClick = {
                        requestAdding.invoke { result: List<SelectInfo> ->
                            onTracksChange.invoke(ArrayList(tracks).apply {

                                val tpIndex = indexOfSelectedPiece().first()
                                val trackIndex: Int = tpIndex.first
                                val pieceIndex: Int = tpIndex.second

                                this[trackIndex] = Track(
                                    pieces = ArrayList(tracks[trackIndex].pieces).apply {
                                        addAll(pieceIndex + 1,
                                            result.map {
                                                Piece(
                                                    model = it.path,
                                                    end = (it.duration?:2000) - 1) })
                                    })
                            })
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            tint = Color.White,
                            contentDescription = "Add")
                    }
                }
            }
        }
    }
}