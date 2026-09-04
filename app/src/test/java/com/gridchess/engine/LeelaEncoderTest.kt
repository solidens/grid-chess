package com.gridchess.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The encoder is the one place where a silent, plausible-looking bug would
 * quietly halve playing strength -- the net would still return a legal move,
 * just a bad one -- so its invariants are pinned here rather than eyeballed.
 */
class LeelaEncoderTest {

    private fun planeOf(t: FloatArray, plane: Int) = t.copyOfRange(plane * 64, plane * 64 + 64)

    private fun setBits(plane: FloatArray) =
        plane.indices.filter { plane[it] == 1f }.toSet()

    private fun encode(vararg uci: String): FloatArray {
        val board = Board()
        val positions = mutableListOf(board.clone())
        for (m in uci) {
            board.doMove(Move(m, board.sideToMove))
            positions.add(board.clone())
        }
        return LeelaEncoder.encode(positions, List(positions.size) { false })
    }

    @Test
    fun `start position puts our pawns on the second rank`() {
        val t = encode()
        // Bits are rank * 8 + file, so White's pawns are 8..15 and the king is e1 = 4.
        assertEquals((8..15).toSet(), setBits(planeOf(t, 0)))
        assertEquals(setOf(4), setBits(planeOf(t, 5)))
        // Their pawns sit on rank 7.
        assertEquals((48..55).toSet(), setBits(planeOf(t, 6)))
        assertEquals(setOf(60), setBits(planeOf(t, 11)))
    }

    @Test
    fun `start position has all four castling planes and the edge plane set`() {
        val t = encode()
        for (plane in 104..107) {
            assertTrue("plane $plane should be all ones", planeOf(t, plane).all { it == 1f })
        }
        assertTrue("side-to-move plane is zero for White", planeOf(t, 108).all { it == 0f })
        assertTrue("halfmove clock is zero", planeOf(t, 109).all { it == 0f })
        assertTrue("plane 110 is always zero", planeOf(t, 110).all { it == 0f })
        assertTrue("plane 111 is all ones", planeOf(t, 111).all { it == 1f })
    }

    @Test
    fun `no history yet means the older boards stay empty`() {
        val t = encode()
        // Only history step 0 is filled; steps 1..7 are planes 13..103.
        assertTrue(t.copyOfRange(13 * 64, 104 * 64).all { it == 0f })
    }

    @Test
    fun `black to move mirrors the board and swaps ours for theirs`() {
        val t = encode("e2e4")   // Black to move
        // "Ours" is now Black. Black's pawns are on rank 7, which mirrors to rank 2.
        assertEquals((8..15).toSet(), setBits(planeOf(t, 0)))
        // Black's king e8 (bit 60) mirrors to e1 (bit 4).
        assertEquals(setOf(4), setBits(planeOf(t, 5)))
        // "Theirs" is White: pawns on rank 2 except e2, which is now on e4.
        // Mirrored, rank 2 -> rank 7 (48..55) and e4 -> e5 (bit 36).
        val theirPawns = setBits(planeOf(t, 6))
        assertEquals((48..55).toSet() - setOf(52) + setOf(36), theirPawns)
        assertTrue("side-to-move plane is all ones for Black", planeOf(t, 108).all { it == 1f })
    }

    @Test
    fun `history step one holds the previous position in the current frame`() {
        val t = encode("e2e4")
        // One ply back it was White to move, but the planes are rewritten into
        // Black's frame, so "ours" there is still Black: pawns on mirrored rank 2.
        assertEquals((8..15).toSet(), setBits(planeOf(t, 13 + 0)))
        // And "theirs" is White's start position, mirrored to rank 7.
        assertEquals((48..55).toSet(), setBits(planeOf(t, 13 + 6)))
    }

    @Test
    fun `losing castling rights clears the matching planes`() {
        val t = encode("e2e4", "e7e5", "e1e2")   // White king steps up; Black to move
        // "Ours" is Black, who still has both rights; "theirs" is White, who has none.
        assertTrue(planeOf(t, 104).all { it == 1f })
        assertTrue(planeOf(t, 105).all { it == 1f })
        assertTrue(planeOf(t, 106).all { it == 0f })
        assertTrue(planeOf(t, 107).all { it == 0f })
    }

    @Test
    fun `halfmove clock is written raw across the whole plane`() {
        val t = encode("g1f3", "g8f6", "f3g1")   // three reversible moves
        assertTrue(planeOf(t, 109).all { it == 3f })
    }

    @Test
    fun `tensor is exactly 112 planes`() {
        assertEquals(112 * 64, encode().size)
    }
}
