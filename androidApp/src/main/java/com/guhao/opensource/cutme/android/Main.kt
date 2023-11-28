package com.guhao.opensource.cutme.android

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
