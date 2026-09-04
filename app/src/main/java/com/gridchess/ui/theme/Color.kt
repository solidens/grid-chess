package com.gridchess.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bauhaus palette, neo-brutalist application.
 *
 * The rule the whole system leans on: exactly one primary per screen region,
 * everything else is Ink on Paper. Colour is a signal, never decoration.
 */
object Grid {
    /** Structure. Every border, every glyph outline, every hard shadow. */
    val Ink = Color(0xFF111111)
    val InkSoft = Color(0xFF4A4A4A)

    /** Ground. Warm off-white, not sterile #FFF. */
    val Paper = Color(0xFFF2F0E9)
    val PaperDeep = Color(0xFFE4E0D4)

    /** Bauhaus primaries. */
    val Red = Color(0xFFD62E1F)
    val Yellow = Color(0xFFF5C518)
    val Blue = Color(0xFF0F4CD1)

    /** Board. Kept low-contrast so the black/white glyphs stay readable. */
    val SquareLight = Color(0xFFF2F0E9)
    val SquareDark = Color(0xFFD9D3C3)

    /** Board state overlays. */
    val Selected = Yellow
    val LastMove = Color(0x33F5C518)
    val Check = Red
    val Hint = Blue
}

/** Level accents: 1 -> calm blue, 5 -> loud red. */
val LevelAccents = listOf(Grid.Blue, Grid.Blue, Grid.Yellow, Grid.Red, Grid.Red)

/**
 * Readable foreground for a filled accent. Yellow is far too light to carry
 * paper-coloured text; red and blue are dark enough that it is the only choice.
 */
fun onAccent(accent: Color): Color = if (accent == Grid.Yellow) Grid.Ink else Grid.Paper
