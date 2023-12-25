package com.guhao.opensource.cutme.android

import android.animation.TimeInterpolator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.animation.addListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.math.abs
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

    val overScrollExtent = 20
    val maxHeight = screenHeight - draggingIconHeight


    var draggingStartingTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var speed by remember { mutableStateOf(0f)}
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
                        speed = diffInDp / timeSpent.toFloat()
                        println("Current dragging speed: $speed")
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

                        val threshold = 1
                        if(abs(speed) > threshold) {
                            val animateTargetValue = if(speed > 0) maxHeight else 0
                            ValueAnimator.ofInt(targetHeight.invoke(), animateTargetValue).apply {
                                duration = 500
                                interpolator = TimeInterpolator { input -> input }
                                setEvaluator(
                                    TypeEvaluator<Int> { fraction, startValue, endValue ->
                                        val len = endValue - startValue


                                        startValue + (len * fraction).roundToInt()
                                    }
                                )
                                addUpdateListener {
                                    onTargetHeightChange.invoke(it.animatedValue as Int)
                                }
                            }.start()
                        } else if(abs(speed) > threshold / 2) {
                            ValueAnimator.ofInt(targetHeight.invoke(), targetHeight.invoke() + overScrollExtent * (if(speed > 0) 1 else -1)).apply {
                                duration = 100
                                addUpdateListener {
                                    onTargetHeightChange.invoke(it.animatedValue as Int)
                                }
                                addListener(
                                    onEnd = {
                                        ValueAnimator.ofInt(targetHeight.invoke(), targetHeight.invoke() + overScrollExtent / 2 * (if(speed > 0) -1 else 1)).apply {
                                            duration = 200
                                            addUpdateListener { onTargetHeightChange.invoke(it.animatedValue as Int) }
                                        }.start()
                                    }
                                )
                            }.start()
                        }
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

        Control(
            modifier = Modifier.fillMaxHeight(),
            tracks = tracks,
            onTracksChange = vm::onTracksChange,
            requestAdding = vm.requestAdding,
            controlState = controlState,
            )
    }
}

