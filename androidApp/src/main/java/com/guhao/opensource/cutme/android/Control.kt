package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.guhao.opensource.cutme.millisTimeFormat
import com.guhao.opensource.cutme.millisTimeStandardFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun List<Track>.move(
    initialPos: Pair<Int, Int>,
    targetPosition: Pair<Int, Int>,
    precedingBlankPieceDuration: Long? = null): List<Track> {

    return ArrayList(this).apply {
        val piece = this[initialPos.first].pieces[initialPos.second]

        fun deleteOld() {

            val newPieces = ArrayList(this[initialPos.first].pieces)
            newPieces.removeAt(initialPos.second)

            this[initialPos.first] = Track(pieces = newPieces)

        }
        fun addNew() {
            val targetTrackIndex = targetPosition.first
            val targetPieceIndex = targetPosition.second
            if(targetTrackIndex < 0 || targetTrackIndex >= size) {
                val pieces = if(precedingBlankPieceDuration != null && precedingBlankPieceDuration > 0) {
                    listOf(Piece(model = null, end = precedingBlankPieceDuration), piece)
                } else listOf(piece)
                add(Track(pieces = pieces))
            } else {
                val newPieces = ArrayList(this[targetTrackIndex].pieces)

                if (targetPieceIndex < 0 || targetPieceIndex >= size) {
                    newPieces.add(piece)
                } else {
                    newPieces.add(targetPieceIndex, piece)
                }

                set(targetTrackIndex, Track(pieces = newPieces))
            }
        }

        if(initialPos.first != targetPosition.first) {
            deleteOld(); addNew()
        } else {
            if(initialPos.second < targetPosition.second) {
                addNew(); deleteOld()
            } else {
                deleteOld(); addNew()
            }
        }
    }
}

suspend fun PointerInputScope.transformGestures(
    panZoomLock: Boolean = false,
    onTouch: () -> Unit = {},
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
        onTouch.invoke()
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
    var focusOn: Boolean = false
    val progressState = ScrollState(initial = 0)
    fun calCurrentMillis(duration: Long): Long {
        return if(progressState.maxValue == 0)
            0
        else
            (duration * progressState.let { it.value / it.maxValue.toFloat() }).roundToLong()
    }
    suspend fun updateProgress(positionInMillis: Long, duration: Long) {
        if(duration.toInt() == 0) return

        val nextPosPixel = (progressState.maxValue * positionInMillis / duration).toInt()
        progressState.scrollTo(nextPosPixel)
    }
}
@Composable
fun rememberControlState(): ControlState {
    return remember { ControlState() }
}
fun List<Track>.longestDuration(): Long = maxOf { track -> track.pieces.sumOf { it.duration }}

@Composable
fun ZoomBox(
    modifier: Modifier,

    zoom: () -> Float,
    onZoomChange: (Float) -> Unit,

    onTouch: () -> Unit,

    content: @Composable BoxScope.() -> Unit
    ) {
    Box(modifier = modifier.pointerInput(Unit) {
        transformGestures(
            onTouch = {
                onTouch.invoke()
            },
            onGesture = { _, _, gestureZoom, _ ->
                onZoomChange((zoom.invoke() * gestureZoom).coerceAtLeast(0.5f))
            }
        )
    }, content = content)
}

