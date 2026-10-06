package com.gridchess.game

import android.app.Application
import android.content.Context
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
import com.gridchess.BuildConfig
import com.gridchess.online.ClientMsg
import com.gridchess.online.Color
import com.gridchess.online.ErrorCode
import com.gridchess.online.OnlineClient
import com.gridchess.online.Result
import com.gridchess.online.ServerMsg
import com.gridchess.online.SideChoice
import com.gridchess.online.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** The game's moves in UCI form, oldest first: what the server and this board are compared by. */
    private val played = mutableListOf<String>()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences("chess", Context.MODE_PRIVATE)

    /** The Gumroad key that opens rooms. Kept on the phone; only the server can tell if it is good. */
    private val _license = MutableStateFlow(prefs.getString(KEY_LICENSE, "").orEmpty())
    val license: StateFlow<String> = _license.asStateFlow()

    val onlineAvailable: Boolean get() = BuildConfig.ONLINE_URL.isNotEmpty()

    private var online: OnlineClient? = null
    private var onlineJob: Job? = null

    init {
        newGame(Difficulty.THREE, Side.WHITE)
    }

    fun newGame(difficulty: Difficulty, playerSide: Side) {
        closeOnline()
        resetBoard()
        _state.value = GameState(
            pieces = snapshotPieces(),
            sideToMove = board.sideToMove,
            playerSide = playerSide,
            difficulty = difficulty,
        )
        maybeEngineMove()
    }

    fun setLicense(key: String) {
        _license.value = key.trim()
        prefs.edit().putString(KEY_LICENSE, key.trim()).apply()
    }

    /** Opens a room for a friend to join; the server says which colour we got. */
    fun host(side: SideChoice) = startOnline(ClientMsg.Create(game = "chess", license = _license.value, side = side))

    fun join(code: String) = startOnline(ClientMsg.Join(code.trim().uppercase()))

    fun offerDraw() {
        if (_state.value.online?.status == Status.PLAYING) online?.send(ClientMsg.Draw)
    }

    fun resign() {
        if (_state.value.online?.status == Status.PLAYING) online?.send(ClientMsg.Resign)
    }

    fun rematch() {
        if (_state.value.online?.status == Status.OVER) online?.send(ClientMsg.Rematch)
    }

    /** Leaves the room for good. Mid-game the server counts it as resigning. */
    fun leaveOnline() = closeOnline()

    private fun startOnline(opening: ClientMsg) {
        closeOnline()
        resetBoard()
        _state.value = GameState(
            pieces = snapshotPieces(),
            sideToMove = board.sideToMove,
            online = OnlineInfo(),
        )
        val client = OnlineClient(BuildConfig.ONLINE_URL, viewModelScope, opening)
        online = client
        onlineJob = viewModelScope.launch {
            launch { client.link.collect { link -> editOnline { it.copy(link = link) } } }
            client.messages.collect(::onServer)
        }
        client.start()
    }

    private fun closeOnline() {
        onlineJob?.cancel()
        onlineJob = null
        online?.close(farewell = ClientMsg.Leave)
        online = null
    }

    private fun onServer(msg: ServerMsg) {
        when (msg) {
            is ServerMsg.Seat -> {
                msg.color?.let { _state.value = _state.value.copy(playerSide = it.side) }
                editOnline { it.copy(code = msg.code, seated = true) }
            }
            is ServerMsg.State -> sync(msg)
            is ServerMsg.Error -> {
                // A refused move needs nothing: the state right behind it puts the board back.
                if (msg.code == ErrorCode.ILLEGAL) return
                val seated = _state.value.online?.seated == true
                if (seated && msg.code != ErrorCode.NO_ROOM) return
                online?.close()
                online = null
                editOnline { it.copy(error = msg.message) }
            }
        }
    }

    /** Brings the board in line with the server's game, which is the one that counts. */
    private fun sync(msg: ServerMsg.State) {
        if (msg.moves != played) {
            val extends = msg.moves.size > played.size && msg.moves.subList(0, played.size) == played
            if (!extends) resetBoard()
            msg.moves.drop(played.size).forEach { uci -> parseUci(uci)?.let(::applyMove) }
            if (!extends) refreshBoard()
        }
        val over = msg.status == Status.OVER
        val s = _state.value
        _state.value = s.copy(
            playerSide = msg.color?.side ?: s.playerSide,
            selected = if (over) null else s.selected,
            targets = if (over) emptySet() else s.targets,
            outcome = when {
                !over -> Outcome.PLAYING
                msg.result == Result.WHITE -> Outcome.WHITE_WINS
                msg.result == Result.BLACK -> Outcome.BLACK_WINS
                else -> Outcome.DRAW
            },
            outcomeReason = msg.reason.orEmpty(),
            canUndo = false,
            online = (s.online ?: OnlineInfo()).copy(
                code = msg.code,
                seated = true,
                status = msg.status,
                opponentOnline = msg.opponentOnline,
                drawOffer = msg.drawOffer?.side,
                rematch = msg.rematch.mapTo(HashSet()) { it.side },
            ),
        )
    }

    private fun editOnline(edit: (OnlineInfo) -> OnlineInfo) {
        val s = _state.value
        s.online?.let { _state.value = s.copy(online = edit(it)) }
    }

    private fun resetBoard() {
        board = Board()
        positions.clear()
        repeated.clear()
        seen.clear()
        played.clear()
        recordPosition()
    }

    /** Folds the board into the UI state after it was rebuilt without [applyMove] seeing the end of it. */
    private fun refreshBoard() {
        _state.value = _state.value.copy(
            pieces = snapshotPieces(),
            sideToMove = board.sideToMove,
            selected = null,
            targets = emptySet(),
            lastMove = if (played.isEmpty()) null else _state.value.lastMove,
            checkSquare = checkSquare(),
        )
    }

    private fun parseUci(uci: String): Move? {
        if (uci.length !in 4..5) return null
        val from = Square.valueOf(uci.substring(0, 2).uppercase())
        val to = Square.valueOf(uci.substring(2, 4).uppercase())
        val promotion = when (uci.getOrNull(4)) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> null
        }?.let { Piece.make(board.sideToMove, it) } ?: Piece.NONE
        return Move(from, to, promotion)
    }

    private val Color.side: Side get() = if (this == Color.WHITE) Side.WHITE else Side.BLACK

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
        if (s.thinking || s.online != null) return
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
        val ply = played.size
        if (!applyMove(move)) {
            _state.value = _state.value.copy(selected = null, targets = emptySet())
            return
        }
        // Shown at once, then sent; if the server disagrees its state puts it back.
        online?.let {
            it.send(ClientMsg.Move(ply, played.last()))
            return
        }
        maybeEngineMove()
    }

    private fun maybeEngineMove() {
        val s = _state.value
        if (s.outcome != Outcome.PLAYING || s.sideToMove == s.playerSide || s.online != null) return

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
        played += uci(move)

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
        if (played.isNotEmpty()) played.removeAt(played.size - 1)
        val key = removed.zobristKey
        val count = (seen[key] ?: 1) - 1
        if (count <= 0) seen.remove(key) else seen[key] = count
    }

    private fun uci(move: Move): String {
        val promo = when (move.promotion.pieceType) {
            PieceType.QUEEN -> "q"
            PieceType.ROOK -> "r"
            PieceType.BISHOP -> "b"
            PieceType.KNIGHT -> "n"
            else -> ""
        }
        return move.from.value().lowercase() + move.to.value().lowercase() + promo
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
        closeOnline()
        engine.close()
    }

    private companion object {
        const val THINK_FLOOR_MS = 420L
        const val KEY_LICENSE = "license"
    }
}
