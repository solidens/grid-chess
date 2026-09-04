package com.gridchess.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Guards the two facts about lc0's policy list that the Kotlin lookup relies
 * on: its length, and that knight promotions have no suffix entry.
 */
class PolicyIndexTest {

    private val moves: List<String> by lazy {
        File("src/main/assets/policy_index.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun `list is exactly 1858 entries`() {
        assertEquals(1858, moves.size)
    }

    @Test
    fun `only queen rook and bishop promotions carry a suffix`() {
        val suffixes = moves.filter { it.length == 5 }.map { it[4] }.toSortedSet()
        assertEquals(setOf('b', 'q', 'r'), suffixes.toSet())
    }

    @Test
    fun `promotions occupy the tail of the vector`() {
        val firstPromotion = moves.indexOfFirst { it.length == 5 }
        assertEquals(1792, firstPromotion)
        assertEquals(1858, moves.size)
        // 22 from-to pairs times three pieces.
        assertEquals(66, moves.count { it.length == 5 })
    }
}
