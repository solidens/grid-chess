package com.gridchess.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Side
import com.gridchess.engine.Difficulty
import com.gridchess.ui.theme.BrutalButton
import com.gridchess.ui.theme.BrutalRule
import com.gridchess.ui.theme.BrutalSlab
import com.gridchess.ui.theme.Caption
import com.gridchess.ui.theme.ColorChip
import com.gridchess.ui.theme.Grid
import com.gridchess.ui.theme.GridTokens
import com.gridchess.ui.theme.LevelAccents
import com.gridchess.ui.theme.onAccent

@Composable
fun HomeScreen(onStart: (Difficulty, Side) -> Unit) {
    var difficulty by remember { mutableStateOf(Difficulty.THREE) }
    var side by remember { mutableStateOf(Side.WHITE) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Grid.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(GridTokens.Page)
            .padding(top = GridTokens.GapWide),
    ) {
        Text(
            "GRID",
            style = MaterialTheme.typography.displayLarge,
            color = Grid.Ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CHESS",
                style = MaterialTheme.typography.displayLarge,
                color = Grid.Red,
            )
            Spacer(Modifier.width(GridTokens.GapWide))
            ColorChip(Grid.Yellow, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            ColorChip(Grid.Blue, size = 20.dp)
        }

        Spacer(Modifier.height(GridTokens.Gap))
        BrutalRule()
        Spacer(Modifier.height(GridTokens.Gap))
        Caption("Local neural opponent · no network required")

        Spacer(Modifier.height(GridTokens.GapSection))
        Caption("Opponent")
        Spacer(Modifier.height(GridTokens.Gap))

        Difficulty.entries.forEach { level ->
            LevelRow(
                level = level,
                selected = level == difficulty,
                onSelect = { difficulty = level },
            )
            Spacer(Modifier.height(GridTokens.Gap + GridTokens.ShadowSmall))
        }

        Spacer(Modifier.height(GridTokens.GapWide))
        Caption("You play")
        Spacer(Modifier.height(GridTokens.Gap))
        Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
            SideChoice("White", side == Side.WHITE, Modifier.weight(1f)) { side = Side.WHITE }
            SideChoice("Black", side == Side.BLACK, Modifier.weight(1f)) { side = Side.BLACK }
        }

        Spacer(Modifier.height(GridTokens.GapSection))
        BrutalButton(
            label = "Start",
            onClick = { onStart(difficulty, side) },
            fill = Grid.Yellow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(GridTokens.GapSection))
    }
}

@Composable
private fun LevelRow(
    level: Difficulty,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val accent = LevelAccents[level.level - 1]
    val content = if (selected) onAccent(accent) else Grid.Ink
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        BrutalSlab(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onSelect),
            fill = if (selected) accent else Grid.Paper,
            shadow = GridTokens.ShadowSmall,
            pressedDepth = if (selected) GridTokens.ShadowSmall else 0.dp,
            contentPadding = PaddingValues(horizontal = GridTokens.GapWide),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = level.level.toString(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = content,
                )
                Spacer(Modifier.width(GridTokens.GapWide))
                Column {
                    Text(
                        text = level.title.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = content,
                    )
                    Caption(
                        "~${level.elo} elo",
                        color = if (selected) content.copy(alpha = 0.7f) else Grid.InkSoft,
                    )
                }
                Spacer(Modifier.weight(1f))
                // A five-bar meter: the same information as the number, read at a
                // glance. Bottom-aligned, because a meter grows off a baseline.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(28.dp),
                ) {
                    repeat(5) { i ->
                        val on = i < level.level
                        Box(
                            Modifier
                                .width(5.dp)
                                .height(if (on) 28.dp else 9.dp)
                                .background(
                                    when {
                                        selected && on -> content
                                        selected -> content.copy(alpha = 0.35f)
                                        on -> Grid.Ink
                                        else -> Grid.PaperDeep
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    Box(modifier.height(56.dp + GridTokens.ShadowSmall)) {
        BrutalSlab(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onSelect),
            fill = if (selected) Grid.Ink else Grid.Paper,
            shadow = GridTokens.ShadowSmall,
            pressedDepth = if (selected) GridTokens.ShadowSmall else 0.dp,
        ) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Bordered, so the white swatch still reads on a paper ground.
                ColorChip(
                    color = if (label == "White") Grid.Paper else Grid.Ink,
                    size = 16.dp,
                )
                Spacer(Modifier.width(GridTokens.Gap))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) Grid.Paper else Grid.Ink,
                )
            }
        }
    }
}
