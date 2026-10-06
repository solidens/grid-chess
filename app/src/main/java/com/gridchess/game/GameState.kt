package com.gridchess.game

import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.gridchess.engine.Difficulty
import com.gridchess.online.Link
import com.gridchess.online.Status

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
    /** Set while playing a person over the network; null against Maia. */
    val online: OnlineInfo? = null,
) {
    val isPlayerTurn: Boolean
        get() = outcome == Outcome.PLAYING && sideToMove == playerSide && !thinking &&
            (online == null || online.status == Status.PLAYING)

    val flipped: Boolean get() = playerSide == Side.BLACK
}

/** What the server last said about the room, plus the state of the line to it. */
data class OnlineInfo(
    val code: String = "",
    val seated: Boolean = false,
    val status: Status = Status.WAITING,
    val link: Link = Link.CONNECTING,
    val opponentOnline: Boolean = false,
    val drawOffer: Side? = null,
    val rematch: Set<Side> = emptySet(),
    /** Set when the game cannot go on -- a bad key, a wrong code -- and the only way is back. */
    val error: String? = null,
)
