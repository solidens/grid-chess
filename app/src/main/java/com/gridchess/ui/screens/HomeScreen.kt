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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Side
import com.gridchess.BuildConfig
import com.gridchess.engine.Difficulty
import com.gridchess.online.SideChoice
import com.gridchess.ui.theme.BrutalButton
import com.gridchess.ui.theme.BrutalField
import com.gridchess.ui.theme.BrutalRule
import com.gridchess.ui.theme.BrutalSlab
import com.gridchess.ui.theme.Caption
import com.gridchess.ui.theme.ColorChip
import com.gridchess.ui.theme.Grid
import com.gridchess.ui.theme.GridTokens
import com.gridchess.ui.theme.LevelAccents
import com.gridchess.ui.theme.onAccent

@Composable
fun HomeScreen(
    onlineAvailable: Boolean,
    license: String,
    onLicense: (String) -> Unit,
    onStart: (Difficulty, Side) -> Unit,
    onHost: (SideChoice) -> Unit,
    onJoin: (String) -> Unit,
    vsFriend: Boolean,
    onVsFriend: (Boolean) -> Unit,
) {
    var difficulty by remember { mutableStateOf(Difficulty.THREE) }
    var side by remember { mutableStateOf(Side.WHITE) }
    var hostSide by remember { mutableStateOf(SideChoice.RANDOM) }
    var code by remember { mutableStateOf("") }
    val friend = vsFriend && onlineAvailable

    // Start is pinned to the bottom edge; only the choices above it scroll. On a
    // tall phone nothing scrolls at all, on a small one the button never hides.
    Column(
        Modifier
            .fillMaxSize()
            .background(Grid.Paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GridTokens.Page)
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
            Caption(
                if (friend) "A friend, by room code" else "Local neural opponent · no network required",
            )

            if (onlineAvailable) {
                Spacer(Modifier.height(GridTokens.GapSection))
                Caption("Play against")
                Spacer(Modifier.height(GridTokens.Gap))
                Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
                    Choice("Maia", !vsFriend, Modifier.weight(1f)) { onVsFriend(false) }
                    Choice("Friend", vsFriend, Modifier.weight(1f)) { onVsFriend(true) }
                }
            }

            if (friend) {
                OnlineChoices(
                    license = license,
                    onLicense = onLicense,
                    side = hostSide,
                    onSide = { hostSide = it },
                    code = code,
                    onCode = { code = it },
                    onJoin = { onJoin(code) },
                )
            } else {
                Spacer(Modifier.height(GridTokens.GapSection))
                Caption("Opponent")
                Spacer(Modifier.height(GridTokens.Gap))

                Difficulty.entries.forEach { level ->
                    LevelRow(
                        level = level,
                        selected = level == difficulty,
                        onSelect = { difficulty = level },
                    )
                    Spacer(Modifier.height(GridTokens.Gap))
                }

                Spacer(Modifier.height(GridTokens.Gap))
                Caption("You play")
                Spacer(Modifier.height(GridTokens.Gap))
                Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide)) {
                    SidePick("White", side == Side.WHITE, Modifier.weight(1f)) { side = Side.WHITE }
                    SidePick("Black", side == Side.BLACK, Modifier.weight(1f)) { side = Side.BLACK }
                }
            }
            Spacer(Modifier.height(GridTokens.GapWide))
        }

        BrutalRule()
        BrutalButton(
            label = if (friend) "Open room" else "Start",
            onClick = { if (friend) onHost(hostSide) else onStart(difficulty, side) },
            enabled = !friend || license.isNotBlank(),
            fill = Grid.Yellow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GridTokens.Page)
                .padding(top = GridTokens.GapWide, bottom = GridTokens.Gap),
        )
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
            .height(ROW_HEIGHT + GridTokens.ShadowSmall),
    ) {
        BrutalSlab(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clickable(onClick = onSelect),
            fill = if (selected) accent else Grid.Paper,
            shadow = GridTokens.ShadowSmall,
            pressedDepth = if (selected) GridTokens.ShadowSmall else 0.dp,
            contentPadding = PaddingValues(horizontal = GridTokens.GapWide),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
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
private fun OnlineChoices(
    license: String,
    onLicense: (String) -> Unit,
    side: SideChoice,
    onSide: (SideChoice) -> Unit,
    code: String,
    onCode: (String) -> Unit,
    onJoin: () -> Unit,
) {
    val uri = LocalUriHandler.current

    Spacer(Modifier.height(GridTokens.GapSection))
    Caption("Join a friend's room")
    Spacer(Modifier.height(GridTokens.Gap))
    Row(
        horizontalArrangement = Arrangement.spacedBy(GridTokens.GapWide),
        verticalAlignment = Alignment.Top,
    ) {
        BrutalField(
            value = code,
            // Codes are four letters; I and O are never used, so a typed 1 or 0 is not one either.
            onValueChange = { typed -> onCode(typed.uppercase().filter { it in 'A'..'Z' }.take(4)) },
            placeholder = "Code",
            textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            modifier = Modifier.weight(1f),
        )
        BrutalButton(
            label = "Join",
            onClick = onJoin,
            enabled = code.length == 4,
            fill = Grid.Blue,
            contentColor = Grid.Paper,
            height = 56.dp,
            modifier = Modifier.width(120.dp),
        )
    }
    Caption("Joining is free")

    Spacer(Modifier.height(GridTokens.GapSection))
    Caption("Or open your own")
    Spacer(Modifier.height(GridTokens.Gap))
    BrutalField(
        value = license,
        onValueChange = onLicense,
        placeholder = "Licence key",
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    if (BuildConfig.GUMROAD_URL.isNotEmpty()) {
        Caption(
            "Get a key →",
            color = Grid.Blue,
            modifier = Modifier
                .clickable { uri.openUri(BuildConfig.GUMROAD_URL) }
                .padding(vertical = GridTokens.Gap),
        )
    } else {
        Caption("Opening a room takes a licence key")
    }

    Spacer(Modifier.height(GridTokens.GapWide))
    Caption("You play")
    Spacer(Modifier.height(GridTokens.Gap))
    Row(horizontalArrangement = Arrangement.spacedBy(GridTokens.Gap)) {
        SidePick("White", side == SideChoice.WHITE, Modifier.weight(1f)) { onSide(SideChoice.WHITE) }
        SidePick("Black", side == SideChoice.BLACK, Modifier.weight(1f)) { onSide(SideChoice.BLACK) }
        Choice("Any", side == SideChoice.RANDOM, Modifier.weight(1f)) { onSide(SideChoice.RANDOM) }
    }
}

/** A plain two- or three-way choice: the same slab as the side picker, without the colour chip. */
@Composable
private fun Choice(
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
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) Grid.Paper else Grid.Ink,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun SidePick(
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

private val ROW_HEIGHT = 60.dp
