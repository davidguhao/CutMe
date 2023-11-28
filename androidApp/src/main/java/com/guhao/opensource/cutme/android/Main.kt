package com.guhao.opensource.cutme.android

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.media3.common.MediaItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.guhao.opensource.cutme.millisTimeFormat

class MainViewModel: ViewModel() {
    val tracks = MutableLiveData(listOf<Track>())

    fun onTracksChange(new: List<Track>) {
        tracks.value = new
    }

    var requestAdding: ((List<SelectInfo>) -> Unit) -> Unit = { throw UnsupportedOperationException() }
}
@Composable
fun Main(vm: MainViewModel = MainViewModel()) {
    val videoHeight = LocalConfiguration.current.let { config ->
        if(config.orientation == Configuration.ORIENTATION_PORTRAIT) config.screenHeightDp / 2 else config.screenHeightDp / 3
    }.dp

    Column {
        VideoScreen(modifier = Modifier.height(videoHeight), mediaItem = null)

        val tracks by vm.tracks.observeAsState(listOf())

        Control(
            modifier = Modifier.fillMaxHeight(),
            tracks = tracks,
            onTracksChange = vm::onTracksChange,
            requestAdding = vm.requestAdding)
    }
}

@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem?
) {
    val context = LocalContext.current
    PlayerSurface(modifier = modifier) { playerView ->
        playerView.player = ExoPlayer.Builder(context).build().apply {
            mediaItem?.let { it -> setMediaItem(it) }
//        prepare()
//        play()
        }
    }
}

@Composable
fun PlayerSurface(
    modifier: Modifier,
    onPlayerViewAvailable: (PlayerView) -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = true
                onPlayerViewAvailable(this)
            }
        },
        modifier = modifier
    )
}

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
) {

    val piece2Width = HashMap<Piece, Int>().apply {
        var sum = 0L
        track.pieces.forEach {
            sum += it.duration
        }

        track.pieces.forEach {
            this[it] = (LocalConfiguration.current.screenWidthDp * (it.duration / sum.toFloat())).toInt()
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        items(track.pieces) { piece ->

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
                    } else
                        HashSet(selectedSet).apply { add(piece) })
                },
                onLongClick = {
                    // Drag but unselect all first
                    onSelectedSetChange(setOf())
                }
            )

        }
        item {
            IconButton(
                modifier = Modifier
                    .padding(start = 10.dp),
                onClick = {
                    requestAdding.invoke { result: List<SelectInfo> ->
                        onTrackChange(Track(track.pieces + result.map { Piece(model = it.path, duration = it.duration?:2000) } ))
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
            }

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

    zoom: Float = 1f
) {
    Box {
        Card(
            shape = RoundedCornerShape(0.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            AnimatedContent(targetState = selected, label = "") { selected ->
                GlideImage(
                    modifier = Modifier
                        .alpha(if (selected) 0.5f else 1f)
                        .height(70.dp)
                        .width(width * zoom)
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        ),
                    contentScale = ContentScale.Crop,
                    model = piece.model,
                    contentDescription = "")
            }

        }


        Column(modifier = Modifier.align(Alignment.Center)) {
            Text(text = piece.duration.millisTimeFormat())
            AnimatedVisibility(visible = selected) {
                Icon(imageVector = Icons.Default.Done, contentDescription = "Selected")
            }
        }
    }
}

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
        detectTransformGestures(
            onGesture = { _, _, gestureZoom, _ ->
                zoom *= gestureZoom
                if(zoom < 1) zoom = 1f
            }
        )
    }) {
        LazyColumn {
            items(items = tracks) { track ->
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
                )
            }

            item {
                TextButton(onClick = {
                    onTracksChange(tracks + listOf(Track(listOf(Piece(model = null, duration = 2000)))))
                }) {
                    Text(text = stringResource(id = R.string.addTrack))
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(30.dp),
            enter = scaleIn(),
            exit = scaleOut(),
            visible = selectionMode) {
            FloatingActionButton(onClick = {
                onTracksChange(ArrayList<Track>().apply {
                    tracks.forEach { track ->
                        val newPieces = track.pieces.filter { piece ->
                            !selectedSet.contains(piece)
                        }

                        if(newPieces.isNotEmpty()) add(Track(newPieces))
                    }
                })
                selectedSet = setOf()
            },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
