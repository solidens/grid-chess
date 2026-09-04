package com.gridchess.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val Sharp = RoundedCornerShape(0.dp)

/**
 * The one primitive the rest of the UI is built from: a flat slab with a hard
 * black border and an unblurred offset shadow.
 *
 * [pressedDepth] slides the slab toward its own shadow instead of lifting it,
 * which is what gives the press its physical, printed-object feel. The caller
 * animates it; the slab itself is stateless.
 */
@Composable
fun BrutalSlab(
    modifier: Modifier = Modifier,
    fill: Color = Grid.Paper,
    border: Color = Grid.Ink,
    borderWidth: Dp = GridTokens.Border,
    shadow: Dp = GridTokens.Shadow,
    pressedDepth: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        // Shadow plate: same silhouette, offset, no blur -- a printed drop, not a light source.
        if (shadow > 0.dp) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = shadow, y = shadow)
                    .background(Grid.Ink, Sharp),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .offset(x = pressedDepth, y = pressedDepth)
                .background(fill, Sharp)
                .border(BorderStroke(borderWidth, border), Sharp)
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Full-width action. Uppercase, tracked, pressed = pushed into its own shadow. */
@Composable
fun BrutalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Grid.Paper,
    contentColor: Color = Grid.Ink,
    enabled: Boolean = true,
    height: Dp = 60.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(
        targetValue = if (pressed && enabled) GridTokens.Shadow else 0.dp,
        animationSpec = spring(stiffness = 1400f),
        label = "press",
    )

    // Reserve the shadow's travel so neighbouring content never reflows.
    Box(modifier.height(height + GridTokens.Shadow)) {
        BrutalSlab(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            fill = if (enabled) fill else Grid.PaperDeep,
            border = if (enabled) Grid.Ink else Grid.InkSoft,
            pressedDepth = depth,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) contentColor else Grid.InkSoft,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** A rule used to separate sections. Thick enough to be a statement. */
@Composable
fun BrutalRule(modifier: Modifier = Modifier, thickness: Dp = GridTokens.Border) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(Grid.Ink),
    )
}

/** Small square colour chip -- the Bauhaus "keyed" element. */
@Composable
fun ColorChip(color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Box(
        modifier
            .size(size)
            .background(color, Sharp)
            .border(2.dp, Grid.Ink, Sharp),
    )
}

/** Uppercase tracked caption, the system's only secondary text treatment. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Grid.InkSoft) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}
