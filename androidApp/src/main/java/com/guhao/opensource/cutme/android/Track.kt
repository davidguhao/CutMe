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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
    onDraggingItemChange: (DraggingItem?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        val maxTrackLength = LocalConfiguration.current.screenWidthDp

        Spacer(modifier = Modifier.width((maxTrackLength / 2).dp))

        track.pieces.forEach { piece ->
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
                onDraggingItemChange = onDraggingItemChange
            )
        }
        AddPieceButton {
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
fun AddPieceButton(onClick: () -> Unit) {
    IconButton(
        modifier = Modifier.padding(vertical = 10.dp),
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add",
            tint = Color.White)
    }
}