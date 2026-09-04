package com.gridchess.game

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.gridchess.engine.Difficulty

enum class Outcome { PLAYING, WHITE_WINS, BLACK_WINS, DRAW }

/** A pending promotion: the move is known except for the piece to promote to. */
data class PendingPromotion(val from: Square, val to: Square)

data class GameState(
    val pieces: List<Piece> = emptyList(),
    val sideToMove: Side = Side.WHITE,
    val playerSide: Side = Side.WHITE,
    val difficulty: Difficulty = Difficulty.THREE,
    val selected: Square? = null,
    val targets: Set<Square> = emptySet(),
    val lastMove: Move? = null,
    val checkSquare: Square? = null,
    val thinking: Boolean = false,
    val outcome: Outcome = Outcome.PLAYING,
    val outcomeReason: String = "",
    val promotion: PendingPromotion? = null,
    val canUndo: Boolean = false,
) {
    val isPlayerTurn: Boolean
        get() = outcome == Outcome.PLAYING && sideToMove == playerSide && !thinking

    val flipped: Boolean get() = playerSide == Side.BLACK
}
