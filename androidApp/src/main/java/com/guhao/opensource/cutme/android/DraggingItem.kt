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
)

fun isInScope(
    randomPoint: Offset,
    center: Offset,
    width: Float,
    height: Float
): Boolean {

    val xInScope = abs(randomPoint.x - center.x) < width / 2
    val yInScope = abs(randomPoint.y - center.y) < height / 2

    return xInScope && yInScope
}

@Composable
fun DraggingItemDetector(
    modifier: Modifier = Modifier,

    enabled: Boolean = true,

    draggingItem: DraggingItem?,
    isInScopeX: (
        randomPoint: Offset,
        center: Offset,
        width: Float,
        height: Float) -> Boolean = ::isInScope,
    onDraggingInScopeChange: (Boolean) -> Unit,

    content: @Composable () -> Unit = {}
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }
    val draggingInScope = draggingItem?.let { enabled && isInScopeX(
        it.position,

        currentRect.center,
        currentRect.width,
        currentRect.height,
    ) }?: false
    LaunchedEffect(key1 = draggingInScope) {
        onDraggingInScopeChange(draggingInScope)
    }

    Box(modifier = modifier.onGloballyPositioned { layoutCoordinates ->
        currentRect = layoutCoordinates.boundsInWindow()
    }) {
        content.invoke()
    }
}

@Composable
fun TranslationXDraggingItemDetector(
    modifier: Modifier = Modifier,

    enabled: Boolean = true,

    draggingItem: DraggingItem?,

    onOffsetChange: (Float) -> Unit = {},
    onDraggingInScopeChange: (Boolean) -> Unit,

    block: @Composable (Dp) -> Unit
) {
    var translationXForDragDp by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density

    fun isInScopeOffset(
        randomPoint: Offset,
        center: Offset,
        width: Float,
        height: Float,

        offset: Float,
    ): Boolean {
        return isInScope(
            randomPoint, center.copy(x = center.x - offset / 2), width + offset, height)
    }

    DraggingItemDetector(
        modifier = modifier,
        enabled = enabled,
        draggingItem = draggingItem,
        isInScopeX = { randomPoint, center, width, height ->

            isInScopeOffset(
                randomPoint = randomPoint,
                center = center,
                width = width,
                height = height,
                offset = translationXForDragDp * density
            )
        },
        onDraggingInScopeChange = { draggingInScope ->

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
        },
        content = {
            block.invoke(translationXForDragDp.dp)
        }
    )
}