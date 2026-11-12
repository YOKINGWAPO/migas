package com.jokin.calorias.ui.add

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenieMeshTest {
    private val left = 0f
    private val top = 1000f
    private val width = 1000f
    private val height = 1500f
    private val tx = 700f
    private val ty = 400f

    private fun mesh(p: Float) = GenieMesh.vertices(p, left, top, width, height, tx, ty, cols = 4, rows = 6)

    @Test
    fun `at the start the mesh is the sheet's own grid`() {
        val v = mesh(0f)
        // Third column (x = 500) of the fourth row (y = 1000 + 1500 * 3 / 6 = 1750).
        val i = (3 * 5 + 2) * 2
        assertEquals(500f, v[i], 0.01f)
        assertEquals(1750f, v[i + 1], 0.01f)
        assertEquals(left + width, v[(0 * 5 + 4) * 2], 0.01f)
        assertEquals(top + height, v.last(), 0.01f)
    }

    @Test
    fun `at the end every point sits on the target`() {
        val v = mesh(1f)
        for (k in v.indices step 2) {
            assertEquals("x of point ${k / 2}", tx, v[k], width * 0.02f)
            assertEquals("y of point ${k / 2}", ty, v[k + 1], 0.01f)
        }
    }

    @Test
    fun `halfway the top is narrower than the bottom, like a funnel`() {
        val v = mesh(0.4f)
        val topWidth = v[4 * 2] - v[0]
        val lastRow = 6 * 5 * 2
        val bottomWidth = v[lastRow + 4 * 2] - v[lastRow]
        assertTrue("top $topWidth vs bottom $bottomWidth", topWidth < bottomWidth * 0.6f)
        assertTrue(bottomWidth > 0f)
    }

    @Test
    fun `no point goes above the target while sliding`() {
        for (p in listOf(0.3f, 0.6f, 0.9f)) {
            val v = mesh(p)
            for (k in 1 until v.size step 2) assertTrue("p=$p y=${v[k]}", v[k] >= ty - 0.001f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a target inside the sheet is rejected`() {
        // The top rows would collapse onto it at once instead of funnelling.
        GenieMesh.vertices(0.5f, left, top, width, height, tx, top + 100f, 4, 6)
    }
}
