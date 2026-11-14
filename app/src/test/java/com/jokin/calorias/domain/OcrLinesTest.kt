package com.jokin.calorias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OcrLinesTest {
    /** A line of [width] x 30 px whose left edge starts at (x, y), turned [angle] degrees clockwise about the origin. */
    private fun line(text: String, x: Float, y: Float, width: Float, angle: Float): OcrLine {
        val rad = Math.toRadians(angle.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val corners = listOf(x to y, x + width to y, x + width to y + 30, x to y + 30).map { (px, py) -> OcrPoint(px * c - py * s, px * s + py * c) }
        return OcrLine(text, corners, angle)
    }

    @Test
    fun tiltedRowsComeBackStraight() {
        val tilted = (0 until 6).flatMap { r ->
            listOf(line("Nutriente $r", 0f, r * 40f, 400f, 8f), line("${r}0,5 g", 600f, r * 40f, 120f, 8f))
        }
        // At 8 degrees the axis-aligned boxes of a row's two cells are ~80 px apart vertically: another row.
        val upright = OcrLines.upright(tilted)
        upright.chunked(2).forEach { (label, value) -> assertTrue("$label / $value", kotlin.math.abs(label.box.centerY - value.box.centerY) <= 2) }
    }

    @Test
    fun uprightKeepsStraightLinesInPlace() {
        val straight = listOf(line("Grasas", 10f, 20f, 100f, 0f))
        assertEquals(TextBox(10, 20, 110, 50), OcrLines.upright(straight).single().box)
    }

    @Test
    fun quarterTurnUndoesSidewaysText() {
        fun lines(angle: Float) = (0 until 5).map { line("Texto largo $it", 0f, it * 40f, 300f, angle) }
        assertEquals(0, OcrLines.quarterTurn(lines(6f)))
        assertEquals(0, OcrLines.quarterTurn(lines(-30f)))
        assertEquals(270, OcrLines.quarterTurn(lines(90f)))
        assertEquals(90, OcrLines.quarterTurn(lines(-89f)))
        assertEquals(180, OcrLines.quarterTurn(lines(179f)))
        assertEquals(0, OcrLines.quarterTurn(emptyList()))
    }

    @Test
    fun shortLinesDoNotDecideTheAngle() {
        val lines = (0 until 3).map { line("Información $it", 0f, it * 40f, 300f, 2f) } +
            (0 until 4).map { line("3 g", 500f, it * 40f, 40f, 70f) }
        assertEquals(0, OcrLines.quarterTurn(lines))
    }
}
