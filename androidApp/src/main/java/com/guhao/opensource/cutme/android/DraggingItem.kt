package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class DraggingItem(
    val position: Offset,
    val width: Dp,

    val pieceIndex: Int = -1,
    val trackIndex: Int = -1,

    val compensationX: Int = 0
)

fun isInScope(
    randomPoint: Offset,
    center: Offset,
    width: Float,
    height: Float,

    offset: Float): Boolean {

    val coverCenter = Offset(
        x = center.x - offset / 2,
        y = center.y
    )

    val xInScope = abs(randomPoint.x - coverCenter.x) < (offset + width) / 2
    val yInScope = abs(randomPoint.y - coverCenter.y) < height / 2

    return xInScope && yInScope
}
@Composable
fun DraggingItemHighFrequencyDetector(
    modifier: Modifier = Modifier,

    draggingItem: DraggingItem?,
    onDraggingInScopeChange: (Boolean) -> Unit,

    enabled: Boolean = true,

    block: @Composable () -> Unit
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }
    val draggingInScope = draggingItem?.let { enabled && isInScope(
        randomPoint = it.position,

        center = currentRect.center,
        width = currentRect.width,
        height = currentRect.height,

        offset = 0f
    ) }?: false

    onDraggingInScopeChange(draggingInScope)

    Box(modifier = modifier.onGloballyPositioned { layoutCoordinates ->
        currentRect = layoutCoordinates.boundsInWindow()
    }) {
        block.invoke()
    }
}
@Composable
fun DraggingItemDetector2(
    modifier: Modifier = Modifier,

    draggingItem: DraggingItem?,
    onDraggingInScopeChange: (Boolean) -> Unit,

    enabled: Boolean = true,

    block: @Composable () -> Unit
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }
    val draggingInScope = draggingItem?.let { enabled && isInScope(
        randomPoint = it.position,

        center = currentRect.center,
        width = currentRect.width,
        height = currentRect.height,

        offset = 0f
    ) }?: false
    LaunchedEffect(key1 = draggingInScope) {
        onDraggingInScopeChange(draggingInScope)
    }

    Box(modifier = modifier.onGloballyPositioned { layoutCoordinates ->
        currentRect = layoutCoordinates.boundsInWindow()
    }) {
        block.invoke()
    }
}
@Composable
fun DraggingItemDetector(
    modifier: Modifier = Modifier,

    enabled: Boolean = true,
    draggingItem: DraggingItem?,

    onOffsetChange: (Float) -> Unit = {},
    onDraggingInScopeChange: (Boolean) -> Unit,

    block: @Composable (Dp) -> Unit
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }
    var translationXForDragDp by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density

    val draggingInScope = draggingItem?.let { enabled && isInScope(
        randomPoint = it.position,

        center = currentRect.center,
        width = currentRect.width,
        height = currentRect.height,

        offset = translationXForDragDp * density
    ) }?: false
    LaunchedEffect(key1 = draggingInScope) {
        onDraggingInScopeChange(draggingInScope)
        val transTarget = if(draggingInScope) {
            draggingItem!!.width.value
        } else 0f

        ValueAnimator.ofFloat(translationXForDragDp, transTarget).apply {
            duration = 250
            addUpdateListener { animator ->
                translationXForDragDp = animator.animatedValue as Float
                onOffsetChange.invoke(-translationXForDragDp * density)
            }
        }.start()
    }

    Box(modifier = modifier.onGloballyPositioned { layoutCoordinates ->
        currentRect = layoutCoordinates.boundsInWindow()
    }) {
        block.invoke(translationXForDragDp.dp)
    }
}