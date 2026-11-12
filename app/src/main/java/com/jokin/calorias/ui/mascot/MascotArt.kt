package com.jokin.calorias.ui.mascot

import androidx.compose.ui.graphics.Color
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.domain.StreakReward
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Faces the mascots can make; CHARGE is the effort face of the legendary reward. */
enum class Pose { NEUTRAL, HAPPY, HUNGRY, SLEEP, CHARGE }

private val INK = Color(0xFF2A1C14)
private val LINE = Color(0xFF3A2616)
private val WHITE = Color.White

private fun c(argb: Long) = Solid(Color(argb))
private fun ol(width: Float = 2.2f, color: Color = LINE) = Line(Solid(color), width)
private fun stroke(color: Color, width: Float) = Line(Solid(color), width)
private fun stroke(argb: Long, width: Float) = Line(c(argb), width)
private fun vertical(vararg stops: Pair<Float, Color>) = Linear(0f, 0f, 0f, 1f, stops.toList())
private fun diagonal(vararg stops: Pair<Float, Color>) = Linear(0f, 0f, 1f, 1f, stops.toList())

/** Fixed decimals: computed coordinates can come out as 1.0E-16, which a path string cannot hold. */
private fun f(v: Float) = String.format(Locale.ROOT, "%.2f", v)
private fun f(v: Double) = f(v.toFloat())
private fun ellipseD(cx: Float, cy: Float, rx: Float, ry: Float) =
    "M${cx - rx},$cy A$rx,$ry 0 1,0 ${cx + rx},$cy A$rx,$ry 0 1,0 ${cx - rx},$cy Z"

/** Where each accessory sits on a mascot, in the 108×108 box. */
private class Anchors(
    val cx: Float, val top: Float,
    val eyeL: Pair<Float, Float>, val eyeR: Pair<Float, Float>,
    val mustX: Float, val mustY: Float, val mustW: Float,
    val bandY: Float, val bandW: Float,
    val capeX: Float, val capeY: Float, val capeLen: Float, val capeW: Float, val capeClasp: Boolean,
    val chainX: Float, val chainY: Float, val chainW: Float,
    val bodyX: Float, val bodyY: Float, val bodyRx: Float, val bodyRy: Float,
)

// ---- shared bits -------------------------------------------------------------------------------

private fun ArtBuilder.eyeK(x: Float, y: Float, rx: Float = 4f, ry: Float = 4.8f, ink: Color = INK) =
    group(motion = Motion.BLINK) {
        ellipse(x, y, rx, ry, Solid(ink))
        circle(x + rx * .35f, y - ry * .38f, rx * .42f, Solid(WHITE))
        circle(x - rx * .35f, y + ry * .38f, rx * .2f, Solid(WHITE), alpha = .85f)
    }

private fun ArtBuilder.arcUp(x: Float, y: Float, w: Float, color: Color = INK, sw: Float = 2.6f) =
    path("M${x - w},${y + 1.5f} Q$x,${y - w * 1.15f} ${x + w},${y + 1.5f}", line = stroke(color, sw))

private fun ArtBuilder.arcDown(x: Float, y: Float, w: Float, color: Color = INK, sw: Float = 2.4f) =
    path("M${x - w},$y Q$x,${y + w * .9f} ${x + w},$y", line = stroke(color, sw))

private fun ArtBuilder.blush(x1: Float, x2: Float, y: Float) {
    ellipse(x1, y, 4.4f, 2.6f, c(0xFFF2836B), alpha = .55f)
    ellipse(x2, y, 4.4f, 2.6f, c(0xFFF2836B), alpha = .55f)
}

private fun ArtBuilder.bubble(x: Float, y: Float, r: Float = 5.4f) {
    circle(x, y, r, Solid(Color(0xFFD6ECFF).copy(alpha = .7f)), stroke(0xFF86BCE8, 1.1f))
    path("M${x - r * .55f},${y - r * .1f} A${r * .6f},${r * .6f} 0 0,1 ${x - r * .05f},${y - r * .6f}", line = stroke(WHITE, 1.3f))
}

/** "Z z z" drawn as strokes: no font to depend on. */
private fun ArtBuilder.zzz(x: Float, y: Float) {
    listOf(Triple(0f, 12f, .75f), Triple(8f, 9f, .55f), Triple(14f, 7f, .4f)).forEach { (dx, s, a) ->
        val l = x + dx; val b = y - dx
        val w = s * .62f; val h = s * .72f
        path("M$l,${b - h} L${l + w},${b - h} L$l,$b L${l + w},$b", line = stroke(0xFF6A7C73, s * .15f), alpha = a)
    }
}

private fun ArtBuilder.drool(x: Float, y: Float, l: Float = 7f) {
    path("M$x,$y C${x - 2.2f},${y + l * .55f} ${x - 2.2f},${y + l} $x,${y + l} C${x + 2.2f},${y + l} ${x + 2.2f},${y + l * .55f} $x,$y Z",
        c(0xFFCFEBFF), stroke(0xFF7FB5E0, 1f))
    circle(x - .6f, y + l * .75f, .7f, Solid(WHITE))
}

private fun ArtBuilder.sparkle(x: Float, y: Float, s: Float = 1f) = path(
    "M$x,${y - 4 * s} Q${x + .8f * s},${y - .8f * s} ${x + 4 * s},$y Q${x + .8f * s},${y + .8f * s} $x,${y + 4 * s} " +
        "Q${x - .8f * s},${y + .8f * s} ${x - 4 * s},$y Q${x - .8f * s},${y - .8f * s} $x,${y - 4 * s} Z",
    c(0xFFF4C152),
)

private fun ArtBuilder.vein(x: Float, y: Float) = path(
    "M${x - 3.4f},${y - 1} Q${x - 1},${y - 1} ${x - 1},${y - 3.4f} M${x + 1},${y - 3.4f} Q${x + 1},${y - 1} ${x + 3.4f},${y - 1} " +
        "M${x + 3.4f},${y + 1} Q${x + 1},${y + 1} ${x + 1},${y + 3.4f} M${x - 1},${y + 3.4f} Q${x - 1},${y + 1} ${x - 3.4f},${y + 1}",
    line = stroke(0xFFE2504A, 1.7f),
)

