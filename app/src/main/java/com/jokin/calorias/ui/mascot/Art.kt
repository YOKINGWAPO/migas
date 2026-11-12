package com.jokin.calorias.ui.mascot

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.sin

/*
 * The mascots were designed as SVG (diseno/rediseno.html). This is the subset of SVG they use, kept
 * as a small scene graph in a 108×108 box: built once per mascot, pose and reward, replayed per frame.
 */

/** Size of the drawing box, the same as an adaptive icon. */
internal const val ART_BOX = 108f

/** Gradients use bounding-box fractions, like SVG's objectBoundingBox. */
internal sealed interface Fill
internal data class Solid(val color: Color) : Fill
internal data class Linear(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val stops: List<Pair<Float, Color>>) : Fill
internal data class Radial(val cx: Float, val cy: Float, val r: Float, val stops: List<Pair<Float, Color>>) : Fill

internal class Line(val fill: Fill, val width: Float)

/** Looping animations a group can carry; the renderer turns them into transforms and alpha. */
internal enum class Motion { NONE, BLINK, FLAME_A, FLAME_B, RISE, FLOAT, BOLT, LASER }

internal sealed interface Node { val bounds: Rect }

internal class ShapeNode(val path: Path, val fill: Fill?, val line: Line?, val alpha: Float, val blur: Float) : Node {
    override val bounds: Rect = path.getBounds()
    val fillBrush: Brush? = fill?.toBrush(bounds)
    val lineBrush: Brush? = line?.fill?.toBrush(bounds)
}

internal class GroupNode(val children: List<Node>, val clip: Path?, val alpha: Float, val motion: Motion, val delay: Float) : Node {
    override val bounds: Rect = children.map { it.bounds }.reduceOrNull { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    } ?: Rect.Zero
}

internal class Art(val nodes: List<Node>, val shake: Boolean)

private fun Fill.toBrush(b: Rect): Brush = when (this) {
    is Solid -> SolidColor(color)
    is Linear -> Brush.linearGradient(
        *stops.toTypedArray(),
        start = Offset(b.left + x1 * b.width, b.top + y1 * b.height),
        end = Offset(b.left + x2 * b.width, b.top + y2 * b.height),
    )
    is Radial -> Brush.radialGradient(
        *stops.toTypedArray(),
        center = Offset(b.left + cx * b.width, b.top + cy * b.height),
        radius = (r * (b.width + b.height) / 2).coerceAtLeast(0.01f),
    )
}

/** Builds nodes with the current transform already applied to every path. */
internal class ArtBuilder {
    private val levels = ArrayDeque<MutableList<Node>>().apply { addLast(mutableListOf()) }
    private var matrix = Matrix()

    private fun add(node: Node) = levels.last().add(node)

    private fun place(p: Path, fill: Fill?, line: Line?, alpha: Float, blur: Float) {
        p.transform(matrix)
        add(ShapeNode(p, fill, line, alpha, blur))
    }

    fun path(d: String, fill: Fill? = null, line: Line? = null, alpha: Float = 1f, blur: Float = 0f) =
        place(PathParser().parsePathString(d).toPath(), fill, line, alpha, blur)

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, fill: Fill? = null, line: Line? = null, alpha: Float = 1f, blur: Float = 0f) =
        place(Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }, fill, line, alpha, blur)

    fun circle(cx: Float, cy: Float, r: Float, fill: Fill? = null, line: Line? = null, alpha: Float = 1f, blur: Float = 0f) =
        ellipse(cx, cy, r, r, fill, line, alpha, blur)

    fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float = 0f, fill: Fill? = null, line: Line? = null, alpha: Float = 1f) =
        place(Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(rx))) }, fill, line, alpha, 0f)

    fun group(clip: String? = null, alpha: Float = 1f, motion: Motion = Motion.NONE, delay: Float = 0f, block: ArtBuilder.() -> Unit) {
        val clipPath = clip?.let { PathParser().parsePathString(it).toPath().apply { transform(matrix) } }
        levels.addLast(mutableListOf())
        block()
        val children = levels.removeLast()
        add(GroupNode(children, clipPath, alpha, motion, delay))
    }

    /** SVG's rotate(deg cx cy). */
    fun rotate(deg: Float, cx: Float, cy: Float, block: ArtBuilder.() -> Unit) =
        transformed(Matrix().apply { translate(cx, cy); rotateZ(deg); translate(-cx, -cy) }, block)

    /** Mirror around the vertical line x = [cx]. */
    fun mirrorX(cx: Float, block: ArtBuilder.() -> Unit) =
        transformed(Matrix().apply { translate(2 * cx, 0f); scale(-1f, 1f) }, block)

    fun transformed(local: Matrix, block: ArtBuilder.() -> Unit) {
        val saved = matrix
        matrix = Matrix(saved.values.copyOf()).apply { timesAssign(local) }
        block()
        matrix = saved
    }

    fun build(shake: Boolean = false) = Art(levels.single().toList(), shake)
}

