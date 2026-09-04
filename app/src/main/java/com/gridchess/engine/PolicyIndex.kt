package com.gridchess.engine

import android.content.res.AssetManager
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * lc0's fixed 1858-entry policy vector.
 *
 * Two details of the format are easy to get wrong and both are load-bearing:
 *
 *  1. Entries are written from the moving side's own perspective, so a Black
 *     move has to be rank-mirrored before it is looked up.
 *  2. Only q/r/b promotions carry a suffix (indices 1792..1857). A knight
 *     promotion is stored as the plain four-character move -- there is no
 *     "a7a8n" entry.
 */
class PolicyIndex private constructor(private val indexOf: Map<String, Int>) {

    /** Policy slot for [move], or -1 if lc0 has no entry for it. */
    fun slotFor(move: Move, side: Side): Int {
        val from = orient(move.from, side)
        val to = orient(move.to, side)
        val suffix = promotionSuffix(move.promotion)
        return indexOf[from + to + suffix] ?: -1
    }

    private fun orient(square: Square, side: Side): String {
        val name = square.value().lowercase()   // e.g. "e2"
        if (side == Side.WHITE) return name
        val rank = name[1] - '0'
        return "${name[0]}${9 - rank}"          // rank r -> 9-r, files unchanged
    }

    /** Knight promotions are the suffix-less form; NONE is a normal move. */
    private fun promotionSuffix(promotion: Piece): String = when (promotion.pieceType) {
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        else -> ""
    }

    companion object {
        const val SIZE = 1858

        fun load(assets: AssetManager): PolicyIndex {
            val map = HashMap<String, Int>(SIZE * 2)
            assets.open("policy_index.txt").bufferedReader().useLines { lines ->
                lines.forEachIndexed { i, line ->
                    val move = line.trim()
                    if (move.isNotEmpty()) map[move] = i
                }
            }
            require(map.size == SIZE) { "policy_index.txt held ${map.size} moves, expected $SIZE" }
            return PolicyIndex(map)
        }
    }
}