/** Wide-open effort eye of the charging face. */
private fun ArtBuilder.eyeI(x: Float, y: Float, rx: Float = 4.4f, ry: Float = 3.6f) {
    ellipse(x, y, rx, ry, Solid(WHITE), ol(1.5f))
    circle(x, y + .3f, 1.5f, Solid(INK))
    circle(x + .5f, y - .3f, .5f, Solid(WHITE))
}

private fun ArtBuilder.groundShadow() = ellipse(54f, 92f, 23f, 3.2f, Solid(Color.Black), alpha = .12f)

// ---- rewards -----------------------------------------------------------------------------------

private fun ArtBuilder.chefHat(cx: Float, top: Float) = rotate(-10f, cx, top) {
    val puff = "M${cx - 13},${top - 4} C${cx - 24},${top - 5} ${cx - 24},${top - 22} ${cx - 12},${top - 21} " +
        "C${cx - 11},${top - 32} ${cx + 3},${top - 34} ${cx + 6},${top - 25} C${cx + 12},${top - 31} ${cx + 25},${top - 24} ${cx + 13},${top - 4} Z"
    path(puff, diagonal(0f to WHITE, 1f to Color(0xFFDCE3EA)), ol())
    group(clip = puff) {
        ellipse(cx + 10, top - 8, 10f, 7f, c(0xFFB8C3CF), alpha = .7f, blur = 2f)
        ellipse(cx - 8, top - 24, 7f, 4f, Solid(WHITE), blur = 2f)
    }
    path("M${cx - 6},${top - 6} C${cx - 7},${top - 12} ${cx - 6},${top - 17} ${cx - 3.5f},${top - 21} " +
        "M${cx + 5},${top - 6} C${cx + 6},${top - 12} ${cx + 6},${top - 16} ${cx + 4},${top - 20}", line = stroke(0xFFC3CCD6, 1.5f))
    path("M${cx - 14},${top - 7} Q$cx,${top - 9} ${cx + 14},${top - 7} L${cx + 14},${top + 2} Q$cx,$top ${cx - 14},${top + 2} Z", c(0xFFF4F7FA), ol())
}

private fun ArtBuilder.crown(cx: Float, top: Float) = rotate(9f, cx, top) {
    path("M${cx - 15},${top + 2} L${cx - 17},${top - 13} L${cx - 8},${top - 5} L$cx,${top - 17} L${cx + 8},${top - 5} L${cx + 17},${top - 13} L${cx + 15},${top + 2} Z",
        vertical(0f to Color(0xFFFFEBA6), .5f to Color(0xFFF5C443), 1f to Color(0xFFD08A1C)), ol())
    path("M${cx - 15.4f},${top - 2} Q$cx,${top - 4} ${cx + 15.4f},${top - 2}", line = stroke(0xFFB87A15, 1.2f))
    path("M${cx - 14},${top - 11} L${cx - 12.5f},${top - 1}", line = stroke(0xFFFFF6D6, 1.3f), alpha = .9f)
    circle(cx - 17, top - 14, 2.3f, c(0xFFFFE594), ol(1.2f))
    circle(cx, top - 18, 2.5f, c(0xFFFFE594), ol(1.2f))
    circle(cx + 17, top - 14, 2.3f, c(0xFFFFE594), ol(1.2f))
    path("M$cx,${top - 7} L${cx + 3},${top - 3.5f} L$cx,$top L${cx - 3},${top - 3.5f} Z",
        Radial(.35f, .3f, .8f, listOf(0f to Color(0xFFFF8A80), 1f to Color(0xFFB3261E))), ol(1f))
    circle(cx - 8.5f, top - 2, 1.8f, c(0xFF4C8DE0), ol(.8f))
    circle(cx + 8.5f, top - 2, 1.8f, c(0xFF4C8DE0), ol(.8f))
}

private fun ArtBuilder.pixelGlasses(a: Anchors) {
    val p = 2.3f
    val (x1, y1) = a.eyeL
    val x2 = a.eyeR.first
    val y = y1 - 3.6f
    rotate(-4f, (x1 + x2) / 2, y) {
        rect(x1 - 8, y, x2 - x1 + 16, p, fill = c(0xFF0B0B0B))
        for (x in listOf(x1, x2)) {
            val left = x - 3 * p
            for (row in 0 until 3) for (col in 0 until 6) {
                if (row == 2 && (col == 0 || col == 5)) continue
                rect(left + col * p, y + p + row * p, p + .05f, p + .05f, fill = c(0xFF0B0B0B))
            }
            rect(left + p, y + p, p, p, fill = Solid(WHITE))
            rect(left + 2 * p, y + 2 * p, p, p, fill = Solid(WHITE))
        }
    }
}

private fun ArtBuilder.mustache(a: Anchors) {
    val x = a.mustX; val y = a.mustY; val s = a.mustW / 28
    val half = "M$x,${y - 1 * s} C${x - 3 * s},${y - 3.4f * s} ${x - 7 * s},${y - 3.4f * s} ${x - 9.5f * s},${y - .4f * s} " +
        "C${x - 11 * s},${y + 1.4f * s} ${x - 13 * s},${y + .6f * s} ${x - 14 * s},${y - 2.6f * s} " +
        "C${x - 14.4f * s},${y + 3 * s} ${x - 9.5f * s},${y + 5 * s} ${x - 5 * s},${y + 3 * s} C${x - 3 * s},${y + 2 * s} ${x - 1 * s},${y + 1.6f * s} $x,${y + 1.4f * s} Z"
    path(half, c(0xFF3B2416), ol(1.3f, Color(0xFF1E120B)))
    mirrorX(x) { path(half, c(0xFF3B2416), ol(1.3f, Color(0xFF1E120B))) }
    path("M${x - 9 * s},${y - .6f * s} q2,-1.2 4.4,-.4", line = stroke(0xFF7A5233, 1f))
}

