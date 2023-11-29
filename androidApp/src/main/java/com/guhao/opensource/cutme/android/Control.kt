package com.guhao.opensource.cutme.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.guhao.opensource.cutme.millisTimeFormat
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



@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun Piece(
    piece: Piece,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,

    zoom: Float = 1f,
    offset: Offset = Offset.Zero
) {
    val actualWidth = width * zoom
    val actionable = actualWidth > 30.dp

    Box(modifier = Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }) {
        Card(
            shape = RoundedCornerShape(0.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            AnimatedContent(targetState = selected, label = "") { selected ->
                GlideImage(
                    modifier = Modifier
                        .alpha(if (selected) 0.5f else 1f)
                        .height(70.dp)
                        .width(actualWidth)
                        .combinedClickable(
                            onClick = {
                                if (actionable) onClick.invoke()
                            },
                            onLongClick = {
                                if (actionable) onLongClick.invoke()
                            }
                        ),
                    contentScale = ContentScale.Crop,
                    model = piece.model,
                    contentDescription = "")
            }

        }


        Column(modifier = Modifier.align(Alignment.Center)) {

            AnimatedVisibility(visible = actionable) {
                Text(
                    text = piece.duration.millisTimeFormat(),
                    color = Color.White)
            }
            AnimatedVisibility(visible = selected) {
                Icon(
                    tint = Color.White,
                    imageVector = Icons.Default.Done, contentDescription = "Selected")
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
            onGesture = { _, pan, gestureZoom, _ ->
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
                            this[indexOf(track)] = it
                        })
                    },

                    selectedSet = selectedSet,
                    onSelectedSetChange = { selectedSet = it },

                    requestAdding = requestAdding,

                    zoom = zoom,
                    longestDuration = longestDuration,
                )
            }

            item {
                TextButton(
                    onClick = {
                        onTracksChange(tracks + listOf(Track(listOf())))
                    }) {
                    Text(text = stringResource(id = R.string.addTrack))
                }
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

        FlowRow(modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(30.dp)) {
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
