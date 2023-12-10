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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
    onZoomChange: (expectedZoom: Float) -> Unit,

    totalDuration: Long,

    draggingItem: DraggingItem?,
    onDraggingItemChange: (DraggingItemChangeReason, DraggingItem?) -> Unit,

    onDraggingInScope: (Int) -> Unit,
    onInScopePiecesClear: () -> Unit,
    draggingHasTarget: () -> Boolean
) {
    val draggingOffsetMap = remember { mutableMapOf<Int, MutableState<Offset>>() }

    val gotPieceFlying = draggingOffsetMap.values.any { it.value != Offset.Zero }
    Row(
        modifier = Modifier
            .zIndex(if (gotPieceFlying) 1f else 0f)
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        val maxTrackLength = LocalConfiguration.current.screenWidthDp

        Spacer(modifier = Modifier.width((maxTrackLength / 2).dp))

        val compensationMap = remember { mutableStateListOf<Pair<Int, Float>>() }
        val inScopePieceSet = remember { HashSet<Int>() }

        track.pieces.forEachIndexed { index, piece ->

            val selected = selectedSet.contains(piece)
            val pieceWidth = (maxTrackLength * (piece.duration / totalDuration.toFloat())).roundToInt().dp
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
                },
                compensationTranslationX = compensationMap.filter { it.first < index }.map { it.second }.sum(),
                onCompensationTranslationXChange = { x ->
                    compensationMap.apply {
                        val ready = Pair(index, x)

                        if(any { it.first == index })
                            set(index, ready)
                        else
                            add(ready)
                    }
                },
                draggingOffsetState = remember { mutableStateOf(Offset.Zero) }.also { draggingOffsetMap.put(index, it) },
                onDraggingInScopeChange = {
                    if(it) {
                        onDraggingInScope.invoke(index)
                        inScopePieceSet.add(index)
                    } else {
                        inScopePieceSet.remove(index)
                        if(inScopePieceSet.isEmpty()) onInScopePiecesClear()
                    }
                },
                draggingHasTarget = draggingHasTarget
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
            }
        ) {
            requestAdding.invoke { result: List<SelectInfo> ->
                onTrackChange(Track(track.pieces + result.map {
                    Piece(
                        model = it.path, end = (it.duration?:2000) - 1) } ))
            }
        }
        Spacer(modifier = Modifier.width((maxTrackLength / 2 - 48).dp))
    }
}
@Composable
fun AddPieceButton(
    draggingItem: DraggingItem?,
    onDraggingInScopeChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    DraggingItemDetector(
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