private fun ArtBuilder.backwardsCap(a: Anchors) {
    val cx = a.cx; val t = a.top + 3
    rotate(-7f, cx, t) {
        path("M${cx - 15},${t + 4} C${cx - 26},${t + 1} ${cx - 31},${t + 5} ${cx - 30},${t + 9} C${cx - 24},${t + 9.5f} ${cx - 19},${t + 8} ${cx - 13},${t + 7.5f} Z", c(0xFF244B88), ol(1.8f))
        path("M${cx - 17},${t + 6} C${cx - 17},${t - 10} ${cx + 17},${t - 10} ${cx + 17},${t + 6} Z", vertical(0f to Color(0xFF6E9BE0), 1f to Color(0xFF2F5EA8)), ol())
        path("M$cx,${t - 7.6f} L$cx,${t + 6} M${cx - 9},${t - 5} C${cx - 11},${t - 1} ${cx - 11},${t + 3} ${cx - 10},${t + 6}", line = stroke(0xFF244B88, 1.1f))
        path("M${cx + 4},${t + 6} L${cx + 4},${t + 1.5f} Q${cx + 8},$t ${cx + 12},${t + 1.5f} L${cx + 12},${t + 6}", c(0xFFF4F7FA), ol(1.2f))
        circle(cx, t - 8, 1.8f, c(0xFF244B88), ol(1f))
        path("M${cx - 12},${t - 2} Q${cx - 8},${t - 6.5f} ${cx - 2},${t - 7}", line = stroke(0xFFA9C6F2, 1.4f), alpha = .8f)
    }
}

private fun ArtBuilder.bandTails(r: Float, y: Float, main: Long, dark: Long, w: Float) {
    path("M${r - 1},${y + 2} C${r + 6},${y - 2} ${r + 10},${y + 3} ${r + 15},${y - 1} C${r + 13},${y + 5} ${r + 8},${y + 5} ${r + 1},${y + 5} Z", c(dark), ol(w))
    path("M${r - 1},${y + 3} C${r + 5},${y + 5} ${r + 8},${y + 11} ${r + 13},${y + 12} C${r + 8},${y + 15} ${r + 3},${y + 10} ${r - 1},${y + 6} Z", c(main), ol(w))
}

private fun bandPath(l: Float, r: Float, cx: Float, y: Float, w: Float) =
    "M$l,$y C${cx - w / 4},${y - 2.4f} ${cx + w / 4},${y - 2.4f} $r,$y L$r,${y + 5.4f} C${cx + w / 4},${y + 3} ${cx - w / 4},${y + 3} $l,${y + 5.4f} Z"

private fun ArtBuilder.headband(a: Anchors) {
    val y = a.bandY; val w = a.bandW; val cx = a.cx; val l = cx - w / 2; val r = cx + w / 2
    bandTails(r, y, 0xFFC33B33, 0xFFC33B33, 1.5f)
    path(bandPath(l, r, cx, y, w), c(0xFFE2504A), ol(1.8f))
    path("M${l + 3},${y + 1.6f} C${cx - w / 4},${y - .4f} ${cx + w / 4},${y - .4f} ${r - 3},${y + 1.6f}", line = stroke(0xFFFF9A8E, 1f), alpha = .8f)
    ellipse(r, y + 2.8f, 2.8f, 3.2f, c(0xFFC33B33), ol(1.4f))
}

private fun ArtBuilder.ninjaBand(a: Anchors) {
    val y = a.bandY; val w = a.bandW; val cx = a.cx; val l = cx - w / 2; val r = cx + w / 2
    bandTails(r, y, 0xFF2D3D59, 0xFF24324A, 1.4f)
    path(bandPath(l, r, cx, y, w), c(0xFF2D3D59), ol(1.8f))
    rect(cx - 9.5f, y - 3.4f, 19f, 9.6f, 2.2f, vertical(0f to Color(0xFFF4F7FA), 1f to Color(0xFF97A3B0)), ol(1.5f))
    for ((dx, dy) in listOf(-7.4f to -1.4f, 7.4f to -1.4f, -7.4f to 4.2f, 7.4f to 4.2f)) circle(cx + dx, y + dy, .6f, c(0xFF6B7684))
    // A fork instead of any real village symbol.
    path("M${cx - 3},${y - 1.4f} L${cx - 3},${y + .8f} Q$cx,${y + 2.6f} ${cx + 3},${y + .8f} L${cx + 3},${y - 1.4f} M$cx,${y - 1.4f} L$cx,${y + 4.4f}", line = stroke(0xFF5B6574, 1.1f))
}

private fun ArtBuilder.capeBack(a: Anchors) {
    val cx = a.capeX; val cy = a.capeY; val len = a.capeLen; val w = a.capeW
    path("M${cx - 9},$cy C${cx - 17},${cy + len * .35f} ${cx - 15},${cy + len * .75f} ${cx - 8},${cy + len} " +
        "Q${cx + 4},${cy + len - 5} ${cx + 12},${cy + len + 1} Q${cx + 22},${cy + len - 6} ${cx + w + 16},${cy + len * .72f} " +
        "C${cx + w + 10},${cy + len * .42f} ${cx + w},${cy + len * .15f} ${cx + 9},$cy Z",
        diagonal(0f to Color(0xFFF0574B), 1f to Color(0xFF9E1F18)), ol())
    path("M${cx + w * .5f},${cy + len * .3f} Q${cx + w * .8f},${cy + len * .55f} ${cx + w + 6},${cy + len * .66f} " +
        "M${cx + 2},${cy + len * .45f} Q${cx + 6},${cy + len * .75f} ${cx + 10},${cy + len * .95f}", line = stroke(0xFF7A1510, 1.2f), alpha = .6f)
}

private fun ArtBuilder.capeFront(a: Anchors) {
    if (!a.capeClasp) return
    circle(a.capeX - 10, a.capeY + 3, 2.3f, c(0xFFF5C443), ol(1.2f))
    circle(a.capeX + 10, a.capeY + 3, 2.3f, c(0xFFF5C443), ol(1.2f))
}

/** Flame silhouette around the body: tongues grow towards the top. */
private fun flame(a: Anchors, sx: Float, sy: Float, n: Int, lift: Float): String {
    val bx = a.bodyX; val by = a.bodyY; val rx = a.bodyRx; val ry = a.bodyRy
    val d = StringBuilder("M${f(bx - rx * sx)},${f(by + ry * .9f)}")
    for (i in 0..n * 2) {
        val t = PI * (1 + i.toDouble() / (n * 2))
        val up = max(0.0, -sin(t))
        val k = if (i % 2 == 1) 1.12 + lift * up else .92
        d.append(" L${f(bx + rx * sx * k * cos(t))},${f(by - 6 + ry * sy * k * sin(t) - (if (i % 2 == 1) 10 * up else 0.0))}")
    }
    d.append(" L${f(bx + rx * sx)},${f(by + ry * .9f)} Q$bx,${by + ry * 1.15f} ${f(bx - rx * sx)},${f(by + ry * .9f)} Z")
    return d.toString()
}

