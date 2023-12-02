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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
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
fun checkDiffInTracks(tracks: List<Track>): Boolean {
    if(savedTracks == null) {
        savedTracks = tracks
        return false
    }

    if(savedTracks!!.size != tracks.size) {
        savedTracks = tracks
        return false
    }

    for((index, element) in savedTracks!!.withIndex()) {
        if(element != tracks[index]) {
            savedTracks = tracks
            return false
        }

        val oldPieces = element.pieces
        val newPiece = tracks[index].pieces

        if(oldPieces.size != newPiece.size) {
            savedTracks = tracks
            return false
        }


        for((index, element) in oldPieces.withIndex()) {
            if(!element.strongEquals(newPiece[index])) {
                savedTracks = tracks
                return false
            }
        }
    }

    return true
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun List<Track>.generateMediaSource(context: Context): MediaSource {
    val trackMediaSources = ArrayList<MediaSource>()

    forEach { track ->
        val builder = ConcatenatingMediaSource2.Builder().apply {
            useDefaultMediaSourceFactory(context)
        }

        var added = false
        track.pieces.forEach { piece ->
            try {
                builder.add(
                    MediaItem.fromUri(piece.model.toString()), piece.duration)
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
        *trackMediaSources.toTypedArray())

}

@Composable
fun VideoScreen(
    modifier: Modifier,
    tracks: List<Track>,
    controlState: ControlState
) {
    Box(modifier = modifier.background(color = Color.Black)) {
        val context = LocalContext.current
        val mediaSource = remember(checkDiffInTracks(tracks)) {
            tracks.generateMediaSource(context)
        }

        val nextPos = controlState.calCurrentMillis(tracks.longestDuration())
        PlayerSurface(
            modifier = Modifier.align(Alignment.Center),

            mediaSource = mediaSource,
            nextPos = nextPos)
    }

}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerSurface(
    modifier: Modifier,
    mediaSource: MediaSource,
    nextPos: Long
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = true
                player = ExoPlayer.Builder(context).apply {

                }.build().apply {
                    setMediaSource(mediaSource)
                    prepare()
                }
            }
        },
        update = { playerView: PlayerView ->
            (playerView.player as ExoPlayer).apply {
                setMediaSource(mediaSource)

                seekTo(nextPos)
            }
        },
        modifier = modifier
    )
}
