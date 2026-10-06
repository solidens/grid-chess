package com.gridchess.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.gridchess.game.GameViewModel
import com.gridchess.game.OnlineInfo
import com.gridchess.game.Outcome
import com.gridchess.online.Link
import com.gridchess.online.Status
import com.gridchess.ui.board.BoardRender
import com.gridchess.ui.board.ChessBoard
import com.gridchess.ui.board.drawPiece
import com.gridchess.ui.theme.BrutalButton
import com.gridchess.ui.theme.BrutalRule
import com.gridchess.ui.theme.BrutalSlab
import com.gridchess.ui.theme.Caption
import com.gridchess.ui.theme.Grid
import com.gridchess.ui.theme.GridTokens
import com.gridchess.ui.theme.LevelAccents
import com.gridchess.ui.theme.onAccent

@Composable
fun GameScreen(viewModel: GameViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online = state.online
    val accent = if (online != null) Grid.Blue else LevelAccents[state.difficulty.level - 1]
    val mySide = state.playerSide
    val theirSide = mySide.flip()

    // Leaving an online game is final, and mid-game it resigns it; both go through one question.
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val leave = {
        if (online != null) viewModel.leaveOnline()
        onExit()
    }
    val exit = {
        if (online?.status == Status.PLAYING && online.error == null) confirm = Confirm.LEAVE else leave()
    }
    // System back means the same as the cross. Without this it closes the app,
    // which online quietly resigns the game.
    BackHandler { if (confirm != null) confirm = null else exit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Grid.Paper),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(GridTokens.Page),
        ) {
            Header(
                title = if (online != null) "Online" else state.difficulty.title,
                caption = when {
                    online == null -> "Maia · ~${state.difficulty.elo} elo · on device"
                    online.code.isEmpty() -> "Opening the room"
                    else -> "Room ${online.code} · vs a friend"
                },
                accent = accent,
                onExit = exit,
            )

            Spacer(Modifier.height(GridTokens.GapWide))
            StatusBar(
                thinking = state.thinking,
                yourTurn = state.isPlayerTurn,
                outcome = state.outcome,
                reason = state.outcomeReason,
                playerSide = state.playerSide,
                accent = accent,
                online = online,
            )

            // The board is the screen. Equal weights above and below centre it in
            // whatever height is left over.
            Spacer(Modifier.weight(1f))
            ChessBoard(
                render = BoardRender(
                    pieces = state.pieces,
                    selected = state.selected,
                    targets = state.targets,
                    lastMoveFrom = state.lastMove?.from,
                    lastMoveTo = state.lastMove?.to,
                    checkSquare = state.checkSquare,
                    flipped = state.flipped,
                    movableSide = state.playerSide.takeIf { state.isPlayerTurn },
                ),
                onSquareTap = viewModel::onSquareTap,
                enabled = state.isPlayerTurn,
            )

            Spacer(Modifier.weight(1f))
            if (online != null) {
                val playing = online.status == Status.PLAYING
                Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
                    BrutalButton(
                        label = when (online.drawOffer) {
                            mySide -> "Offered"
                            theirSide -> "Accept draw"
                            else -> "Draw"
                        },
                        onClick = viewModel::offerDraw,
                        enabled = playing && online.drawOffer != mySide,
                        fill = if (online.drawOffer == theirSide) Grid.Yellow else Grid.Paper,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                    )
                    BrutalButton(
                        label = "Resign",
                        onClick = { confirm = Confirm.RESIGN },
                        enabled = playing,
                        fill = Grid.Ink,
                        contentColor = Grid.Paper,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
                BrutalButton(
                    label = "Undo",
                    onClick = viewModel::undo,
                    enabled = state.canUndo && !state.thinking,
                    height = 52.dp,
                    modifier = Modifier.weight(1f),
                )
                BrutalButton(
                    label = "New",
                    onClick = { viewModel.newGame(state.difficulty, state.playerSide) },
                    fill = Grid.Ink,
                    contentColor = Grid.Paper,
                    height = 52.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(GridTokens.GapWide))
        }

        state.promotion?.let {
            PromotionOverlay(
                side = state.playerSide,
                onPick = viewModel::completePromotion,
                onCancel = viewModel::cancelPromotion,
            )
        }

        if (online != null && online.error == null) {
            AnimatedVisibility(
                visible = online.status == Status.WAITING,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                WaitingOverlay(code = online.code, onCancel = leave)
            }
        }

        AnimatedVisibility(
            visible = state.outcome != Outcome.PLAYING && online?.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val (label, enabled) = when {
                online == null -> "Rematch" to true
                !online.opponentOnline -> "Opponent left" to false
                mySide in online.rematch -> "Asked" to false
                theirSide in online.rematch -> "Accept rematch" to true
                else -> "Rematch" to true
            }
            OutcomeOverlay(
                outcome = state.outcome,
                reason = state.outcomeReason,
                playerSide = state.playerSide,
                rematchLabel = label,
                rematchEnabled = enabled,
                onRematch = {
                    if (online != null) viewModel.rematch() else viewModel.newGame(state.difficulty, state.playerSide)
                },
                onExit = leave,
            )
        }

        online?.error?.let { ErrorOverlay(message = it, onExit = leave) }

        confirm?.let { asked ->
            ConfirmOverlay(
                question = if (asked == Confirm.RESIGN) "Resign?" else "Leave?",
                detail = if (asked == Confirm.RESIGN) "The game goes to your opponent" else "Leaving now resigns the game",
                action = if (asked == Confirm.RESIGN) "Resign" else "Leave",
                onAction = {
                    confirm = null
                    if (asked == Confirm.RESIGN) viewModel.resign() else leave()
                },
                onCancel = { confirm = null },
            )
        }
    }
}