private fun ArtBuilder.legendaryBack(a: Anchors) {
    val bx = a.bodyX; val by = a.bodyY; val rx = a.bodyRx; val ry = a.bodyRy
    group(motion = Motion.FLAME_A) {
        path(flame(a, 1.35f, 1.4f, 11, .7f), Radial(.5f, .75f, .7f, listOf(
            0f to WHITE, .35f to Color(0xFFFFF08A), .8f to Color(0xFFFFC21A).copy(alpha = .85f), 1f to Color(0xFFFF9F00).copy(alpha = 0f),
        )), blur = 1.2f)
    }
    group(motion = Motion.FLAME_B) {
        path(flame(a, 1.12f, 1.18f, 9, .5f), Radial(.5f, .75f, .6f, listOf(0f to WHITE, 1f to Color(0xFFFFE45C).copy(alpha = .2f))), blur = 1.2f)
    }
    listOf(-20f, -9f, 4f, 15f, 24f).forEachIndexed { i, dx ->
        group(motion = Motion.RISE, delay = i * .19f) { rect(bx + dx, by - 6, 1.6f, 10f + (i % 3) * 4, .8f, c(0xFFFFF6C2)) }
    }
    listOf(Triple(-22f, 2.2f, 0f), Triple(17f, 1.6f, .5f), Triple(27f, 2.6f, .9f), Triple(-12f, 1.4f, 1.2f)).forEach { (dx, r, delay) ->
        group(motion = Motion.FLOAT, delay = delay) {
            path("M${bx + dx - r},${by + ry + 4} l${r * .8f},${-r * 1.1f} l${r * 1.2f},${r * .2f} l${r * .4f},${r * 1.2f} Z", c(0xFF7C7468), ol(.8f))
        }
    }
    group(motion = Motion.FLAME_B) { ellipse(bx, by + ry + 5, rx + 10, 3.2f, c(0xFFFFE45C), alpha = .55f) }
}

private fun ArtBuilder.legendaryFront(a: Anchors) {
    val bx = a.bodyX; val by = a.bodyY; val rx = a.bodyRx
    val cx = a.cx; val t = a.top + 6
    path("M${cx - 24},${t + 8} L${cx - 35},${t - 3} L${cx - 24},${t - 6} L${cx - 32},${t - 21} L${cx - 17},${t - 13} L${cx - 18},${t - 34} " +
        "L${cx - 6},${t - 17} L${cx + 1},${t - 41} L${cx + 7},${t - 18} L${cx + 18},${t - 35} L${cx + 16},${t - 13} L${cx + 32},${t - 23} " +
        "L${cx + 23},${t - 5} L${cx + 35},${t - 4} L${cx + 24},${t + 8} L${cx + 14},${t + 3} L${cx + 9},${t + 10} L${cx + 4},${t + 3} " +
        "L${cx - 3},${t + 11} L${cx - 6},${t + 3} L${cx - 14},${t + 9} L${cx - 15},${t + 2} Z",
        vertical(0f to Color(0xFFFFFBD6), .45f to Color(0xFFFFE03D), 1f to Color(0xFFF2A900)), ol(2.1f))
    path("M${cx - 17},${t - 29} L${cx - 13},${t - 12} M${cx + 1},${t - 35} L${cx + 2},${t - 14} M${cx + 16},${t - 30} L${cx + 12},${t - 11} " +
        "M${cx - 27},${t - 17} L${cx - 18},${t - 8} M${cx + 27},${t - 19} L${cx + 18},${t - 7}",
        line = Line(vertical(0f to WHITE.copy(alpha = .9f), 1f to WHITE.copy(alpha = 0f)), 1.8f))
    group(motion = Motion.BOLT, delay = 0f) { path("M${bx - rx - 8},${by - 14} l5,4 l-4,3 l6,5 l-3,1", line = stroke(0xFF9BEBFF, 1.8f)) }
    group(motion = Motion.BOLT, delay = .35f) { path("M${bx + rx + 7},${by - 4} l-5,4 l4,3 l-6,5", line = stroke(WHITE, 1.6f)) }
    group(motion = Motion.BOLT, delay = .7f) { path("M${cx + 26},${t - 30} l-3,5 l4,2 l-4,6", line = stroke(0xFF9BEBFF, 1.5f)) }
    group(motion = Motion.BOLT, delay = .2f) { path("M${cx - 27},${t - 26} l3,5 l-4,2 l3,5", line = stroke(WHITE, 1.4f)) }
}

private fun ArtBuilder.laserEyes(a: Anchors) = group(motion = Motion.LASER) {
    for ((x, y) in listOf(a.eyeL, a.eyeR)) {
        circle(x, y, 9f, Radial(.5f, .5f, .5f, listOf(0f to WHITE, .3f to Color(0xFFFF4D3D), 1f to Color(0xFFFF1A1A).copy(alpha = 0f))))
        ellipse(x, y, 16f, 1.6f, c(0xFFFF3B2F), blur = 1.4f)
        ellipse(x, y, 1.4f, 8f, c(0xFFFF3B2F), alpha = .8f, blur = 1.4f)
        ellipse(x, y, 11f, .7f, Solid(WHITE))
        circle(x, y, 2.2f, Solid(WHITE))
    }
}

private fun ArtBuilder.strawHat(a: Anchors) {
    val cx = a.cx; val t = a.top + 2
    val straw = vertical(0f to Color(0xFFFBE3A0), 1f to Color(0xFFE2B85A))
    rotate(-8f, cx, t) {
        ellipse(cx, t + 5, 26f, 6.8f, straw, ol())
        val strokes = (0 until 14).joinToString(" ") { i ->
            val ang = PI * i / 13
            val x = cx - 24 * cos(ang); val y = t + 5 + 6 * sin(ang)
            "M${f(x * .78 + cx * .22)},${f(y * .8 + (t + 5) * .2)} L${f(x)},${f(y)}"
        }
        path(strokes, line = stroke(0xFFC99A3E, .9f))
        path("M${cx - 14},${t + 4} C${cx - 15},${t - 13} ${cx + 15},${t - 13} ${cx + 14},${t + 4} Z", straw, ol())
        path("M${cx - 14.4f},${t - 1} Q$cx,${t - 3.4f} ${cx + 14.4f},${t - 1} L${cx + 14.2f},${t + 4} Q$cx,${t + 1.8f} ${cx - 14.2f},${t + 4} Z", c(0xFFD83A2E), ol(1.6f))
        path("M${cx - 8},${t - 7} Q${cx - 3},${t - 10} ${cx + 4},${t - 9.5f}", line = stroke(0xFFFFF3CC, 1.4f), alpha = .8f)
    }
}

