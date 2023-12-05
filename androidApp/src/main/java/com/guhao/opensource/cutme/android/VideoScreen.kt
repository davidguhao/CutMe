package com.guhao.opensource.cutme.android

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    modifier: Modifier,
    tracks: List<Track>,
    controlState: ControlState
) {
    Box(modifier = modifier.background(color = Color.Black)) {
        val coroutineScope = rememberCoroutineScope()
        val playerUpdateExecutor = remember { PlayerUpdateExecutor() }

        playerUpdateExecutor.updateMediaSource(LocalContext.current, tracks)
        playerUpdateExecutor.updateTouchState(controlState.focusOn)

        Player(
            modifier = Modifier.fillMaxSize(),

            playerUpdateExecutor = playerUpdateExecutor,
            controlPosition = controlState.calCurrentMillis(tracks.longestDuration()),
            onPlayerPositionContinuouslyChange = { current: Long, duration:Long ->
                coroutineScope.launch {
                    controlState.updateProgress(current, duration)
                }
            },
            onIsPlayingChanged = {
                if(it) controlState.focusOn = false
            }
        )
    }

}

class PlayerUpdateExecutor {
    private var mediaSource: MediaSource? = null
    private var shouldUpdateMediaSource: Boolean = false

    fun execOnNewMediaSource(block: (MediaSource) -> Unit) {
        if(shouldUpdateMediaSource) {
            block.invoke(mediaSource!!)
            shouldUpdateMediaSource = false
        }
    }

    private var savedMediaSource: MediaSource? = null
    private var savedTracks: List<Track>? = null

    fun updateMediaSource(context: Context, tracks: List<Track>) {

        mediaSource = if(trackListEquals(savedTracks, tracks)) {
            savedMediaSource!!
        } else {
            tracks.generateMediaSource(context).also {
                savedTracks = tracks
                savedMediaSource = it

                shouldUpdateMediaSource = true
            }
        }
    }

    private var onTouch = false
    fun updateTouchState(onTouch: Boolean) {
        this.onTouch = onTouch
    }
    fun execOnTouch(block: () -> Unit) {
        if(onTouch) {
            block.invoke()
            onTouch = false
        }
    }

    companion object {
        private fun trackListEquals(savedTracks: List<Track>?, newTracks: List<Track>): Boolean {
            if(savedTracks == null) {
                return false
            }

            if(savedTracks != newTracks) {
                return false
            }

            for((index, savedTrack) in savedTracks.withIndex()) {
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
        private fun List<Track>.generateMediaSource(context: Context): MediaSource {
            val trackMediaSources = ArrayList<MediaSource>()

            forEach { track ->
                val builder = ConcatenatingMediaSource2.Builder().apply {
                    useDefaultMediaSourceFactory(context)
                }

                var added = false
                track.pieces.forEach { piece ->
                    try {
                        val raw = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                            .createMediaSource(MediaItem.fromUri(piece.model.toString()))
                        val clipped = ClippingMediaSource(
                            raw,
                            piece.start * 1000,
                            piece.end * 1000)
                        builder.add(clipped, piece.duration)
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
    }
}
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun Player(
    modifier: Modifier,
    playerUpdateExecutor: PlayerUpdateExecutor,
    controlPosition: Long,
    onPlayerPositionContinuouslyChange: (Long, Long) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit
) {
    var controllerIsPlaying by remember { mutableStateOf(false) }
    var localPlayer by remember { mutableStateOf<ExoPlayer?>(null)}
    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {

                    useController = false
                    val builder = ExoPlayer.Builder(context)

                    player = builder.build().apply {
                        addListener(object: Player.Listener {
                            fun getCurrentPos() {
                                if(isPlaying) {
                                    onPlayerPositionContinuouslyChange(currentPosition, duration)
                                    postDelayed(this::getCurrentPos, 5)
                                }
                            }
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                controllerIsPlaying = isPlaying

                                onIsPlayingChanged(isPlaying)

                                getCurrentPos()
                            }
                        })

                        prepare()
                    }.also { localPlayer = it }
                }
            },
            update = { playerView: PlayerView ->
                (playerView.player as ExoPlayer).apply {
                    // Only when the media source is different, we update,
                    playerUpdateExecutor.execOnNewMediaSource {
                        setMediaSource(it)
                    }

                    // Only when it is not playing, we follow the control panel position.
                    playerUpdateExecutor.execOnTouch {
                        val player = (playerView.player as ExoPlayer)
                        if(player.isPlaying) pause()
                        seekTo(controlPosition)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        PlayerController(
            modifier = Modifier.fillMaxSize(),
            isPlaying = controllerIsPlaying,
            onIsPlayingChange = { isPlaying ->
                localPlayer?.let {
                    if(isPlaying) it.play() else it.pause()
                }
            })
    }

}

@Composable
fun PlayerController(
    modifier: Modifier,

    isPlaying: Boolean,
    onIsPlayingChange: (Boolean) -> Unit,
) {
    var showController by remember { mutableStateOf(true) }

    Box(modifier = modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                showController = !showController
            }
        )
    }) {
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(), exit = fadeOut(),
            visible = showController) {
            IconButton(
                modifier = Modifier.graphicsLayer(
                    scaleX = 2f, scaleY = 2f
                ),
                onClick = { onIsPlayingChange.invoke(!isPlaying) }) {
                AnimatedContent(targetState = isPlaying, label = "isPlaying") { isPlaying ->
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "isPlaying",
                        tint = Color.White,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            drawCircle(
                                color = Color.Black,
                                radius = size.width / 2,
                                alpha = 0.8f,
                                blendMode = if(isPlaying) BlendMode.SrcOut else BlendMode.DstOver
                            )
                        })
                }

            }
        }
    }

}