private enum class Confirm { RESIGN, LEAVE }

private fun Side.flip(): Side = if (this == Side.WHITE) Side.BLACK else Side.WHITE

/** The room code, big enough to read across a table, until the friend is in. */
@Composable
private fun WaitingOverlay(code: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    Scrim {
        BrutalSlab(
            modifier = Modifier
                .padding(GridTokens.Page)
                .fillMaxWidth()
                .height(300.dp),
            fill = Grid.Yellow,
            contentPadding = PaddingValues(GridTokens.GapWide),
        ) {
            Column(Modifier.fillMaxSize()) {
                Caption(if (code.isEmpty()) "Opening a room" else "Room code", color = Grid.Ink)
                Text(
                    code.ifEmpty { "····" },
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace),
                    color = Grid.Ink,
                )
                Caption("Your friend enters it under Friend · Join", color = Grid.Ink)
                Spacer(Modifier.weight(1f))
                BrutalButton(
                    label = "Share code",
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Play me in Grid Chess. Room code: $code")
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    enabled = code.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                )
                Spacer(Modifier.height(GridTokens.Gap))
                BrutalButton(
                    label = "Cancel",
                    onClick = onCancel,
                    fill = Grid.Ink,
                    contentColor = Grid.Paper,
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onExit: () -> Unit) {
    Scrim {
        BrutalSlab(
            modifier = Modifier
                .padding(GridTokens.Page)
                .fillMaxWidth()
                .height(220.dp),
            fill = Grid.Red,
            contentPadding = PaddingValues(GridTokens.GapWide),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text("NO GAME", style = MaterialTheme.typography.displayLarge, color = Grid.Paper)
                Caption(message, color = Grid.Paper)
                Spacer(Modifier.weight(1f))
                BrutalButton(label = "Menu", onClick = onExit, modifier = Modifier.fillMaxWidth(), height = 52.dp)
            }
        }
    }
}

@Composable
private fun ConfirmOverlay(
    question: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    onCancel: () -> Unit,
) {
    Scrim(onTap = onCancel) {
        BrutalSlab(
            modifier = Modifier
                .padding(GridTokens.Page)
                .fillMaxWidth()
                .height(240.dp),
            fill = Grid.Paper,
            contentPadding = PaddingValues(GridTokens.GapWide),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(question.uppercase(), style = MaterialTheme.typography.displayLarge, color = Grid.Ink)
                Caption(detail)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
                    BrutalButton(
                        label = "Play on",
                        onClick = onCancel,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                    )
                    BrutalButton(
                        label = action,
                        onClick = onAction,
                        fill = Grid.Red,
                        contentColor = Grid.Paper,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The darkened ground every overlay sits on. A tap on it does [onTap], or nothing. */
@Composable
private fun Scrim(onTap: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Grid.Ink.copy(alpha = 0.82f))
            .clickable(enabled = true, onClick = { onTap?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun Header(title: String, caption: String, accent: Color, onExit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = Grid.Ink,
            )
            Caption(caption)
        }
        Box(
            Modifier
                .size(44.dp)
                .background(accent)
                .clickable(onClick = onExit),
        ) {
            Text(
                "×",
                style = MaterialTheme.typography.displayMedium,
                color = onAccent(accent),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
    Spacer(Modifier.height(GridTokens.Gap))
    BrutalRule()
}

@Composable
private fun StatusBar(
    thinking: Boolean,
    yourTurn: Boolean,
    outcome: Outcome,
    reason: String,
    playerSide: Side,
    accent: Color,
    online: OnlineInfo?,
) {
    // A slow pulse is the only "loading" affordance in the app -- no spinners.
    val pulse = rememberInfiniteTransition(label = "think")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "alpha",
    )

    val opponent = if (playerSide == Side.WHITE) Side.BLACK else Side.WHITE
    val label = when {
        outcome != Outcome.PLAYING -> reason
        online != null && online.link == Link.CONNECTING -> "Connecting"
        online != null && online.link == Link.OFFLINE -> "Reconnecting"
        online?.status == Status.WAITING -> "Waiting for friend"
        online != null && !online.opponentOnline -> "Opponent away"
        online?.drawOffer == opponent -> "Draw offered"
        thinking -> "Thinking"
        yourTurn -> "Your move"
        online != null -> "Their move"
        else -> "Waiting"
    }
    // Online, anything that is not your move is somebody else's work in progress.
    val busy = thinking || (online != null && outcome == Outcome.PLAYING && !yourTurn)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .alpha(if (busy) alpha else 1f)
                .background(if (busy) accent else Grid.Ink),
        )
        Spacer(Modifier.width(GridTokens.Gap))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = Grid.Ink,
        )
        Spacer(Modifier.weight(1f))
        Caption(if (playerSide == Side.WHITE) "You: white" else "You: black")
    }
}

@Composable
private fun PromotionOverlay(
    side: Side,
    onPick: (PieceType) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Grid.Ink.copy(alpha = 0.75f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        BrutalSlab(
            modifier = Modifier
                .padding(GridTokens.Page)
                .fillMaxWidth()
                .height(150.dp),
            fill = Grid.Paper,
            contentPadding = PaddingValues(GridTokens.GapWide),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Caption("Promote to")
                Spacer(Modifier.height(GridTokens.Gap))
                Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.Gap)) {
                    // The real glyphs, not letters. The rest of the app refuses to
                    // name pieces in text; this screen has no business doing it.
                    listOf(
                        PieceType.QUEEN, PieceType.ROOK,
                        PieceType.BISHOP, PieceType.KNIGHT,
                    ).forEach { type ->
                        Box(Modifier.weight(1f).height(72.dp)) {
                            BrutalSlab(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onPick(type) },
                                shadow = GridTokens.ShadowSmall,
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawPiece(
                                        Piece.make(side, type),
                                        Rect(Offset.Zero, size),
                                        Grid.Ink,
                                        Grid.Paper,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeOverlay(
    outcome: Outcome,
    reason: String,
    playerSide: Side,
    rematchLabel: String,
    rematchEnabled: Boolean,
    onRematch: () -> Unit,
    onExit: () -> Unit,
) {
    val playerWon = (outcome == Outcome.WHITE_WINS && playerSide == Side.WHITE) ||
        (outcome == Outcome.BLACK_WINS && playerSide == Side.BLACK)
    val headline = when {
        outcome == Outcome.DRAW -> "Draw"
        playerWon -> "You win"
        else -> "You lose"
    }
    val accent = when {
        outcome == Outcome.DRAW -> Grid.Yellow
        playerWon -> Grid.Blue
        else -> Grid.Red
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Grid.Ink.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        BrutalSlab(
            modifier = Modifier
                .padding(GridTokens.Page)
                .fillMaxWidth()
                .height(260.dp),
            fill = accent,
            contentPadding = PaddingValues(GridTokens.GapWide),
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    headline.uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Grid.Paper,
                )
                Caption(reason, color = Grid.Paper)
                Spacer(Modifier.weight(1f))
                BrutalButton(
                    label = rematchLabel,
                    onClick = onRematch,
                    enabled = rematchEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                )
                Spacer(Modifier.height(GridTokens.Gap))
                BrutalButton(
                    label = "Menu",
                    onClick = onExit,
                    fill = Grid.Ink,
                    contentColor = Grid.Paper,
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                )
            }
        }
    }
}