private fun ArtBuilder.goldChain(a: Anchors) {
    for (i in 0..12) {
        val u = i / 12f
        val x = a.chainX - a.chainW / 2 + a.chainW * u
        val y = a.chainY - 4 + 8 * 4 * u * (1 - u)
        if (i % 2 == 1) ellipse(x, y, 1.7f, 1.1f, c(0xFFF7CF4A), ol(.7f)) else circle(x, y, 1.4f, c(0xFFE9B32A), ol(.7f))
    }
}

private fun ArtBuilder.gangBandana(a: Anchors) {
    val cx = a.cx; val t = a.top + 3
    path("M${cx + 15},${t + 3} C${cx + 21},${t + 5} ${cx + 24},${t + 12} ${cx + 27},${t + 14} C${cx + 22},${t + 16} ${cx + 17},${t + 11} ${cx + 14},${t + 6} Z", c(0xFF1F7A35), ol(1.5f))
    path("M${cx + 15},${t + 3} C${cx + 22},$t ${cx + 26},${t + 4} ${cx + 30},${t + 3} C${cx + 27},${t + 8} ${cx + 21},${t + 8} ${cx + 15},${t + 6} Z", c(0xFF2A9343), ol(1.5f))
    path("M${cx - 17},${t + 6} C${cx - 18},${t - 11} ${cx + 18},${t - 11} ${cx + 17},${t + 6} Q$cx,${t + 2} ${cx - 17},${t + 6} Z", vertical(0f to Color(0xFF3FAE55), 1f to Color(0xFF1F7A35)), ol())
    for ((dx, dy, r) in listOf(Triple(-9f, -2f, 1.1f), Triple(-2f, -5f, 1f), Triple(6f, -1f, 1.2f), Triple(11f, -5f, .9f), Triple(-12f, 3f, .8f), Triple(1f, 1.5f, .9f))) {
        circle(cx + dx, t + dy, r, c(0xFFE9F7EC), alpha = .85f)
    }
    circle(cx + 15.5f, t + 4.5f, 2.6f, c(0xFF1F7A35), ol(1.3f))
    goldChain(a)
}

private fun ArtBuilder.plumberCap(a: Anchors) {
    val cx = a.cx; val t = a.top + 3
    path("M${cx - 16.5f},${t + 5} C${cx - 16.5f},${t - 12} ${cx + 16.5f},${t - 12} ${cx + 16.5f},${t + 5} Z", vertical(0f to Color(0xFFF0564B), 1f to Color(0xFFB8241B)), ol())
    circle(cx, t - 3, 4.6f, Solid(WHITE), ol(1.3f))
    path("M${cx - 18},${t + 4} Q$cx,${t + 12} ${cx + 18},${t + 4} Q$cx,${t + 7.5f} ${cx - 18},${t + 4} Z", c(0xFFA91F17), ol(1.6f))
    path("M${cx - 11},${t - 6} Q${cx - 7},${t - 9.5f} ${cx - 2},${t - 9.6f}", line = stroke(0xFFFFB3AB, 1.3f), alpha = .8f)
    mustache(a)
}

private fun ArtBuilder.elfHat(a: Anchors) {
    val cx = a.cx; val t = a.top + 4
    path("M${cx - 16},$t Q${cx - 13},${t - 15} ${cx + 3},${t - 19} Q${cx + 20},${t - 22} ${cx + 36},${t - 11} Q${cx + 24},${t - 10} ${cx + 15},${t - 6} Q${cx + 17},${t - 2} ${cx + 16},$t Z",
        Linear(0f, 0f, 1f, 0f, listOf(0f to Color(0xFF5DBB4A), 1f to Color(0xFF2F8A2C))), ol())
    path("M${cx - 6},${t - 12} Q${cx + 8},${t - 18} ${cx + 24},${t - 14}", line = stroke(0xFF9BE08A, 1.3f), alpha = .7f)
    path("M${cx - 17},${t + 4} Q$cx,${t - 1.5f} ${cx + 17},${t + 4} L${cx + 16.5f},${t - 1} Q$cx,${t - 6.5f} ${cx - 16.5f},${t - 1} Z", c(0xFF3D9A37), ol(1.6f))
}

private fun ArtBuilder.drillShades(a: Anchors) {
    val (x1, y) = a.eyeL
    val x2 = a.eyeR.first
    val lens = { x: Float, s: Float -> "M${x + 4 * s},${y - 3.5f} L${x - 13 * s},${y - 10} L${x - 7.5f * s},${y + 3} L${x + 3 * s},${y + 2.6f} Z" }
    val red = diagonal(0f to Color(0xFFFF9A5C), 1f to Color(0xFFD81F15))
    path("M${x1 + 4},${y - 2.6f} L${x2 - 4},${y - 2.6f}", line = stroke(LINE, 2.4f))
    path(lens(x1, 1f), red, ol(1.8f))
    path(lens(x2, -1f), red, ol(1.8f))
    path("M${x1 - 8},${y - 6} L${x1 - 2},${y - 3.6f} M${x2 + 8},${y - 6} L${x2 + 2},${y - 3.6f}", line = stroke(0xFFFFE1CC, 1.3f))
}

private fun ArtBuilder.electricEars(a: Anchors) {
    val cx = a.cx; val t = a.top
    val ear: ArtBuilder.() -> Unit = {
        path("M${cx - 13},${t + 6} C${cx - 21},${t - 5} ${cx - 27},${t - 17} ${cx - 26},${t - 26} C${cx - 18},${t - 21} ${cx - 10},${t - 9} ${cx - 5},${t + 3} Z", c(0xFFFFD43B), ol())
        path("M${cx - 26},${t - 26} C${cx - 22.5f},${t - 23} ${cx - 19.5f},${t - 19.5f} ${cx - 17.5f},${t - 16.5f} L${cx - 23.5f},${t - 13.5f} C${cx - 25},${t - 18} ${cx - 26.4f},${t - 22} ${cx - 26},${t - 26} Z", Solid(INK))
    }
    ear(this)
    mirrorX(cx, ear)
    circle(a.eyeL.first - 6, a.eyeL.second + 8.5f, 3.8f, c(0xFFE8463C), ol(1.2f))
    circle(a.eyeR.first + 6, a.eyeR.second + 8.5f, 3.8f, c(0xFFE8463C), ol(1.2f))
}

