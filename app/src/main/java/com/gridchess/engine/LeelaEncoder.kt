package com.gridchess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.CastleRight
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side

/**
 * Builds lc0's INPUT_CLASSICAL_112_PLANE tensor, which is what every Maia
 * network expects. Layout, straight from lc0's `encoder.cc`:
 *
 *   planes 13*i + 0..5   our P N B R Q K   (i = 0 is the current position,
 *   planes 13*i + 6..11  their P N B R Q K   i = 1 one ply back, up to i = 7)
 *   plane  13*i + 12     1 if that position had already occurred
 *   plane  104           we can castle queenside
 *   plane  105           we can castle kingside
 *   plane  106           they can castle queenside
 *   plane  107           they can castle kingside
 *   plane  108           all ones when Black is to move
 *   plane  109           the halfmove (fifty-move) clock, raw
 *   plane  110           all zeros (a move counter in a long-dead version)
 *   plane  111           all ones, so the network can find the board edges
 *
 * Two conventions matter. Everything is written from the perspective of the
 * side to move -- "ours" is always the mover, and for Black the board is
 * rank-mirrored -- and that holds for the history planes too, so a position
 * from one ply ago is re-oriented into the current mover's frame rather than
 * its own. Missing history (the opening moves of a game) stays zero, matching
 * lc0's behaviour when it plays out from the start position.
 */
object LeelaEncoder {

    const val PLANES = 112
    const val HISTORY = 8
    private const val PLANES_PER_BOARD = 13
    private const val AUX = PLANES_PER_BOARD * HISTORY   // 104
    const val TENSOR_SIZE = PLANES * 64

    /** Piece planes are written in this order for each side. */
    private val WHITE_ORDER = arrayOf(
        Piece.WHITE_PAWN, Piece.WHITE_KNIGHT, Piece.WHITE_BISHOP,
        Piece.WHITE_ROOK, Piece.WHITE_QUEEN, Piece.WHITE_KING,
    )
    private val BLACK_ORDER = arrayOf(
        Piece.BLACK_PAWN, Piece.BLACK_KNIGHT, Piece.BLACK_BISHOP,
        Piece.BLACK_ROOK, Piece.BLACK_QUEEN, Piece.BLACK_KING,
    )

    /**
     * @param positions oldest first, [positions].last() is the position to move in.
     *   At most the final [HISTORY] entries are read.
     * @param repeated one flag per entry of [positions]: true when that position
     *   had already appeared earlier in the game.
     */
    fun encode(positions: List<Board>, repeated: List<Boolean>): FloatArray {
        require(positions.isNotEmpty()) { "need at least the current position" }
        require(repeated.size == positions.size) { "repetition flags must match positions" }

        val out = FloatArray(TENSOR_SIZE)
        val current = positions.last()
        val us = current.sideToMove
        val them = us.flip()
        val blackToMove = us == Side.BLACK

        val ourOrder = if (blackToMove) BLACK_ORDER else WHITE_ORDER
        val theirOrder = if (blackToMove) WHITE_ORDER else BLACK_ORDER

        for (i in 0 until HISTORY) {
            val idx = positions.size - 1 - i
            if (idx < 0) break                       // no history yet: leave zeros
            val board = positions[idx]
            val base = i * PLANES_PER_BOARD

            for (p in 0 until 6) {
                writeBitboard(out, base + p, board.getBitboard(ourOrder[p]), blackToMove)
                writeBitboard(out, base + 6 + p, board.getBitboard(theirOrder[p]), blackToMove)
            }
            if (repeated[idx]) fill(out, base + 12, 1f)
        }

        val ourCastle = current.getCastleRight(us)
        val theirCastle = current.getCastleRight(them)
        if (ourCastle.canCastleQueenSide()) fill(out, AUX + 0, 1f)
        if (ourCastle.canCastleKingSide()) fill(out, AUX + 1, 1f)
        if (theirCastle.canCastleQueenSide()) fill(out, AUX + 2, 1f)
        if (theirCastle.canCastleKingSide()) fill(out, AUX + 3, 1f)

        if (blackToMove) fill(out, AUX + 4, 1f)
        fill(out, AUX + 5, current.halfMoveCounter.toFloat())
        // AUX + 6 stays zero.
        fill(out, AUX + 7, 1f)

        return out
    }

    /**
     * lc0 bit `n` and chesslib bit `n` both mean rank n/8, file n%8, so the only
     * adjustment for Black is a vertical flip -- and flipping ranks while keeping
     * files is exactly a byte reversal of the 64-bit board.
     */
    private fun writeBitboard(out: FloatArray, plane: Int, bitboard: Long, mirror: Boolean) {
        var bb = if (mirror) java.lang.Long.reverseBytes(bitboard) else bitboard
        val offset = plane * 64
        while (bb != 0L) {
            val bit = java.lang.Long.numberOfTrailingZeros(bb)
            out[offset + bit] = 1f
            bb = bb and (bb - 1)
        }
    }

    private fun fill(out: FloatArray, plane: Int, value: Float) {
        java.util.Arrays.fill(out, plane * 64, plane * 64 + 64, value)
    }

    private fun CastleRight.canCastleQueenSide() =
        this == CastleRight.QUEEN_SIDE || this == CastleRight.KING_AND_QUEEN_SIDE

    private fun CastleRight.canCastleKingSide() =
        this == CastleRight.KING_SIDE || this == CastleRight.KING_AND_QUEEN_SIDE
}
