package com.gridchess.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.random.Random

/**
 * Runs a Maia network locally through ONNX Runtime and turns its policy head
 * into a move.
 *
 * There is no tree search: one forward pass per move, which is the point --
 * Maia's whole value is the move a human of that rating would play, and search
 * would sand exactly that off. A pass is a couple of milliseconds even on a
 * low-end phone, so the visible "thinking" pause in the UI is deliberate.
 */
class MaiaEngine(
    private val context: Context,
    private val random: Random = Random.Default,
) : Closeable {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val policyIndex: PolicyIndex by lazy { PolicyIndex.load(context.assets) }

    private var loaded: Difficulty? = null
    private var session: OrtSession? = null
    private var inputName: String = "/input/planes"
    private var policyOutput: String? = null

    /**
     * Picks a move for the side to move in [positions].last().
     *
     * @param positions oldest first; the last entry is the position to move in.
     * @param repeated per-position "this had already occurred" flags.
     * @return the chosen move, or null when the position is already over.
     */
    fun chooseMove(
        positions: List<Board>,
        repeated: List<Boolean>,
        difficulty: Difficulty,
    ): Move? {
        val board = positions.last()
        val legal = MoveGenerator.generateLegalMoves(board)
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal.first()

        val policy = runNetwork(positions, repeated, difficulty)
            ?: return legal[random.nextInt(legal.size)]   // net unavailable: stay playable

        val side = board.sideToMove
        val logits = FloatArray(legal.size) { i ->
            val slot = policyIndex.slotFor(legal[i], side)
            if (slot >= 0) policy[slot] else Float.NEGATIVE_INFINITY
        }
        if (logits.all { it == Float.NEGATIVE_INFINITY }) {
            // Should not happen; means the policy mapping missed every move.
            Log.w(TAG, "no legal move mapped into the policy vector")
            return legal[random.nextInt(legal.size)]
        }

        val ranked = legal.indices
            .filter { logits[it] > Float.NEGATIVE_INFINITY }
            .sortedByDescending { logits[it] }

        val picked = if (difficulty.temperature <= 0f) {
            ranked.first()
        } else {
            sample(ranked, logits, difficulty.temperature)
        }

        val choice = if (difficulty.blunderGuard) {
            guard(board, legal, ranked, picked)
        } else {
            picked
        }
        return legal[choice]
    }

    private fun runNetwork(
        positions: List<Board>,
        repeated: List<Boolean>,
        difficulty: Difficulty,
    ): FloatArray? {
        val active = ensureSession(difficulty) ?: return null
        val planes = LeelaEncoder.encode(positions, repeated)
        val started = android.os.SystemClock.elapsedRealtime()
        return try {
            OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(planes),
                longArrayOf(1, LeelaEncoder.PLANES.toLong(), 8, 8),
            ).use { tensor ->
                active.run(mapOf(inputName to tensor)).use { result ->
                    val name = policyOutput ?: return null
                    @Suppress("UNCHECKED_CAST")
                    val raw = result.get(name).orElse(null)?.value as? Array<FloatArray>
                    Log.d(TAG, "forward pass in ${android.os.SystemClock.elapsedRealtime() - started} ms")
                    raw?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "inference failed for ${difficulty.asset}", e)
            null
        }
    }

    private fun ensureSession(difficulty: Difficulty): OrtSession? {
        if (loaded == difficulty) return session
        close()
        return try {
            val bytes = context.assets.open(difficulty.asset).use { it.readBytes() }
            val options = OrtSession.SessionOptions().apply {
                // Two threads is the sweet spot: the nets are small enough that
                // more just adds scheduling overhead on little cores.
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val created = env.createSession(bytes, options)
            inputName = created.inputNames.first()
            // lc0 exports name the heads "/output/policy" and "/output/value",
            // but the exact prefix has changed between versions -- find it.
            policyOutput = created.outputNames.firstOrNull { it.contains("policy", true) }
                ?: created.outputNames.firstOrNull()
            session = created
            loaded = difficulty
            created
        } catch (e: Exception) {
            Log.e(TAG, "could not load ${difficulty.asset}", e)
            loaded = null
            session = null
            null
        }
    }

    /** Softmax over the legal moves at [temperature], then draw one. */
    private fun sample(ranked: List<Int>, logits: FloatArray, temperature: Float): Int {
        val top = ranked.take(TOP_K)
        val max = logits[top.first()]
        val weights = DoubleArray(top.size) { exp(((logits[top[it]] - max) / temperature).toDouble()) }
        val total = weights.sum()
        if (total <= 0.0 || total.isNaN()) return ranked.first()
        var r = random.nextDouble() * total
        for (i in top.indices) {
            r -= weights[i]
            if (r <= 0.0) return top[i]
        }
        return top.last()
    }

    /**
     * At levels 4-5 a hung queen reads as a broken engine rather than a human
     * mistake, so the top candidates get a shallow material check and the
     * sampled move is dropped if a near-equally-likely one is much sounder.
     */
    private fun guard(board: Board, legal: List<Move>, ranked: List<Int>, picked: Int): Int {
        val candidates = (listOf(picked) + ranked.take(TOP_K)).distinct()
        val scores = candidates.associateWith { Material.evaluateMove(board, legal[it]) }
        val best = candidates.maxByOrNull { scores.getValue(it) } ?: picked
        // Only override when the sampled move genuinely drops material.
        return if (scores.getValue(best) - scores.getValue(picked) >= BLUNDER_THRESHOLD) best else picked
    }

    override fun close() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "session close failed", e)
        }
        session = null
        loaded = null
    }

    private companion object {
        const val TAG = "MaiaEngine"
        const val TOP_K = 5
        /** Three pawns: a minor piece hung outright. */
        const val BLUNDER_THRESHOLD = 300
    }
}
