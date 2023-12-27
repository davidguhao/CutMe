package com.guhao.opensource.cutme.android

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.animation.addListener
import kotlin.math.abs
import kotlin.math.roundToInt

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


const val DETECTOR_DEBUG_MODE = false
val DETECTOR_DEBUG_MODE_COLOR = Color(0x44ffffff)

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

    debugMode: Boolean = DETECTOR_DEBUG_MODE,

    content: @Composable () -> Unit = {}
) {
    var currentRect by remember { mutableStateOf(Rect.Zero) }
    val draggingInScope = draggingItem?.let { enabled && isInScopeX(
        it.position,

        currentRect.center,
        currentRect.width,
        currentRect.height,
    ) } ?: false
    LaunchedEffect(key1 = draggingInScope) {
        onDraggingInScopeChange(draggingInScope)
    }

    val effectiveModifier = if(debugMode)
        modifier.background(color = DETECTOR_DEBUG_MODE_COLOR)
    else
        modifier

    Box(modifier = effectiveModifier
        .onGloballyPositioned { layoutCoordinates ->
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

    onDraggingInScopeChange: (Boolean) -> Unit,

    block: @Composable (Dp) -> Unit
) {
    var translationXForDragDp by remember { mutableIntStateOf(0) }
    var translationXHitMaxValue by remember { mutableStateOf(false) }
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

    var latestInScope by remember { mutableStateOf(false) }

    if(draggingItem == null && latestInScope && translationXHitMaxValue) { // Flash
        translationXForDragDp = 0
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
            latestInScope = draggingInScope

            translationXHitMaxValue = false
            onDraggingInScopeChange.invoke(false)

            val transTarget = if (draggingInScope) {
                draggingItem!!.width.value.roundToInt()
            } else 0

            ValueAnimator.ofInt(translationXForDragDp, transTarget).apply {
                duration = 250
                addUpdateListener { animator ->
                    translationXForDragDp = animator.animatedValue as Int
                }
                addListener(
                    onEnd = {
                        translationXHitMaxValue = true
                        onDraggingInScopeChange.invoke(latestInScope)
                    },
                )
            }.start()
        },
        content = {
            block.invoke(translationXForDragDp.dp)
        }
    )
}