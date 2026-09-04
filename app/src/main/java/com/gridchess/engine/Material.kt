package com.gridchess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator

/**
 * A deliberately tiny material check, used only by the blunder guard at levels
 * 4-5. It is not an evaluation function and is not trying to play chess -- it
 * answers one question: after this move, does the opponent win material by
 * force in a single capture that we cannot recapture profitably?
 *
 * Replies are restricted to captures, since a quiet move cannot win material
 * immediately and searching them would cost far more for no added signal.
 */
object Material {

    private const val MATE = 100_000

    private val VALUE = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 0,
    )

    /** Centipawn balance for [move]'s mover after a capture-only 3-ply probe. */
    fun evaluateMove(board: Board, move: Move): Int {
        val probe = board.clone()
        val us = probe.sideToMove
        probe.doMove(move)

        if (probe.isMated) return MATE            // we delivered mate
        if (probe.isStaleMate) return 0

        val replies = MoveGenerator.generateLegalMoves(probe)
        if (replies.isEmpty()) return balance(probe, us)

        var worst = Int.MAX_VALUE
        for (reply in replies) {
            if (!isCapture(probe, reply)) continue
            probe.doMove(reply)
            worst = minOf(worst, bestRecapture(probe, us))
            probe.undoMove()
        }
        // The opponent also always has the option of not capturing at all.
        val quiet = if (isMatedAfterAnyReply(probe, us)) -MATE else balance(probe, us)
        return minOf(worst, quiet)
    }

    /** Our best single recapture from [board], scored for [us]. */
    private fun bestRecapture(board: Board, us: Side): Int {
        var best = balance(board, us)
        for (move in MoveGenerator.generateLegalMoves(board)) {
            if (!isCapture(board, move)) continue
            board.doMove(move)
            best = maxOf(best, balance(board, us))
            board.undoMove()
        }
        return best
    }

    /** Cheap mate-in-one detection for the opponent's quiet options. */
    private fun isMatedAfterAnyReply(board: Board, us: Side): Boolean {
        for (move in MoveGenerator.generateLegalMoves(board)) {
            board.doMove(move)
            val mated = board.isMated && board.sideToMove == us
            board.undoMove()
            if (mated) return true
        }
        return false
    }

    private fun isCapture(board: Board, move: Move): Boolean =
        board.getPiece(move.to) != Piece.NONE

    private fun balance(board: Board, us: Side): Int {
        var score = 0
        for (piece in Piece.entries) {
            if (piece == Piece.NONE) continue
            val value = VALUE[piece.pieceType] ?: continue
            val count = java.lang.Long.bitCount(board.getBitboard(piece))
            score += if (piece.pieceSide == us) value * count else -value * count
        }
        return score
    }
}