private fun ArtBuilder.rewardBack(r: StreakReward?, a: Anchors) = when (r) {
    StreakReward.CAPE -> capeBack(a)
    StreakReward.LEGENDARY -> legendaryBack(a)
    else -> Unit
}

private fun ArtBuilder.rewardFront(r: StreakReward?, a: Anchors) = when (r) {
    null -> Unit
    StreakReward.PIXEL_GLASSES -> pixelGlasses(a)
    StreakReward.MUSTACHE -> mustache(a)
    StreakReward.BACKWARDS_CAP -> backwardsCap(a)
    StreakReward.GANG_BANDANA -> gangBandana(a)
    StreakReward.PLUMBER_CAP -> plumberCap(a)
    StreakReward.ELECTRIC_EARS -> electricEars(a)
    StreakReward.HEADBAND -> headband(a)
    StreakReward.CHEF_HAT -> chefHat(a.cx, a.top)
    StreakReward.CAPE -> capeFront(a)
    StreakReward.STRAW_HAT -> strawHat(a)
    StreakReward.ELF_HAT -> elfHat(a)
    StreakReward.NINJA_BAND -> ninjaBand(a)
    StreakReward.LEGENDARY -> legendaryFront(a)
    StreakReward.LASER_EYES -> laserEyes(a)
    StreakReward.CROWN -> { crown(a.cx, a.top); sparkle(a.cx - 22, a.top - 10, .7f); sparkle(a.cx + 23, a.top - 14, .9f) }
    StreakReward.DRILL_SHADES -> drillShades(a)
}

// ---- mascots -----------------------------------------------------------------------------------

private val POTATO = Anchors(
    cx = 57f, top = 35f, eyeL = 47f to 56f, eyeR = 66f to 56f, mustX = 57f, mustY = 62.6f, mustW = 26f, bandY = 39f, bandW = 46f,
    capeX = 57f, capeY = 40f, capeLen = 46f, capeW = 26f, capeClasp = false, chainX = 57f, chainY = 77f, chainW = 30f,
    bodyX = 56f, bodyY = 60f, bodyRx = 27f, bodyRy = 26f,
)
private val PENGUIN = Anchors(
    cx = 54f, top = 32f, eyeL = 46.5f to 53.5f, eyeR = 61.5f to 53.5f, mustX = 54f, mustY = 63.4f, mustW = 22f, bandY = 39f, bandW = 38f,
    capeX = 54f, capeY = 46f, capeLen = 40f, capeW = 22f, capeClasp = true, chainX = 54f, chainY = 69f, chainW = 26f,
    bodyX = 54f, bodyY = 60f, bodyRx = 24f, bodyRy = 28f,
)
private val RACCOON = Anchors(
    cx = 54f, top = 37f, eyeL = 45f to 54f, eyeR = 63f to 54f, mustX = 54f, mustY = 64.6f, mustW = 24f, bandY = 41f, bandW = 42f,
    capeX = 54f, capeY = 72f, capeLen = 20f, capeW = 18f, capeClasp = true, chainX = 54f, chainY = 75f, chainW = 22f,
    bodyX = 54f, bodyY = 64f, bodyRx = 26f, bodyRy = 30f,
)

internal fun mascotArt(kind: MascotKind, pose: Pose, reward: StreakReward?): Art = ArtBuilder().apply {
    when (kind) {
        MascotKind.POTATO -> potato(pose, reward)
        MascotKind.PENGUIN -> penguin(pose, reward)
        MascotKind.RACCOON -> raccoon(pose, reward)
    }
}.build(shake = reward == StreakReward.LEGENDARY && pose == Pose.CHARGE)

private fun ArtBuilder.potato(pose: Pose, reward: StreakReward?) {
    val a = POTATO
    val body = "M30,60 C28,46 36,36 50,34 C58,33 62,36 68,36 C78,37 84,47 83,60 C82,75 72,86 56,86 C40,86 31,76 30,60 Z"
    val skin = c(0xFFD49B5C)
    val arm = { x: Float, y: Float, r: Float -> rotate(r, x, y) { ellipse(x, y, 4.3f, 7.2f, skin, ol()) } }
    groundShadow()
    rewardBack(reward, a)
    ellipse(46f, 87f, 5.5f, 3.6f, c(0xFFC48B4E), ol())
    ellipse(66f, 87f, 5.5f, 3.6f, c(0xFFC48B4E), ol())
    when (pose) {
        Pose.HAPPY -> { arm(26f, 46f, -35f); arm(87f, 46f, 35f) }
        Pose.HUNGRY -> arm(28f, 68f, 25f)
        else -> { arm(29f, 68f, 22f); arm(84f, 68f, -22f) }
    }
    path(body, Radial(.38f, .3f, .8f, listOf(0f to Color(0xFFF5CF95), .55f to Color(0xFFDFA764), 1f to Color(0xFFB97C41))))
    group(clip = body) { ellipse(72f, 80f, 28f, 15f, c(0xFF9E6430), alpha = .28f) }
    path(body, line = ol())
    path("M37,47 C41,40 47,37 54,37", line = stroke(0xFFFFEBC6, 3f), alpha = .85f)
    ellipse(74f, 45f, 2.4f, 1.6f, c(0xFF9C6532), alpha = .7f)
    circle(36f, 69f, 1.7f, c(0xFF9C6532), alpha = .7f)
    ellipse(77f, 70f, 1.9f, 1.3f, c(0xFF9C6532), alpha = .7f)
    blush(39.5f, 73.5f, 63f)
    when (pose) {
        Pose.CHARGE -> {
            eyeI(47f, 56f); eyeI(66f, 56f)
            path("M40,47.5 L52.5,52 M73,47.5 L60.5,52", line = stroke(INK, 3f))
            path("M47,63.5 Q57,61 67,63.5 Q65.5,76 57,76 Q48.5,76 47,63.5 Z", c(0xFF3A1A12))
            path("M51,71.5 Q57,68.5 63,71.5 Q60,75.5 57,75.5 Q54,75.5 51,71.5 Z", c(0xFFE86A5A))
            rect(54.5f, 62.6f, 4.4f, 3.4f, .8f, Solid(WHITE), ol(1f))
            vein(73f, 41f)
        }
        Pose.HAPPY -> {
            arcUp(47f, 56f, 4f); arcUp(66f, 56f, 4f)
            path("M48,64 Q57,64 65,63 Q63,73 56,73 Q49,73 48,64 Z", Solid(INK))
            path("M52,69.5 Q56.5,67 61,69.5 Q57,73 52,69.5 Z", c(0xFFE86A5A))
            rect(54f, 63.6f, 4f, 3f, .8f, Solid(WHITE))
        }
        Pose.HUNGRY -> {
            eyeK(47f, 55f, 5f, 6f); eyeK(66f, 55f, 5f, 6f)
            path("M41,46 L50,48 M72,46 L63,48", line = stroke(INK, 2.4f))
            ellipse(57f, 67f, 3.2f, 3.8f, Solid(INK))
            drool(61f, 69f)
        }
        Pose.SLEEP -> {
            arcDown(47f, 57f, 3.8f); arcDown(66f, 57f, 3.8f)
            ellipse(56f, 66f, 2f, 1.6f, Solid(INK))
            bubble(63f, 64f); zzz(74f, 38f)
        }
        Pose.NEUTRAL -> {
            eyeK(47f, 56f); eyeK(66f, 56f)
            path("M42,49 Q46,47 51,48.5", line = stroke(INK, 2.2f))
            path("M62,46 Q66,43.5 71,45.5", line = stroke(INK, 2.2f))
            path("M49,65 Q57,71 64,64", line = stroke(INK, 2.4f))
            path("M54.5,67.5 L54.8,71 L58.3,71 L58.2,68.3", Solid(WHITE), stroke(INK, 1.2f))
        }
    }
    if (pose == Pose.HUNGRY) rotate(-15f, 66f, 77f) { ellipse(66f, 77f, 7f, 4.3f, skin, ol()) }
    rewardFront(reward, a)
}

