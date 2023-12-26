package com.guhao.opensource.cutme.android

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

class Track(
    val pieces: List<Piece>
)

@Composable
fun Track(
    track: Track,
    onTrackChange: (Track) -> Unit,

    selectedSet: Set<Piece>,
    onSelectedSetChange: (Set<Piece>) -> Unit,

    requestAdding: ((List<SelectInfo>) -> Unit) -> Unit,

    zoom: Float = 1f,

    totalDuration: Long,

    draggingItem: DraggingItem?,
    onDraggingItemChange: (DraggingItemChangeReason, DraggingItem?) -> Unit,

    onDraggingInScope: (Int) -> Unit,
    onInScopePiecesClear: () -> Unit,

    hasDroppingTarget: () -> Boolean,

    maxTrackLengthDp: Int,
    scrollingCompensationX: Int,

    hasPieceFlying: Boolean, // Globally
    onHasPieceFlying: (Boolean) -> Unit, // This will be called directly.

    originalPosAnimationConcatenation: AnimationConcatenation?,
    onOriginalPosAnimationConcatenationFinish: () -> Unit,

    targetPosAnimationConcatenation: AnimationConcatenation?,
    onTargetPosAnimationConcatenationFinish: () -> Unit,

    onBlankPieceWidthCalculated: (Int) -> Unit,
) {
    val draggingOffsetMap = remember { mutableMapOf<Int, MutableState<Offset>>() }

    val gotPieceFlyingInThisTrack = draggingOffsetMap.values.any { it.value != Offset.Zero }
    onHasPieceFlying.invoke(gotPieceFlyingInThisTrack)
    Row(
        modifier = Modifier
            .zIndex(if (gotPieceFlyingInThisTrack) 1f else 0f)
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Spacer(modifier = Modifier.width((maxTrackLengthDp / 2).dp))

        val compensationMap = remember { mutableStateListOf<Pair<Int, Float>>() }
        val inScopePieceSet = remember { HashSet<Int>() }

        var currentDraggingIndex by remember { mutableStateOf<Int?>(null) }

        track.pieces.forEachIndexed { index, piece ->

            val selected = selectedSet.contains(piece)
            val pieceWidth = (maxTrackLengthDp * (piece.duration / totalDuration.toFloat())).roundToInt().dp

            val draggingOffsetState = remember { mutableStateOf(Offset.Zero) }.also { draggingOffsetMap[index] = it }

            Piece(
                zoom = zoom,
                piece = piece,
                width = pieceWidth,
                selected = selected,
                onClick = {
                    onSelectedSetChange(if (selected) {
                        selectedSet
                            .filter { it != piece }
                            .toSet()
                    } else selectedSet + piece)
                },
                onLongClick = {
                    onSelectedSetChange(setOf())
                },
                draggingItem = draggingItem,
                onDraggingItemChange = { reason, item ->
                    onDraggingItemChange.invoke(reason, item?.copy(pieceIndex = index))

                    currentDraggingIndex = if(item == null) {
                        null
                    } else index
                },
                scrollingCompensationX = scrollingCompensationX,
                piecesPaddingCompensationX = compensationMap.filter { it.first < index }.map { it.second }.sum(),
                onPiecesPaddingCompensationXChange = { x ->
                    compensationMap.apply {
                        val ready = Pair(index, x)

                        if(any { it.first == index })
                            set(index, ready)
                        else
                            add(ready)
                    }
                },
                draggingOffsetState = draggingOffsetState,
                onDraggingInScopeChange = {
                    if(it) {
                        onDraggingInScope.invoke(index)
                        inScopePieceSet.add(index)
                    } else {
                        inScopePieceSet.remove(index)
                        if(inScopePieceSet.isEmpty()) onInScopePiecesClear()
                    }
                },
                enableDraggingDetector = currentDraggingIndex?.let { index != it + 1 } ?: true,

                hasDroppingTarget = hasDroppingTarget,

                originalPosAnimationConcatenation = if(originalPosAnimationConcatenation?.originalPosition?.second == index) originalPosAnimationConcatenation.shouldPaddingForOriginal else null,
                onOriginalPosAnimationConcatenationFinished = onOriginalPosAnimationConcatenationFinish,
                targetPosAnimationConcatenation = if(targetPosAnimationConcatenation?.targetPosition?.second == index) targetPosAnimationConcatenation.animationStartPositionForTarget else null,
                onTargetPosAnimationConcatenationFinish = onTargetPosAnimationConcatenationFinish,
            )
        }

        AnimatedContent(targetState = hasPieceFlying, label = "") { flying ->
            if(flying) {
                BlankPiece(
                    modifier = Modifier.height(pieceHeight),
                    draggingItem = draggingItem,
                    onDraggingInScopeChange = {
                        if(it) {
                            onDraggingInScope.invoke(-1) // Use -1 to indicate adding to the end
                            inScopePieceSet.add(-1)
                        } else {
                            inScopePieceSet.remove(-1)
                            if(inScopePieceSet.isEmpty()) onInScopePiecesClear()
                        }
                    },
                    onWidthCalculated = onBlankPieceWidthCalculated
                )
            } else {
                AddPieceButton {
                    requestAdding.invoke { result: List<SelectInfo> ->
                        onTrackChange(Track(track.pieces + result.map {
                            Piece(
                                model = it.path, end = (it.duration?:2000) - 1) } ))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width((maxTrackLengthDp / 2 - 48).dp))
    }
}

@Composable
fun BlankPiece(
    modifier: Modifier,

    draggingItem: DraggingItem?,
    onDraggingInScopeChange: (Boolean) -> Unit,
    onWidthCalculated: (Int) -> Unit
) {
    Box {
        var currentRect by remember { mutableStateOf(Rect.Zero) }

        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val density = LocalDensity.current.density
        val detectorWidth = (screenWidthDp - (currentRect.left / density).roundToInt()).dp
        var inScope by remember { mutableStateOf(false) }
        DraggingItemDetector(
            modifier = modifier
                .width(detectorWidth)
                .onGloballyPositioned { layoutCoordinates ->
                    currentRect = layoutCoordinates.boundsInWindow()
                },
            draggingItem = draggingItem,
            onDraggingInScopeChange = {
                inScope = it
                onDraggingInScopeChange.invoke(it)
            })

        val draggingItemLeft = draggingItem?.let { it.position.x / density - it.width.value / 2 }?.coerceAtLeast(0f)
        val blankPieceWidth = draggingItemLeft?.minus(currentRect.left / density)?.coerceAtLeast(0f) ?: 0f

        if(inScope) onWidthCalculated.invoke(blankPieceWidth.roundToInt())

        AnimatedVisibility(
            enter = fadeIn(), exit = fadeOut(),
            visible = inScope
        ) {
            PieceCard(
                modifier = Modifier
                    .alpha(0.99f)
                    .height(pieceHeight)
                    .width(blankPieceWidth.dp),
            )
        }
    }
}

@Composable
fun AddPieceButton(
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 10.dp),
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add",
            tint = Color.White)
    }
}