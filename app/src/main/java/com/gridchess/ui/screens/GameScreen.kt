package com.gridchess.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.gridchess.game.GameViewModel
import com.gridchess.game.Outcome
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
    val accent = LevelAccents[state.difficulty.level - 1]

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
                title = state.difficulty.title,
                elo = state.difficulty.elo,
                accent = accent,
                onExit = onExit,
            )

            Spacer(Modifier.height(GridTokens.GapWide))
            StatusBar(
                thinking = state.thinking,
                yourTurn = state.isPlayerTurn,
                outcome = state.outcome,
                reason = state.outcomeReason,
                playerSide = state.playerSide,
                accent = accent,
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
            Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
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

        AnimatedVisibility(
            visible = state.outcome != Outcome.PLAYING,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            OutcomeOverlay(
                outcome = state.outcome,
                reason = state.outcomeReason,
                playerSide = state.playerSide,
                onRematch = { viewModel.newGame(state.difficulty, state.playerSide) },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun Header(title: String, elo: Int, accent: androidx.compose.ui.graphics.Color, onExit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = Grid.Ink,
            )
            Caption("Maia · ~$elo elo · on device")
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
    accent: androidx.compose.ui.graphics.Color,
) {
    // A slow pulse is the only "loading" affordance in the app -- no spinners.
    val pulse = rememberInfiniteTransition(label = "think")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "alpha",
    )

    val label = when {
        outcome != Outcome.PLAYING -> reason
        thinking -> "Thinking"
        yourTurn -> "Your move"
        else -> "Waiting"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .alpha(if (thinking) alpha else 1f)
                .background(if (thinking) accent else Grid.Ink),
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
                    label = "Rematch",
                    onClick = onRematch,
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
