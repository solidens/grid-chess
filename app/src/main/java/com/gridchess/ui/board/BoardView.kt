package com.gridchess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.gridchess.ui.theme.Grid
import com.gridchess.ui.theme.GridTokens

/**
 * Everything the board needs to paint one frame. Deliberately a plain value
 * type: the view does no chess reasoning of its own, it only draws.
 */
data class BoardRender(
    val pieces: List<Piece>,          // 64 entries, index 0 = a1 (chesslib ordinal order)
    val selected: Square? = null,
    val targets: Set<Square> = emptySet(),
    val lastMoveFrom: Square? = null,
    val lastMoveTo: Square? = null,
    val checkSquare: Square? = null,
    val flipped: Boolean = false,     // true when the human plays Black
    val movableSide: Side? = null,    // whose pieces the human may pick up right now
)

@Composable
fun ChessBoard(
    render: BoardRender,
    onSquareTap: (Square) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { 2.5.dp.toPx() }
    val frame = GridTokens.BorderThick

    // Gesture lambdas outlive a single composition, so they have to read the
    // current board rather than the one captured when the handler was set up.
    val current by rememberUpdatedState(render)
    val tap by rememberUpdatedState(onSquareTap)

    var dragFrom by remember { mutableStateOf<Square?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Unspecified) }

    fun reset() {
        dragFrom = null
        dragAt = Offset.Unspecified
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Grid.Ink)
            .border(frame, Grid.Ink)
            .padding(frame),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // Tap-tap and drag are separate handlers: a drag only begins once
                // the pointer passes touch slop, so the two never fight.
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        tap(squareAt(offset, size.width / 8f, current.flipped))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cell = size.width / 8f
                            val from = squareAt(offset, cell, current.flipped)
                            val piece = current.pieces.getOrNull(from.ordinal) ?: Piece.NONE
                            // Only the side to move can be picked up; anything else
                            // falls through and the board simply does not move.
                            if (piece != Piece.NONE && piece.pieceSide == current.movableSide) {
                                dragFrom = from
                                dragAt = offset
                                tap(from)          // selects it, so the targets light up
                            }
                        },
                        onDrag = { change, _ ->
                            if (dragFrom != null) {
                                change.consume()
                                dragAt = change.position
                            }
                        },
                        onDragEnd = {
                            val from = dragFrom
                            if (from != null && dragAt.isSpecified) {
                                val to = squareAt(dragAt, size.width / 8f, current.flipped)
                                // Dropping a piece back where it came from is a
                                // cancel, not a move -- leave it selected.
                                if (to != from) tap(to)
                            }
                            reset()
                        },
                        onDragCancel = ::reset,
                    )
                },
        ) {
            val cell = size.width / 8f
            val hovered = if (dragFrom != null && dragAt.isSpecified) {
                squareAt(dragAt, cell, render.flipped)
            } else {
                null
            }

            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val square = squareAt(col, row, render.flipped)
                    val topLeft = Offset(col * cell, row * cell)
                    val rect = Rect(topLeft, Size(cell, cell))

                    // Board ground. Light/dark alternates on file+rank parity.
                    val light = (col + row) % 2 == 0
                    drawRect(
                        color = if (light) Grid.SquareLight else Grid.SquareDark,
                        topLeft = topLeft,
                        size = Size(cell, cell),
                    )

                    // Last move: a flat yellow wash under everything else.
                    if (square == render.lastMoveFrom || square == render.lastMoveTo) {
                        drawRect(Grid.LastMove, topLeft, Size(cell, cell))
                    }

                    // Check: solid red square, the loudest state on the board.
                    if (square == render.checkSquare) {
                        drawRect(Grid.Check, topLeft, Size(cell, cell))
                    }

                    // Selection: a thick yellow inset frame, not a fill, so the
                    // piece underneath stays fully legible.
                    if (square == render.selected) {
                        val inset = strokePx * 1.6f
                        drawRect(
                            color = Grid.Selected,
                            topLeft = topLeft + Offset(inset / 2f, inset / 2f),
                            size = Size(cell - inset, cell - inset),
                            style = Stroke(width = inset),
                        )
                    }

                    // Where the dragged piece would land.
                    if (square == hovered && square != dragFrom) {
                        val inset = strokePx * 2f
                        drawRect(
                            color = Grid.Hint,
                            topLeft = topLeft + Offset(inset / 2f, inset / 2f),
                            size = Size(cell - inset, cell - inset),
                            style = Stroke(width = inset),
                        )
                    }

                    val piece = render.pieces.getOrNull(square.ordinal) ?: Piece.NONE
                    // The piece in hand is drawn at the pointer, not on its square.
                    if (piece != Piece.NONE && square != dragFrom) {
                        drawPiece(piece, rect, Grid.Ink, Grid.Paper, strokePx)
                    }

                    // Legal targets. A dot on an empty square, a ring around an
                    // occupied one -- the classic capture/quiet distinction.
                    if (square in render.targets) {
                        if (piece == Piece.NONE) {
                            drawCircle(
                                color = Grid.Hint,
                                radius = cell * 0.13f,
                                center = rect.center,
                            )
                        } else {
                            drawCircle(
                                color = Grid.Hint,
                                radius = cell * 0.42f,
                                center = rect.center,
                                style = Stroke(width = strokePx * 1.6f),
                            )
                        }
                    }
                }
            }

            // Grid lines sit on top of every fill.
            for (i in 1 until 8) {
                val p = i * cell
                drawLine(Grid.Ink, Offset(p, 0f), Offset(p, size.height), strokeWidth = 1f)
                drawLine(Grid.Ink, Offset(0f, p), Offset(size.width, p), strokeWidth = 1f)
            }

            // The piece in hand, last, so it rides over everything, and oversized
            // because a lifted piece should read as lifted.
            //
            // It stays centred on the contact point rather than raised above it.
            // Raising it looks better under a thumb but splits what you see from
            // where it lands -- the piece hovers over one square while the drop
            // resolves to another. The blue frame around the pointer is the aiming
            // aid instead, and it stays visible outside the contact patch.
            val lifted = dragFrom
            if (lifted != null && dragAt.isSpecified) {
                val piece = render.pieces.getOrNull(lifted.ordinal) ?: Piece.NONE
                if (piece != Piece.NONE) {
                    drawPiece(
                        piece,
                        Rect(center = dragAt, radius = cell * 1.15f / 2f),
                        Grid.Ink,
                        Grid.Paper,
                        strokePx,
                    )
                }
            }
        }
    }
}

private fun squareAt(offset: Offset, cell: Float, flipped: Boolean): Square {
    val col = (offset.x / cell).toInt().coerceIn(0, 7)
    val row = (offset.y / cell).toInt().coerceIn(0, 7)
    return squareAt(col, row, flipped)
}

/**
 * Maps a (col, row) cell in screen space to a board square.
 *
 * Row 0 is the top of the screen. Unflipped that is rank 8; flipped it is
 * rank 1 and files run h..a.
 */
private fun squareAt(col: Int, row: Int, flipped: Boolean): Square {
    val file = if (flipped) 7 - col else col
    val rank = if (flipped) row else 7 - row
    return Square.squareAt(rank * 8 + file)
}
