package com.gridchess.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.gridchess.engine.Difficulty
import com.gridchess.engine.MaiaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = MaiaEngine(app.applicationContext)

    private var board = Board()

    /**
     * Every position of the game, oldest first. Maia reads eight plies of
     * history, so this is engine input, not just an undo stack.
     */
    private val positions = mutableListOf<Board>()
    private val repeated = mutableListOf<Boolean>()
    private val seen = HashMap<Long, Int>()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    init {
        newGame(Difficulty.THREE, Side.WHITE)
    }

    fun newGame(difficulty: Difficulty, playerSide: Side) {
        board = Board()
        positions.clear()
        repeated.clear()
        seen.clear()
        recordPosition()

        _state.value = GameState(
            pieces = snapshotPieces(),
            sideToMove = board.sideToMove,
            playerSide = playerSide,
            difficulty = difficulty,
        )
        maybeEngineMove()
    }

    fun onSquareTap(square: Square) {
        val s = _state.value
        if (!s.isPlayerTurn || s.promotion != null) return

        val piece = board.getPiece(square)

        // Tapping one of our own pieces always re-selects rather than moving.
        if (piece != Piece.NONE && piece.pieceSide == s.playerSide) {
            _state.value = s.copy(selected = square, targets = targetsFrom(square))
            return
        }

        val from = s.selected ?: return
        if (square !in s.targets) {
            _state.value = s.copy(selected = null, targets = emptySet())
            return
        }

        if (isPromotion(from, square)) {
            _state.value = s.copy(
                promotion = PendingPromotion(from, square),
                selected = null,
                targets = emptySet(),
            )
            return
        }

        playPlayerMove(Move(from, square))
    }

    fun completePromotion(type: PieceType) {
        val pending = _state.value.promotion ?: return
        val piece = Piece.make(_state.value.playerSide, type)
        _state.value = _state.value.copy(promotion = null)
        playPlayerMove(Move(pending.from, pending.to, piece))
    }

    fun cancelPromotion() {
        _state.value = _state.value.copy(promotion = null)
    }

    /** Steps back over the engine's reply and the player's move together. */
    fun undo() {
        val s = _state.value
        if (s.thinking) return
        var undone = 0
        while (undone < 2 && positions.size > 1) {
            board.undoMove()
            dropPosition()
            undone++
            if (board.sideToMove == s.playerSide) break
        }
        if (undone == 0) return
        _state.value = s.copy(
            pieces = snapshotPieces(),
            sideToMove = board.sideToMove,
            selected = null,
            targets = emptySet(),
            lastMove = null,
            checkSquare = checkSquare(),
            outcome = Outcome.PLAYING,
            outcomeReason = "",
            canUndo = positions.size > 1,
        )
        // Undoing the engine's opening move (when the player is Black) leaves it
        // on move again with nothing to trigger it, so re-arm here.
        maybeEngineMove()
    }

    fun setDifficulty(difficulty: Difficulty) {
        _state.value = _state.value.copy(difficulty = difficulty)
    }

    private fun playPlayerMove(move: Move) {
        if (!applyMove(move)) {
            _state.value = _state.value.copy(selected = null, targets = emptySet())
            return
        }
        maybeEngineMove()
    }

    private fun maybeEngineMove() {
        val s = _state.value
        if (s.outcome != Outcome.PLAYING || s.sideToMove == s.playerSide) return

        _state.value = s.copy(thinking = true)
        viewModelScope.launch {
            val difficulty = _state.value.difficulty
            val snapshot = positions.map { it.clone() }
            val flags = repeated.toList()

            val started = System.currentTimeMillis()
            val move = withContext(Dispatchers.Default) {
                engine.chooseMove(snapshot, flags, difficulty)
            }
            // A single forward pass returns almost instantly; a short floor keeps
            // the reply from landing before the player has seen their own move.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < THINK_FLOOR_MS) delay(THINK_FLOOR_MS - elapsed)

            if (move == null) {
                _state.value = _state.value.copy(thinking = false)
                return@launch
            }
            applyMove(move)
            _state.value = _state.value.copy(thinking = false)
        }
    }

    /** Applies [move] to the board and folds the result into the UI state. */
    private fun applyMove(move: Move): Boolean {
        val legal = MoveGenerator.generateLegalMoves(board).any {
            it.from == move.from && it.to == move.to && it.promotion == move.promotion
        }
        if (!legal || !board.doMove(move)) return false

        recordPosition()

        val outcome = readOutcome()
        _state.value = _state.value.copy(
            pieces = snapshotPieces(),
            sideToMove = board.sideToMove,
            selected = null,
            targets = emptySet(),
            lastMove = move,
            checkSquare = checkSquare(),
            outcome = outcome.first,
            outcomeReason = outcome.second,
            canUndo = positions.size > 1,
        )
        return true
    }

    private fun recordPosition() {
        val key = board.zobristKey
        val count = (seen[key] ?: 0) + 1
        seen[key] = count
        positions.add(board.clone())
        repeated.add(count > 1)
    }

    private fun dropPosition() {
        if (positions.isEmpty()) return
        val removed = positions.removeAt(positions.size - 1)
        repeated.removeAt(repeated.size - 1)
        val key = removed.zobristKey
        val count = (seen[key] ?: 1) - 1
        if (count <= 0) seen.remove(key) else seen[key] = count
    }

    private fun snapshotPieces(): List<Piece> =
        (0 until 64).map { board.getPiece(Square.squareAt(it)) }

    private fun targetsFrom(square: Square): Set<Square> =
        MoveGenerator.generateLegalMoves(board)
            .filter { it.from == square }
            .map { it.to }
            .toSet()

    private fun isPromotion(from: Square, to: Square): Boolean {
        val piece = board.getPiece(from)
        if (piece.pieceType != PieceType.PAWN) return false
        val rank = to.rank.ordinal
        return (piece.pieceSide == Side.WHITE && rank == 7) ||
            (piece.pieceSide == Side.BLACK && rank == 0)
    }

    private fun checkSquare(): Square? =
        if (board.isKingAttacked) board.getKingSquare(board.sideToMove) else null

    private fun readOutcome(): Pair<Outcome, String> = when {
        board.isMated -> {
            val winner = if (board.sideToMove == Side.WHITE) Outcome.BLACK_WINS else Outcome.WHITE_WINS
            winner to "Checkmate"
        }
        board.isStaleMate -> Outcome.DRAW to "Stalemate"
        board.isInsufficientMaterial -> Outcome.DRAW to "Insufficient material"
        board.isRepetition -> Outcome.DRAW to "Threefold repetition"
        board.halfMoveCounter >= 100 -> Outcome.DRAW to "Fifty-move rule"
        else -> Outcome.PLAYING to ""
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
    }

    private companion object {
        const val THINK_FLOOR_MS = 420L
    }
}