private fun ArtBuilder.penguin(pose: Pose, reward: StreakReward?) {
    val a = PENGUIN
    val orange = vertical(0f to Color(0xFFFFB760), 1f to Color(0xFFEE8A2B))
    val flip = { x: Float, y: Float, r: Float -> rotate(r, x, y) { ellipse(x, y, 5.6f, 13f, c(0xFF2A3442), ol()) } }
    val beakClosed = { path("M48.5,59 Q54,56.5 59.5,59 Q56.5,64.5 54,65.5 Q51.5,64.5 48.5,59 Z", orange, ol()) }
    groundShadow()
    rewardBack(reward, a)
    ellipse(45f, 87f, 7f, 3.6f, c(0xFFF29A3A), ol())
    ellipse(63f, 87f, 7f, 3.6f, c(0xFFF29A3A), ol())
    when (pose) {
        Pose.HAPPY -> { flip(27f, 52f, -55f); flip(81f, 52f, 55f) }
        Pose.HUNGRY -> flip(31f, 66f, 25f)
        else -> { flip(31f, 66f, 25f); flip(77f, 66f, -25f) }
    }
    path("M54,32 C70,32 78,46 78,62 C78,78 68,88 54,88 C40,88 30,78 30,62 C30,46 38,32 54,32 Z", vertical(0f to Color(0xFF425267), 1f to Color(0xFF202934)), ol())
    path("M54,46 C50,40 39,40 38,51 C37,58 40,62 41,66 C42,78 47,85 54,85 C61,85 66,78 67,66 C68,62 71,58 70,51 C69,40 58,40 54,46 Z", vertical(0f to WHITE, 1f to Color(0xFFD9E1EA)))
    path("M40,41 C44,36 49,34.5 54,34.5", line = stroke(WHITE, 2.6f), alpha = .35f)
    blush(41f, 67f, 60f)
    when (pose) {
        Pose.CHARGE -> {
            eyeI(46.5f, 53.5f, 4.2f, 3.4f); eyeI(61.5f, 53.5f, 4.2f, 3.4f)
            path("M39.5,45.5 L51,49.5 M68.5,45.5 L57,49.5", line = stroke(0xFFF4F6F8, 3.2f))
            path("M39.5,45.5 L51,49.5 M68.5,45.5 L57,49.5", line = stroke(INK, 2.2f))
            path("M48.5,57 Q54,54.5 59.5,57 Q54,59 48.5,57 Z", orange, ol())
            path("M48.5,60 Q54,59 59.5,60 Q57,71 54,71 Q51,71 48.5,60 Z", c(0xFF9E3A2E), ol())
            path("M50.5,65 Q54,62.8 57.5,65 Q55.5,70 54,70 Q52.5,70 50.5,65 Z", c(0xFFE8665A))
            vein(67f, 41f)
        }
        Pose.HAPPY -> {
            arcUp(46.5f, 53f, 3.8f); arcUp(61.5f, 53f, 3.8f)
            path("M48.5,58.5 Q54,56 59.5,58.5 Q54,60.5 48.5,58.5 Z", orange, ol())
            path("M49.5,60 Q54,59.5 58.5,60 Q56,67 54,67 Q52,67 49.5,60 Z", c(0xFFB44A3A), ol())
        }
        Pose.HUNGRY -> {
            eyeK(46.5f, 53f, 4.6f, 5.6f); eyeK(61.5f, 53f, 4.6f, 5.6f)
            path("M41,45 L49,47.5 M67,45 L59,47.5", line = stroke(INK, 2.2f))
            path("M48.5,58 Q54,55.5 59.5,58 Q54,60 48.5,58 Z", orange, ol())
            path("M50,61.5 Q54,61 58,61.5 Q55.5,66 54,66 Q52.5,66 50,61.5 Z", orange, ol())
        }
        Pose.SLEEP -> {
            arcDown(46.5f, 54f, 3.4f); arcDown(61.5f, 54f, 3.4f)
            beakClosed(); bubble(62f, 64f); zzz(73f, 36f)
        }
        Pose.NEUTRAL -> {
            eyeK(46.5f, 53.5f, 3.6f, 4.4f); eyeK(61.5f, 53.5f, 3.6f, 4.4f)
            path("M58,46 Q62,43.5 66,45.5", line = stroke(INK, 2.2f))
            path("M42,48 L50,48", line = stroke(INK, 2.2f))
            beakClosed()
        }
    }
    if (pose == Pose.HUNGRY) flip(68f, 64f, 38f)
    rewardFront(reward, a)
}