@Composable
fun Control(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    onTracksChange: (List<Track>) -> Unit,

    requestAdding: ((List<SelectInfo>) -> Unit) -> Unit,
    controlState: ControlState = rememberControlState(),
) {

    var selectedPiecesSet by remember { mutableStateOf(setOf<Piece>()) }
    BackHandler(enabled = selectedPiecesSet.isNotEmpty()) {
        selectedPiecesSet = setOf()
    }

    var draggingItem by remember { mutableStateOf<DraggingItem?>(null) }
    var currentDroppingTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var currentPrecedingPaddingDp by remember { mutableIntStateOf(0) }

    var zoom by remember { mutableFloatStateOf(1f) }


    val currentTracks by remember { mutableStateOf<List<Track>>(listOf()) }.also {
        it.value = tracks
    }
    ZoomBox(
        modifier = modifier,
        onTouch = {
            controlState.focusOn = true
        },
        zoom = { zoom },
        onZoomChange = { zoom = it }
    ) {
        val horizontalScrollState = controlState.progressState
        var xPosOnCreated by remember { mutableIntStateOf(horizontalScrollState.value) }
        val expectedCompensationCausedByScroll = horizontalScrollState.value - xPosOnCreated

        val totalDuration = tracks.longestDuration()

        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val maxTrackLengthDp = screenWidthDp

        val onTracksChangeInControl = { newTracks: List<Track> ->
            if(totalDuration > 0) zoom *= (newTracks.longestDuration() / totalDuration.toFloat())

            onTracksChange.invoke(newTracks)
        }

        val startAndEndPaddingDp = maxTrackLengthDp / 2

        val inScopeTrackSet = remember { HashSet<Int>() }

        LazyColumn(
            modifier = Modifier
                .padding(top = 50.dp)
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)) {
            items(items = tracks) { track ->
                val trackIndex = tracks.indexOf(track)

                Track(
                    track = track,
                    onTrackChange = { it: Track ->
                        onTracksChangeInControl.invoke(ArrayList(tracks).apply {
                            this[indexOf(track)] = it
                        })
                    },

                    selectedSet = selectedPiecesSet,
                    onSelectedSetChange = { selectedPiecesSet = it },

                    requestAdding = requestAdding,

                    zoom = zoom,
//                    onZoomChange = { expectedZoom: Float ->
//                        ValueAnimator.ofFloat(zoom, expectedZoom).apply {
//                            duration = 1000
//                            addUpdateListener {
//                                zoom = it.animatedValue as Float
//                            }
//                        }.start()
//                    },
                    totalDuration = totalDuration,
                    draggingItem = draggingItem?.copy(compensationX = expectedCompensationCausedByScroll),
                    onDraggingItemChange = { reason, item ->

                        if(reason != DraggingItemChangeReason.UPDATE) {
                            // Currently the 'item' will be null, don't use it.
                            currentDroppingTarget?.let { targetPos ->
                                val initialPos = draggingItem!!.let { Pair(it.trackIndex, it.pieceIndex) }

                                onTracksChangeInControl.invoke(
                                    currentTracks.move(
                                        initialPos,
                                        targetPos,
                                        precedingBlankPieceDuration = if(currentPrecedingPaddingDp > 0) {
                                            (totalDuration * currentPrecedingPaddingDp / maxTrackLengthDp.toFloat()).roundToLong()
                                        } else null
                                    ))
                            }
                        }

                        if(reason == DraggingItemChangeReason.UPDATE && draggingItem == null && item != null) {
                            // Means created for the first time.

                            xPosOnCreated = horizontalScrollState.value
                        }

                        draggingItem = item?.copy(trackIndex = trackIndex)
                    },
                    onDraggingInScope = { pieceIndex ->
                        currentDroppingTarget = Pair(trackIndex, pieceIndex)
                        inScopeTrackSet.add(trackIndex)
                    },
                    onInScopePiecesClear = {
                        inScopeTrackSet.remove(trackIndex)
                        if(inScopeTrackSet.isEmpty()) {
                            currentDroppingTarget = null
                        }
                    },
                    shouldAnimateDraggingItemBack = { currentDroppingTarget == null },

                    maxTrackLengthDp = maxTrackLengthDp,
                )
            }

            item {
                val density = LocalDensity.current.density

                var inScope by remember { mutableStateOf(false) }

                fun calTarget(draggingItem: DraggingItem) = ((horizontalScrollState.value + draggingItem.position.x) / density - draggingItem.width.value / 2 - startAndEndPaddingDp).roundToInt().coerceAtLeast(0)

                val blankPieceWidthDp = draggingItem?.let { calTarget(it) } ?: 0
                currentPrecedingPaddingDp = blankPieceWidthDp
                Box {
                    AnimatedVisibility(
                        visible = inScope && blankPieceWidthDp > 0,
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        PieceCard(
                            modifier = Modifier
                                .alpha(0.5f)
                                .padding(start = startAndEndPaddingDp.dp)
                                .width(blankPieceWidthDp.dp)
                                .height(pieceHeight),
                        ) {}
                    }

                    DraggingItemDetector2(
                        modifier = Modifier
                            .padding(start = (horizontalScrollState.value / density).dp)
                            .width(screenWidthDp.dp)
                            .height(pieceHeight),
                        draggingItem = draggingItem,
                        onDraggingInScopeChange = {
                            inScope = it

                            val trackIndex = tracks.size

                            if(it) {
                                currentDroppingTarget = Pair(trackIndex, 0)
                                inScopeTrackSet.add(trackIndex)
                            } else {
                                inScopeTrackSet.remove(trackIndex)
                                if(inScopeTrackSet.isEmpty()) {
                                    currentDroppingTarget = null
                                }
                            }
                        }
                    ) {}
                }


            }
        }

        var expectingScrollingTo by remember { mutableIntStateOf(0) }
        LaunchedEffect(key1 = expectingScrollingTo) {
            horizontalScrollState.scrollTo(expectingScrollingTo)
        }

        EdgeDraggingDetector(
            modifier = Modifier.fillMaxSize(),
            draggingItem = draggingItem,

            currentScrollValue = horizontalScrollState.value,
            onExpectingScrollingToChange = {
                expectingScrollingTo = it
            },
            maxScrollValue = horizontalScrollState.maxValue
        )

        val currentGlobalProgressInMillis = controlState.calCurrentMillis(totalDuration) // in milliseconds

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            enter = scaleIn(), exit = scaleOut(),
            visible = draggingItem == null) {
            Column {
                ProgressHintText(modifier = Modifier.align(CenterHorizontally), current = currentGlobalProgressInMillis)
                Spacer(modifier = Modifier.height(10.dp))
                ProgressHintLine(modifier = Modifier.align(CenterHorizontally))
            }
        }

        BottomTools(modifier = Modifier
            .padding(30.dp)
            .align(Alignment.BottomCenter),
            selectedPiecesSet = selectedPiecesSet,
            onSelectedPiecesSetChange = {
                selectedPiecesSet = it
            },
            tracks = tracks,
            onTracksChange = onTracksChangeInControl,
            currentGlobalProgressInMillis = currentGlobalProgressInMillis,
            requestAdding = requestAdding
        )
    }
}

