package com.guhao.opensource.cutme.android

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
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
            modifier = Modifier,

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
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {

                useController = true
                val builder = ExoPlayer.Builder(context)

                player = builder.build().apply {
                    addListener(object: Player.Listener {
                        fun getCurrentPos() {
                            if(isPlaying) {
                                onPlayerPositionContinuouslyChange(currentPosition, duration)
                                postDelayed(this::getCurrentPos, 10)
                            }
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            onIsPlayingChanged(isPlaying)

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
                playerUpdateExecutor.execOnNewMediaSource {
                    setMediaSource(it)
                }

                // Only when it is not playing, we follow the control panel position.
                playerUpdateExecutor.execOnTouch {
                    if((playerView.player as ExoPlayer).isPlaying) pause()
                    seekTo(controlPosition)
                }
            }
        },
        modifier = modifier
    )
}


