package com.guhao.opensource.cutme.android

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.media3.ui.PlayerView

class MainViewModel: ViewModel() {
    val tracks = MutableLiveData(listOf<Track>())

    fun onTracksChange(new: List<Track>) {
        tracks.value = new
    }

    var requestAdding: ((List<SelectInfo>) -> Unit) -> Unit = { throw UnsupportedOperationException() }
}
@Composable
fun Main(vm: MainViewModel = MainViewModel()) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    var videoHeight by remember { mutableIntStateOf(screenHeight / 2) }

    Column {
        VideoScreen(modifier = Modifier.height(videoHeight.dp), mediaItem = null)

        val density = LocalDensity.current.density
        val draggingIcon = Icons.Default.Menu
        val draggingIconHeight = draggingIcon.viewportHeight / density
        var dragging by remember { mutableStateOf(false) }
        Icon(
            imageVector = draggingIcon,
            contentDescription = "Dragger",
            tint = if(dragging) Color.White else Color.Black,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp)
                .graphicsLayer(scaleX = 3f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change: PointerInputChange, dragAmount: Offset ->
                            var current = videoHeight
                            current += (dragAmount.y / density).toInt()
                            if(current <= screenHeight - draggingIconHeight)  videoHeight = current
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
