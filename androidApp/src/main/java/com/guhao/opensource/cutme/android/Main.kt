package com.guhao.opensource.cutme.android

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainViewModel: ViewModel() {
    val tracks = MutableLiveData(listOf(Track(listOf())))
    fun onTracksChange(new: List<Track>) {
        tracks.value = new
    }

    var requestAdding: ((List<SelectInfo>) -> Unit) -> Unit = { throw UnsupportedOperationException() }
}


@Composable
fun DragPad(
    modifier: Modifier,

    targetHeight: () -> Int,
    onTargetHeightChange: (Int) -> Unit)
{
    val density = LocalDensity.current.density
    val draggingIcon = Icons.Default.Menu
    val draggingIconHeight = 48 // dp
    var dragging by remember { mutableStateOf(false) }
    val screenHeight = LocalConfiguration.current.screenHeightDp

    Icon(
        imageVector = draggingIcon,
        contentDescription = "Drag man",
        tint = if(dragging) Color.White else Color.Black,
        modifier = modifier
            .padding(vertical = 10.dp)
            .graphicsLayer(scaleX = 3f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { _: PointerInputChange, dragAmount: Offset ->
                        val current = targetHeight.invoke() + (dragAmount.y / density).roundToInt()
                        if (current <= screenHeight - draggingIconHeight) {
                            onTargetHeightChange(current)
                        }
                    },
                    onDragStart = {
                        dragging = true
                    },
                    onDragCancel = {
                        dragging = false
                    },
                    onDragEnd = {
                        dragging = false
                    }
                )
            }
    )
}
@Composable
fun Main(vm: MainViewModel = MainViewModel()) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    var videoHeight by remember { mutableIntStateOf(screenHeight / 2) }
    Column {
        val tracks by vm.tracks.observeAsState(listOf(Track(listOf())))
        val controlState = rememberControlState()
        VideoScreen(
            modifier = Modifier.height(videoHeight.dp),
            tracks = tracks,
            controlState = controlState)

        DragPad(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            targetHeight = { videoHeight },
            onTargetHeightChange = { videoHeight = it })


        Control(
            modifier = Modifier.fillMaxHeight(),
            tracks = tracks,
            onTracksChange = vm::onTracksChange,
            requestAdding = vm.requestAdding,
            controlState = controlState)
    }
}

var savedTracks: List<Track>? = null
var savedMediaSource: MediaSource? = null
fun checkDiffInTracks(newTracks: List<Track>): Boolean {
    if(savedTracks == null) {
        return false
    }

    if(savedTracks!! != newTracks) {
        return false
    }

    for((index, savedTrack) in savedTracks!!.withIndex()) {
        val newTrack = newTracks[index]
        if(savedTrack != newTrack) {
            return false
        }

        val oldPieces = savedTrack.pieces
        val newPieces = newTrack.pieces

        for((i, oldPiece) in oldPieces.withIndex()) {
            val newPiece = newPieces[i]
            if(oldPiece != newPiece) {
                return false
            }
        }
    }

    return true
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun List<Track>.getMediaSource(context: Context): MediaSource {
    if(checkDiffInTracks(this)) {
        return savedMediaSource!!
    }
    val trackMediaSources = ArrayList<MediaSource>()

    forEach { track ->
        val builder = ConcatenatingMediaSource2.Builder().apply {
            useDefaultMediaSourceFactory(context)
        }

        var added = false
        track.pieces.forEach { piece ->
            try {
                builder.add(
                    MediaItem.fromUri(piece.model.toString()),
                    piece.duration)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            added = true
        }
        if(added) trackMediaSources.add(builder.build())
    }

    return MergingMediaSource(
        false,
        false,
        *trackMediaSources.toTypedArray()).also {
        savedTracks = this
        savedMediaSource = it
    }

}

@Composable
fun VideoScreen(
    modifier: Modifier,
    tracks: List<Track>,
    controlState: ControlState
) {
    Box(modifier = modifier.background(color = Color.Black)) {
        val mediaSource = tracks.getMediaSource(LocalContext.current)
        val coroutineScope = rememberCoroutineScope()

        Player(
            modifier = Modifier.align(Alignment.Center),
            mediaSource = mediaSource,
            position = controlState.calCurrentMillis(tracks.longestDuration()),
            onPositionChange = { current: Long, duration:Long ->
                coroutineScope.launch {
                    controlState.updateProgress(current, duration)
                }
            })
    }

}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun Player(
    modifier: Modifier,
    mediaSource: MediaSource,
    position: Long,
    onPositionChange: (Long, Long) -> Unit,
) {
    var currentMediaSource by remember { mutableStateOf<MediaSource?>(null) }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {

                useController = true
                val builder = ExoPlayer.Builder(context)

                player = builder.build().apply {
                    addListener(object: Player.Listener {
                        fun getCurrentPos() {
                            if(isPlaying) {
                                onPositionChange(currentPosition, duration)
                                postDelayed(this::getCurrentPos, 10)
                            }
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            getCurrentPos()
                        }
                    })

                    prepare()
                }
            }
        },
        update = { playerView: PlayerView ->
            (playerView.player as ExoPlayer).apply {
                // Only when the media source is different, we update,
                if(currentMediaSource != mediaSource) {
                    setMediaSource(mediaSource)
                    currentMediaSource = mediaSource
                }

                // Only when it is not playing, we follow the control panel position.
                if(!(playerView.player as ExoPlayer).isPlaying) seekTo(position)
            }
        },
        modifier = modifier
    )
}