@Composable
fun EdgeDraggingDetector(
    modifier: Modifier,
    draggingItem: DraggingItem?,
    currentScrollValue: Int,
    onExpectingScrollingToChange: (Int) -> Unit,

    maxScrollValue: Int,
) {
    Box(modifier = modifier) {
        var inScope by remember { mutableIntStateOf(0) }


        val totalBrowseTime = 5 * 1000 // This is the time that you will need to browse the complete longest track.
        var currentAnimator by remember { mutableStateOf<ValueAnimator?>(null) }
        LaunchedEffect(key1 = inScope) {

            val backScrollTime = (totalBrowseTime * currentScrollValue / maxScrollValue.toFloat()).roundToLong()
            val forwardScrollTime = totalBrowseTime - backScrollTime
            when(inScope) {
                -1 -> { // Start detected
                    ValueAnimator.ofInt(currentScrollValue, 0).apply {
                        duration = backScrollTime
                        addUpdateListener {
                            onExpectingScrollingToChange.invoke(it.animatedValue as Int)
                        }
                    }.also { currentAnimator = it }.start()
                }

                0 -> { // Cancelled
                    currentAnimator?.cancel()
                }

                1 -> { // End detected
                    ValueAnimator.ofInt(currentScrollValue, maxScrollValue).apply {
                        duration = forwardScrollTime
                        addUpdateListener { onExpectingScrollingToChange.invoke(it.animatedValue as Int) }
                    }.also { currentAnimator = it }.start()
                }

                else -> throw UnsupportedOperationException()
            }
        }

        val edge = @Composable { modifier: Modifier, onDraggingInScopeChange: (Boolean) -> Unit ->
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            DraggingItemDetector2(
                modifier = modifier
                    .fillMaxHeight()
                    .width(screenWidthDp.dp / 10),
                draggingItem = draggingItem, onDraggingInScopeChange = onDraggingInScopeChange, block = {})
        }

        edge.invoke(Modifier.align(Alignment.CenterStart)) {
            inScope = if(it) -1 else 0
        }
        edge.invoke(Modifier.align(Alignment.CenterEnd)) {
            inScope = if(it) 1 else 0
        }

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

                        if(isEmpty()) add(Track(listOf()))
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