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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

    longestDuration: Long,
) {

    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    /**
     * Every piece will have a calculated width for it.
     */
    var trackLength = 0f
    val piece2Width = HashMap<Piece, Int>().apply {
        track.pieces.forEach { piece ->
            (screenWidthDp * (piece.duration / longestDuration.toFloat())).roundToInt().let {
                this[piece] = it
                trackLength += it
            }
        }
    }

    val pieceStates = remember { HashSet<PieceState>() }
    val isAnyPieceMoving = pieceStates.any { it.isMoving() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .zIndex(if (isAnyPieceMoving) 1f else 0f)
    ) {
        Spacer(modifier = Modifier.width((screenWidthDp / 2).dp))

        track.pieces.forEach { piece ->
            val selected = selectedSet.contains(piece)
            val pieceState = rememberPieceState().also { pieceStates.add(it) }
            val currentWidth = piece2Width[piece]!!.dp
            Piece(
                zoom = zoom,
                piece = piece,
                width = currentWidth,
                selected = selected,
                onClick = {
                    onSelectedSetChange(if (selected) {
                        selectedSet
                            .filter { it != piece }
                            .toSet()
                    } else HashSet(selectedSet).apply { add(piece) })
                },
                onLongClick = {
                    onSelectedSetChange(setOf())
                },
                onDragStart = {
                    onZoomChange(1f)
                },
                onDragEnd = {
                },
                onDragCancel = {
                },
                state = pieceState
            )

        }
        IconButton(
            modifier = Modifier.padding(vertical = 10.dp),
            onClick = {
                requestAdding.invoke { result: List<SelectInfo> ->
                    onTrackChange(Track(track.pieces + result.map {
                        Piece(
                            model = it.path, end = (it.duration?:2000) - 1) } ))
                }
            },
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.White)
        }

        Spacer(modifier = Modifier.width((screenWidthDp / 2 - 48).dp))

    }
}