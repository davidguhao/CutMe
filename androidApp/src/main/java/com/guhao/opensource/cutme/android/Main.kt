package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import java.lang.Thread.sleep
import kotlin.math.roundToInt

class MainViewModel: ViewModel() {
    val tracks = MutableLiveData(listOf(Track(listOf())))
    private val tracksHistory: TracksHistoryRecorder = TracksHistoryRecorder()

    fun onTracksChange(new: List<Track>) {
        tracks.value = new

        tracksHistory.record(new)
    }

    fun hasPreviousTracks() = tracksHistory.hasPreviousTracks()
    fun hasNextTracks() = tracksHistory.hasNextTracks()
    fun goToPreviousTracks() {
        if(hasPreviousTracks()) tracks.value = tracksHistory.previous()
    }
    fun goToNextTracks() {
        if(hasNextTracks()) tracks.value = tracksHistory.next()
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

    val maxHeight = screenHeight - draggingIconHeight

    var draggingStartingTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var speed by remember { mutableFloatStateOf(0f) }
    Icon(
        imageVector = draggingIcon,
        contentDescription = "Drag man",
        tint = if(dragging) Color.White else Color.Black,
        modifier = modifier
            .padding(vertical = 10.dp)
            .graphicsLayer(scaleX = 3f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        draggingStartingTime = System.currentTimeMillis()
                    },

                    onDrag = { _: PointerInputChange, dragAmount: Offset ->
                        val diffInDp = (dragAmount.y / density).roundToInt()

                        val timeSpent = System.currentTimeMillis() - draggingStartingTime
                        speed = if (timeSpent == 0L) 0f else (diffInDp / timeSpent.toFloat())
                            .coerceAtMost(5f)
                            .coerceAtLeast(-5f) // Dp per millisecond
                        // I don't believe the value bigger than 5 is a normal one.
//                        println("Current dragging speed: $speed")
                        draggingStartingTime = System.currentTimeMillis()

                        val current = targetHeight.invoke() + diffInDp
                        if (current <= maxHeight) {
                            onTargetHeightChange(current)
                        }
                    },

                    onDragCancel = {
                        dragging = false
                    },
                    onDragEnd = {
                        dragging = false

                        // Here I am expecting the speed will be changing from its original value
                        // to 0 smoothly. But now I am really not sure where it will go.
                        var startTime = System.currentTimeMillis()

                        ValueAnimator
                            .ofFloat(speed, 0f)
                            .apply {
                                duration = 800
                                addUpdateListener {
                                    val currentSpeed = it.animatedValue as Float
                                    val timeDiff = System.currentTimeMillis() - startTime

                                    val nextTargetHeight =
                                        (targetHeight.invoke() + currentSpeed * timeDiff)
                                            .roundToInt()
                                            .coerceAtMost(maxHeight)
                                            .coerceAtLeast(0)
                                    onTargetHeightChange.invoke(nextTargetHeight)
                                    startTime = System.currentTimeMillis() // update for next round

                                    if (nextTargetHeight == 0 || nextTargetHeight == maxHeight) {
                                        cancel()

                                        // You hit back, the speed is the same but the direction changes
                                        ValueAnimator
                                            .ofFloat(-currentSpeed, 0f, currentSpeed)
                                            .apply {
                                                duration = 250
                                                interpolator = LinearInterpolator()
                                                addUpdateListener {
                                                    val currentSpeed = it.animatedValue as Float
                                                    val timeDiff =
                                                        System.currentTimeMillis() - startTime

                                                    val nextTargetHeight =
                                                        (targetHeight.invoke() + currentSpeed * timeDiff)
                                                            .roundToInt()
                                                            .coerceAtMost(maxHeight)
                                                            .coerceAtLeast(0)

                                                    onTargetHeightChange.invoke(nextTargetHeight)

                                                    startTime =
                                                        System.currentTimeMillis() // update for next round
                                                }
                                            }
                                            .start()

                                    }
                                }
                            }
                            .start()
                    }
                )
            }
    )
}
@Composable
fun Main(vm: MainViewModel = MainViewModel()) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    var videoHeight by remember { mutableIntStateOf(screenHeight / 2) }


    val controlState = rememberControlState()

    Column {
        val tracks by vm.tracks.observeAsState(listOf(Track(listOf())))

        VideoScreen(
            modifier = Modifier.height(videoHeight.dp),
            tracks = tracks,
            controlState = controlState)
        Box(modifier = Modifier.fillMaxWidth()) {
            DragPad(
                modifier = Modifier.align(Alignment.Center),
                targetHeight = { videoHeight },
                onTargetHeightChange = { videoHeight = it })
            TextButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {

                }) {
                Text(text = stringResource(id = R.string.build))

            }
        }

        Box {
            var lastTimeTouchedControl by remember { mutableLongStateOf(0) }

            Control(
                modifier = Modifier.fillMaxHeight(),
                tracks = tracks,
                onTracksChange = vm::onTracksChange,
                requestAdding = vm.requestAdding,
                controlState = controlState,
                onTouch = {
                    lastTimeTouchedControl = System.currentTimeMillis()
                })

            TracksHistory(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 20.dp),

                lastTimeTouchedControl = lastTimeTouchedControl,
                hasPrevious = vm::hasPreviousTracks,
                hasNext = vm::hasNextTracks,
                goPrevious = vm::goToPreviousTracks,
                goNext = vm::goToNextTracks,
            )
        }
    }
}

@Composable
fun TracksHistory(
    modifier: Modifier,

    lastTimeTouchedControl: Long,

    hasPrevious: () -> Boolean,
    hasNext: () -> Boolean,
    goPrevious: () -> Unit,
    goNext: () -> Unit
) {
    var historyVisible by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = lastTimeTouchedControl) {
        historyVisible = false
        delay(1000)
        historyVisible = true
    }
    AnimatedVisibility(modifier = modifier, visible = historyVisible) {
        Row {
            AnimatedVisibility(visible = hasPrevious.invoke()) {
                IconButton(onClick = goPrevious) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        tint = Color.White,
                        contentDescription = "Go previous")
                }
            }

            AnimatedVisibility(visible = hasNext.invoke()) {
                IconButton(onClick = goNext) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        tint = Color.White,

                        contentDescription = "Go next",
                        )
                }
            }
        }
    }
}