/** What the looping animations look like at [time] seconds; [blink] closes the eyes from 0 to 1. */
internal class Frame(val time: Float, val blink: Float) {
    companion object { val Still = Frame(0f, 0f) }
}

internal fun DrawScope.drawArt(art: Art, frame: Frame) {
    val k = size.minDimension / ART_BOX
    scale(k, pivot = Offset.Zero) {
        val dx = if (art.shake) sin(frame.time * 70f) * .45f else 0f
        val dy = if (art.shake) sin(frame.time * 53f + 1f) * .35f else 0f
        translate(dx, dy) { art.nodes.forEach { drawNode(it, frame, 1f) } }
    }
}

private fun loop(time: Float, period: Float, delay: Float): Float = (((time - delay) % period) + period) % period / period

private fun DrawScope.drawNode(node: Node, frame: Frame, parentAlpha: Float) {
    when (node) {
        is ShapeNode -> drawShape(node, parentAlpha * node.alpha)
        is GroupNode -> {
            val b = node.bounds
            var a = parentAlpha * node.alpha
            var sx = 1f; var sy = 1f; var ty = 0f
            var pivot = b.center
            when (node.motion) {
                Motion.NONE -> Unit
                Motion.BLINK -> sy = 1f - .9f * frame.blink
                Motion.FLAME_A, Motion.FLAME_B -> {
                    val period = if (node.motion == Motion.FLAME_A) .9f else .64f
                    var v = (sin(2 * PI * frame.time / period).toFloat() + 1f) / 2f
                    if (node.motion == Motion.FLAME_B) v = 1f - v
                    sx = .97f + .06f * v; sy = .94f + .16f * v
                    pivot = Offset(b.center.x, b.bottom)
                }
                Motion.RISE -> {
                    val u = loop(frame.time, .9f, node.delay)
                    ty = 8f - 30f * u
                    a *= if (u < .3f) u / .3f else 1f - (u - .3f) / .7f
                }
                Motion.FLOAT -> {
                    val u = loop(frame.time, 1.4f, node.delay)
                    ty = -16f * u
                    a *= if (u < .25f) u / .25f else 1f - (u - .25f) / .75f
                }
                Motion.BOLT -> {
                    val u = loop(frame.time, 1.05f, node.delay)
                    a *= if (u < .12f || (u >= .22f && u < .30f)) 1f else 0f
                }
                Motion.LASER -> a *= .75f + .25f * (sin(2 * PI * frame.time / 1.4f).toFloat() + 1f) / 2f
            }
            if (a <= 0f) return
            withTransform({
                translate(0f, ty)
                scale(sx, sy, pivot)
            }) {
                val clip = node.clip
                if (clip != null) clipPath(clip) { node.children.forEach { drawNode(it, frame, a) } }
                else node.children.forEach { drawNode(it, frame, a) }
            }
        }
    }
}

private fun DrawScope.drawShape(s: ShapeNode, alpha: Float) {
    if (s.blur > 0f) {
        // Compose has no per-shape blur; the framework paint does, through a mask filter.
        drawIntoCanvas { canvas ->
            val blur = BlurMaskFilter(s.blur, BlurMaskFilter.Blur.NORMAL)
            s.fillBrush?.let { canvas.nativeCanvas.drawPath(s.path.asAndroidPath(), framePaint(it, alpha, blur)) }
            s.lineBrush?.let {
                val p = framePaint(it, alpha, blur).apply { style = Paint.Style.STROKE; strokeWidth = s.line!!.width; strokeCap = Paint.Cap.ROUND }
                canvas.nativeCanvas.drawPath(s.path.asAndroidPath(), p)
            }
        }
        return
    }
    s.fillBrush?.let { drawPath(s.path, it, alpha = alpha) }
    s.lineBrush?.let {
        drawPath(s.path, it, alpha = alpha, style = Stroke(width = s.line!!.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.framePaint(brush: Brush, alpha: Float, blur: BlurMaskFilter) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        when (brush) {
            is SolidColor -> color = brush.value.copy(alpha = brush.value.alpha * alpha).toArgb()
            is ShaderBrush -> { shader = brush.createShader(size); this.alpha = (alpha * 255).toInt() }
        }
        maskFilter = blur
    }