private fun ArtBuilder.raccoon(pose: Pose, reward: StreakReward?) {
    val a = RACCOON
    val mask = Color(0xFF2F3138)
    groundShadow()
    rewardBack(reward, a)
    rotate(-38f, 80f, 74f) {
        ellipse(80f, 72f, 9f, 19f, c(0xFFA3A6B0))
        group(clip = ellipseD(80f, 72f, 9f, 19f)) {
            rect(68f, 60f, 24f, 5f, fill = Solid(mask))
            rect(68f, 70f, 24f, 5f, fill = Solid(mask))
            rect(68f, 80f, 24f, 12f, fill = Solid(mask))
        }
        ellipse(80f, 72f, 9f, 19f, line = ol())
    }
    ellipse(54f, 81f, 16f, 10f, c(0xFF9497A1), ol())
    ellipse(54f, 83f, 9f, 6f, c(0xFFDCD8D0))
    circle(36f, 40f, 8f, c(0xFFA3A6B0), ol()); circle(72f, 40f, 8f, c(0xFFA3A6B0), ol())
    circle(36f, 41f, 4f, c(0xFF3E4048)); circle(72f, 41f, 4f, c(0xFF3E4048))
    path("M54,36 C68,36 78,44 79,52 L84,56 L79,59 C77,67 68,74 54,74 C40,74 31,67 29,59 L24,56 L29,52 C30,44 40,36 54,36 Z",
        vertical(0f to Color(0xFFBABDC6), 1f to Color(0xFF8F929C)), ol())
    path("M54,37.5 C51,42 51,46 54,49 C57,46 57,42 54,37.5 Z", c(0xFF3E4048))
    path("M32,55 C34,47 43,46 49,50.5 C52,52.5 56,52.5 59,50.5 C65,46 74,47 76,55 C74,62.5 65,62.5 59,58.5 C56,56.5 52,56.5 49,58.5 C43,62.5 34,62.5 32,55 Z", Solid(mask))
    path("M37,46 Q42.5,43 48,45.5 M60,45.5 Q65.5,43 71,46", line = stroke(0xFFF2EFE9, 2.2f))
    ellipse(54f, 65f, 11f, 8f, c(0xFFF2EFE9))
    path("M50.5,60 Q54,58.5 57.5,60 Q56,63.5 54,64 Q52,63.5 50.5,60 Z", Solid(INK))
    circle(52.8f, 60.2f, .8f, Solid(WHITE))
    when (pose) {
        Pose.CHARGE -> {
            eyeI(45f, 54.5f, 4f, 3.2f); eyeI(63f, 54.5f, 4f, 3.2f)
            path("M37,45 L49,49.5 M71,45 L59,49.5", line = stroke(0xFFF2EFE9, 2.8f))
            vein(70f, 41f)
            path("M47.5,65.5 Q54,64 60.5,65.5 Q59,75 54,75 Q49,75 47.5,65.5 Z", c(0xFF3A1A12))
            path("M49.6,65.6 L50.6,68.6 L51.8,65.4 Z M56.2,65.4 L57.4,68.6 L58.4,65.6 Z", Solid(WHITE))
            path("M50.5,72 Q54,70 57.5,72 Q55.5,74.6 54,74.6 Q52.5,74.6 50.5,72 Z", c(0xFFE86A5A))
        }
        Pose.HAPPY -> {
            arcUp(45f, 54f, 3.8f, WHITE); arcUp(63f, 54f, 3.8f, WHITE)
            path("M48.5,66 Q54,65 60,65.5 Q58.5,72.5 54,72.5 Q49.5,72.5 48.5,66 Z", Solid(INK))
            path("M50.5,66 L51.5,68.6 L52.6,66 Z", Solid(WHITE))
            path("M51.5,70 Q54.5,68.5 57.5,70 Q54.5,72.5 51.5,70 Z", c(0xFFE86A5A))
        }
        Pose.HUNGRY -> {
            circle(45f, 54f, 4.6f, Solid(WHITE)); circle(63f, 54f, 4.6f, Solid(WHITE))
            eyeK(45.5f, 55.5f, 3f, 3.4f); eyeK(63.5f, 55.5f, 3f, 3.4f)
            path("M50,67 Q54,69.5 58,67", line = stroke(INK, 1.9f))
            path("M55.5,68.2 Q58.5,72.5 60.3,67.5", c(0xFFE86A5A), stroke(INK, 1f))
            circle(54f, 81f, 8.5f, c(0xFFD9A066), ol())
            circle(51f, 78.5f, 1.4f, c(0xFF6B3E1E)); circle(57f, 80f, 1.6f, c(0xFF6B3E1E)); circle(52.5f, 84f, 1.3f, c(0xFF6B3E1E))
            path("M60.5,75.5 Q63,77 62.3,80", line = stroke(0xFFB9824A, 1.4f))
            ellipse(45f, 80f, 4.2f, 3.4f, c(0xFF8E919B), ol()); ellipse(63f, 80f, 4.2f, 3.4f, c(0xFF8E919B), ol())
        }
        Pose.SLEEP -> {
            arcDown(45f, 54.5f, 3.4f, WHITE); arcDown(63f, 54.5f, 3.4f, WHITE)
            path("M51.5,67 Q54,68.5 56.5,67", line = stroke(INK, 1.8f))
            bubble(61f, 67f); zzz(74f, 36f)
        }
        Pose.NEUTRAL -> {
            for (x in listOf(45f, 63f)) group(motion = Motion.BLINK) {
                circle(x, 54f, 4.4f, Solid(WHITE))
                circle(x + 1, 55f, 2.6f, Solid(INK))
                circle(x + 1.8f, 54.2f, .9f, Solid(WHITE))
                path("M${x - 5},54 A5,5 0 0,1 ${x + 5},54 Z", Solid(mask))
            }
            path("M50,67.5 Q55,69.5 60,65", line = stroke(INK, 2f))
        }
    }
    rewardFront(reward, a)
}
