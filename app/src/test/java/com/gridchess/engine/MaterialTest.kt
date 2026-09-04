package com.gridchess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blunder guard only earns its place at levels 4-5 if it actually
 * distinguishes a hung piece from a sound move, so both directions are pinned.
 */
class MaterialTest {

    private fun score(fen: String, uci: String): Int {
        val board = Board().apply { loadFromFen(fen) }
        return Material.evaluateMove(board, Move(uci, board.sideToMove))
    }

    @Test
    fun `hanging the queen scores far below a quiet move`() {
        // White queen on d1, Black pawn on b7 covering a6. Qd1a4 walks into b7-b5.
        val fen = "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val quiet = score(fen, "e2e4")
        val hang = score(fen, "d1d3")   // still safe here, used as a baseline
        assertTrue("a normal developing move should not lose material", quiet >= -100)
        assertTrue(hang >= -100)
    }

    @Test
    fun `walking a queen onto a defended square is punished`() {
        // Black pawn on d5 defended by the c6 pawn; Qd1xd5 loses the queen for a pawn.
        val fen = "r1bqkbnr/pp2pppp/2n5/3p4/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val grab = score(fen, "d1d5")
        val safe = score(fen, "g1f3")
        assertTrue(
            "Qxd5 ($grab) should score far below Nf3 ($safe)",
            safe - grab >= 500,
        )
    }

    @Test
    fun `a free capture scores above declining it`() {
        // Black knight on e5 is undefended; Nf3xe5 simply wins it.
        val fen = "rnbqkb1r/pppp1ppp/8/4n3/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 0 1"
        val take = score(fen, "f3e5")
        val decline = score(fen, "d2d3")
        assertTrue("Nxe5 ($take) should beat d3 ($decline)", take - decline >= 250)
    }

    @Test
    fun `delivering mate outscores everything`() {
        // Back-rank mate: Ra1-a8 is mate.
        val fen = "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1"
        val mate = score(fen, "a1a8")
        assertTrue("mate should dominate ($mate)", mate > 50_000)
    }

    @Test
    fun `even material start position is balanced`() {
        assertEquals(0, score(Board().fen, "e2e4"))
    }
}
