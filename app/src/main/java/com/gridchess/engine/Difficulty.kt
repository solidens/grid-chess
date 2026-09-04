package com.gridchess.engine

/**
 * The five levels are five different Maia networks, not one network throttled
 * five ways. Each was trained only on games by humans in that rating band, so
 * a level-2 blunder is the blunder a 1300 actually plays -- which is the whole
 * reason for choosing Maia over a depth-limited alpha-beta engine.
 *
 * [temperature] adds the last bit of variety: at low levels we sample from the
 * policy so the same opening does not repeat move for move; at the top level we
 * take the argmax and play the net's honest best move.
 *
 * [blunderGuard] runs a shallow material check over the sampled move and rejects
 * moves that hang a piece outright. Off at levels 1-3 (hanging pieces IS the
 * 1100-1500 experience), on at 4-5 where such a move reads as broken.
 */
enum class Difficulty(
    val level: Int,
    val title: String,
    val elo: Int,
    val asset: String,
    val temperature: Float,
    val blunderGuard: Boolean,
) {
    ONE(1, "Novice", 1100, "models/maia-1100.onnx", 0.85f, false),
    TWO(2, "Casual", 1300, "models/maia-1300.onnx", 0.70f, false),
    THREE(3, "Club", 1500, "models/maia-1500.onnx", 0.50f, false),
    FOUR(4, "Strong", 1700, "models/maia-1700.onnx", 0.30f, true),
    FIVE(5, "Expert", 1900, "models/maia-1900.onnx", 0.0f, true);

    companion object {
        fun ofLevel(level: Int): Difficulty = entries.first { it.level == level }
    }
}
