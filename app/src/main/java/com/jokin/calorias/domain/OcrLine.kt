package com.jokin.calorias.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class OcrPoint(val x: Float, val y: Float)

/** A recognised line as the OCR returns it: its four corners follow the text, even when it is tilted. */
data class OcrLine(val text: String, val corners: List<OcrPoint>, val angle: Float)

/**
 * Turns OCR lines into upright [TextLine]s. A label photographed at an angle has every line tilted
 * the same way; its axis-aligned boxes then overlap the rows above and below, which breaks the
 * row grouping. Rotating all corners back by the typical angle restores straight rows.
 */
object OcrLines {
    // Short lines ("3,2 g") report noisy angles; the tilt of the table comes from the longer ones.
    private const val MIN_CHARS_FOR_ANGLE = 4

    private fun typicalAngle(lines: List<OcrLine>): Float {
        val reliable = lines.filter { it.text.length >= MIN_CHARS_FOR_ANGLE }.ifEmpty { lines }
        return reliable.map { it.angle }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
    }

    /**
     * Degrees to turn the photo clockwise so its text reads upright: a label shot sideways or upside
     * down reads badly even after [upright], so it is worth a second OCR pass on the turned photo.
     */
    fun quarterTurn(lines: List<OcrLine>): Int {
        val tilt = typicalAngle(lines)
        val turns = Math.round(-tilt / 90f).mod(4)
        return if (abs(tilt) > 45f) turns * 90 else 0
    }

    fun upright(lines: List<OcrLine>): List<TextLine> {
        val tilt = typicalAngle(lines)
        val rad = Math.toRadians(-tilt.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        return lines.filter { it.corners.isNotEmpty() }.map { line ->
            val xs = line.corners.map { p -> p.x * c - p.y * s }
            val ys = line.corners.map { p -> p.x * s + p.y * c }
            TextLine(line.text, TextBox(xs.min().roundToInt(), ys.min().roundToInt(), xs.max().roundToInt(), ys.max().roundToInt()))
        }
    }
}
