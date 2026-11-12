package com.jokin.calorias.ui.add

/**
 * Vertices for the "genie" that sucks the add sheet into the day's ring, as for drawBitmapMesh:
 * (cols + 1) × (rows + 1) points, x then y, row by row from the top.
 *
 * The sheet first narrows into a funnel whose tip is the target, then slides up through it. Each row
 * is narrower the closer it is to the target, so the sides curve like macOS minimising a window.
 * At [progress] 0 it is the untouched rectangle; at 1 every point is on the target.
 */
object GenieMesh {
    /** Width a row keeps when it reaches the target, so the funnel tip never inverts. */
    private const val TIP_WIDTH = 0.02f
    /** Share of the run spent narrowing before the slide takes over. */
    private const val SQUEEZE_SHARE = 0.45f
    private const val SLIDE_START = 0.15f

    fun vertices(
        progress: Float,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        targetX: Float,
        targetY: Float,
        cols: Int,
        rows: Int,
    ): FloatArray {
        require(cols > 0 && rows > 0) { "the mesh needs at least one cell" }
        require(targetY < top) { "the target must be above the sheet" }
        val p = progress.coerceIn(0f, 1f)
        val squeeze = smooth((p / SQUEEZE_SHARE).coerceAtMost(1f))
        val slideT = ((p - SLIDE_START) / (1f - SLIDE_START)).coerceIn(0f, 1f)
        val slide = slideT * slideT * slideT
        val bottom = top + height
        val travel = bottom - targetY
        val centerX = left + width / 2
        val out = FloatArray((cols + 1) * (rows + 1) * 2)
        var i = 0
        for (r in 0..rows) {
            val y = maxOf(targetY, top + height * r / rows - slide * travel)
            val closeness = smooth(((y - targetY) / travel).coerceIn(0f, 1f))
            val rowWidth = lerp(1f, TIP_WIDTH + (1f - TIP_WIDTH) * closeness, squeeze)
            val rowCenter = lerp(centerX, targetX, squeeze * (1f - closeness))
            for (c in 0..cols) {
                val x0 = left + width * c / cols
                out[i++] = rowCenter + (x0 - centerX) * rowWidth
                out[i++] = y
            }
        }
        return out
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
