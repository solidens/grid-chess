package com.gridchess.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType

/**
 * Pieces reduced to the Bauhaus primitives: circle, square, triangle.
 *
 * Silhouette alone has to carry identity -- there is no colour cue and no
 * lettering -- so the six shapes are deliberately maximally distinct, and
 * scale ranks them: king and queen read biggest, pawn smallest.
 */
private val Scale = mapOf(
    PieceType.KING to 0.72f,
    PieceType.QUEEN to 0.68f,
    PieceType.ROOK to 0.56f,
    PieceType.BISHOP to 0.62f,
    PieceType.KNIGHT to 0.62f,
    PieceType.PAWN to 0.42f,
)

fun DrawScope.drawPiece(
    piece: Piece,
    cell: Rect,
    ink: Color,
    paper: Color,
    strokePx: Float,
) {
    val type = piece.pieceType ?: return
    val isWhite = piece.pieceSide == com.github.bhlangonijr.chesslib.Side.WHITE

    // White = paper body with an ink outline; black = solid ink. Both keep the
    // same outline weight so the two sides sit at equal visual weight.
    val body = if (isWhite) paper else ink
    val outline = ink

    val extent = minOf(cell.width, cell.height) * (Scale[type] ?: 0.5f)
    val box = Rect(
        center = cell.center,
        radius = extent / 2f,
    )

    val path = when (type) {
        PieceType.PAWN -> circlePath(box)
        PieceType.KNIGHT -> trianglePath(box)
        PieceType.BISHOP -> diamondPath(box)
        PieceType.ROOK -> squarePath(box)
        PieceType.QUEEN -> archPath(box)
        PieceType.KING -> crossPath(box)
        else -> return
    }

    drawPath(path, color = body, style = Fill)
    drawPath(path, color = outline, style = Stroke(width = strokePx))
}

private fun circlePath(b: Rect) = Path().apply { addOval(b) }

private fun squarePath(b: Rect) = Path().apply { addRect(b) }

/** Equilateral-ish triangle, apex up. */
private fun trianglePath(b: Rect) = Path().apply {
    moveTo(b.center.x, b.top)
    lineTo(b.right, b.bottom)
    lineTo(b.left, b.bottom)
    close()
}

/** Square rotated 45 degrees. */
private fun diamondPath(b: Rect) = Path().apply {
    moveTo(b.center.x, b.top)
    lineTo(b.right, b.center.y)
    lineTo(b.center.x, b.bottom)
    lineTo(b.left, b.center.y)
    close()
}

/** Semicircle sitting on a rectangle -- the Bauhaus arch. */
private fun archPath(b: Rect) = Path().apply {
    val r = b.width / 2f
    val shoulder = b.top + r
    moveTo(b.left, b.bottom)
    lineTo(b.left, shoulder)
    arcTo(
        rect = Rect(Offset(b.left, b.top), Size(b.width, b.width)),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    lineTo(b.right, b.bottom)
    close()
}

/** Greek cross. */
private fun crossPath(b: Rect) = Path().apply {
    val arm = b.width / 3f
    val x0 = b.left
    val x1 = b.left + arm
    val x2 = b.right - arm
    val x3 = b.right
    val y0 = b.top
    val y1 = b.top + arm
    val y2 = b.bottom - arm
    val y3 = b.bottom
    moveTo(x1, y0)
    lineTo(x2, y0)
    lineTo(x2, y1)
    lineTo(x3, y1)
    lineTo(x3, y2)
    lineTo(x2, y2)
    lineTo(x2, y3)
    lineTo(x1, y3)
    lineTo(x1, y2)
    lineTo(x0, y2)
    lineTo(x0, y1)
    lineTo(x1, y1)
    close()
}
