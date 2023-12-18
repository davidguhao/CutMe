package com.guhao.opensource.cutme.android

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

    shouldAnimateDraggingItemBack: () -> Boolean,

    maxTrackLengthDp: Int,
) {
    val draggingOffsetMap = remember { mutableMapOf<Int, MutableState<Offset>>() }

    val gotPieceFlying = draggingOffsetMap.values.any { it.value != Offset.Zero }
    Row(
        modifier = Modifier
            .zIndex(if (gotPieceFlying) 1f else 0f)
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
            val flying = draggingOffsetState.value != Offset.Zero

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
                compensationX = if(flying) compensationMap.filter { it.first < index }.map { it.second }.sum() else 0f,
                onCompensationXChange = { x ->
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

                shouldAnimateDraggingItemBack = shouldAnimateDraggingItemBack
            )
        }
        AddPieceButton(
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
            enableDragDetector = !(gotPieceFlying && draggingItem != null && draggingItem.pieceIndex + 1 == track.pieces.size)
        ) {
            requestAdding.invoke { result: List<SelectInfo> ->
                onTrackChange(Track(track.pieces + result.map {
                    Piece(
                        model = it.path, end = (it.duration?:2000) - 1) } ))
            }
        }
        Spacer(modifier = Modifier.width((maxTrackLengthDp / 2 - 48).dp))
    }
}

@Composable
fun AddPieceButton(
    draggingItem: DraggingItem?,
    onDraggingInScopeChange: (Boolean) -> Unit,
    enableDragDetector: Boolean = true,

    onClick: () -> Unit,
) {
    DraggingItemDetector(
        enabled = enableDragDetector,
        draggingItem = draggingItem,
        onDraggingInScopeChange = onDraggingInScopeChange) {
        IconButton(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 10.dp, start = it),
            onClick = onClick,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.White)
        }
    }

}