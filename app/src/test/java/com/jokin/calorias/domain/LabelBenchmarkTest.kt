package com.jokin.calorias.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Replays real nutrition labels (Open Food Facts photos read by ML Kit on the emulator, see
 * datos/etiquetas/) through the parser and scores it against the values typed in by the community.
 * Those values are sometimes outdated or for the prepared product, so 100 % is not reachable; the
 * floors below guard against regressions and are set from the measured scores.
 */
class LabelBenchmarkTest {
    private enum class Field(val key: String) { KCAL("kcal"), PROTEIN("protein"), CARBS("carbs"), FAT("fat"), SATURATED("saturated"), SUGARS("sugars"), FIBER("fiber"), SALT("salt") }

    private class Score {
        var labels = 0
        var read = 0
        var allCoreRight = 0
        var fields = 0
        var right = 0
        var wrong = 0

        val readPct get() = pct(read, labels)
        val corePct get() = pct(allCoreRight, labels)
        val rightPct get() = pct(right, fields)
        val wrongPct get() = pct(wrong, fields)

        private fun pct(n: Int, d: Int) = if (d == 0) 0.0 else 100.0 * n / d
    }

    private val labels: Map<String, JsonObject> by lazy {
        val stream = checkNotNull(javaClass.classLoader!!.getResourceAsStream("labels/off-labels.json.gz")) { "fixture missing" }
        val root = GZIPInputStream(stream).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        root.getValue("labels").jsonObject.mapValues { it.value.jsonObject }
    }

    private fun passes(json: JsonArray): List<List<TextLine>> = json.map { pass ->
        OcrLines.upright(
            pass.jsonArray.map { l ->
                val o = l.jsonObject
                val c = o.getValue("corners").jsonArray.map { it.jsonPrimitive.float }
                OcrLine(o.getValue("text").jsonPrimitive.content, c.chunked(2).map { (x, y) -> OcrPoint(x, y) }, o.getValue("angle").jsonPrimitive.float)
            },
        )
    }

    private fun value(r: LabelReading, f: Field): Double? = when (f) {
        Field.KCAL -> r.nutrients.kcal
        Field.PROTEIN -> r.nutrients.protein
        Field.CARBS -> r.nutrients.carbs
        Field.FAT -> r.nutrients.fat
        Field.SATURATED -> r.nutrients.extras[ExtraNutrient.SATURATED_FAT]
        Field.SUGARS -> r.nutrients.extras[ExtraNutrient.SUGARS]
        Field.FIBER -> r.nutrients.extras[ExtraNutrient.FIBER]
        Field.SALT -> r.nutrients.extras[ExtraNutrient.SALT]
    }

    // Labels round (EU guidance: 0.1 g steps under 10 g, whole grams above), and kJ-only labels convert with drift.
    private fun matches(f: Field, read: Double, truth: Double): Boolean =
        if (f == Field.KCAL) abs(read - truth) <= maxOf(3.0, truth * 0.02) else abs(read - truth) <= maxOf(0.15, truth * 0.03)

    private fun score(variant: String, failures: MutableList<String>? = null): Score {
        val s = Score()
        for ((code, label) in labels) {
            val json = label.getValue("variants").jsonObject[variant]?.jsonArray ?: continue
            val truth = label.getValue("truth").jsonObject.mapValues { it.value.jsonPrimitive.double }
            s.labels++
            val reading = LabelParser.parseBest(passes(json))
            if (reading != null) s.read++
            var coreRight = reading != null
            val wrongFields = mutableListOf<String>()
            for (f in Field.entries) {
                val expected = truth[f.key] ?: continue
                s.fields++
                // The parser fills the four core fields with 0 when absent, so a 0 there counts as "not read".
                val got = reading?.let { value(it, f) }?.takeUnless { it == 0.0 && f.ordinal <= Field.FAT.ordinal && expected != 0.0 }
                val ok = got != null && matches(f, got, expected)
                if (ok) s.right++ else if (got != null) s.wrong++
                if (!ok && f.ordinal <= Field.FAT.ordinal) coreRight = false
                if (!ok) wrongFields += "${f.key}=${got ?: "-"}/$expected"
            }
            if (coreRight) s.allCoreRight++
            if (wrongFields.isNotEmpty()) failures?.add("$code ${wrongFields.joinToString(" ")}")
        }
        return s
    }

    @Test
    fun realLabelsScore() {
        val report = StringBuilder("variant  labels  read%  core%  fields-right%  fields-wrong%\n")
        val scores = listOf("photo", "tilted", "far").associateWith { v ->
            score(v).also { s ->
                report.append("%-7s  %6d  %5.1f  %5.1f  %13.1f  %13.1f\n".format(v, s.labels, s.readPct, s.corePct, s.rightPct, s.wrongPct))
            }
        }
        println(report)
        if (System.getenv("LABEL_FAILURES") != null) mutableListOf<String>().also { score("photo", it) }.forEach(::println)
        val photo = scores.getValue("photo")
        assertTrue("fixture too small: ${photo.labels}", photo.labels >= 140)
        assertTrue(report.toString(), photo.rightPct >= FLOOR_PHOTO_RIGHT)
        assertTrue(report.toString(), photo.wrongPct <= CEILING_PHOTO_WRONG)
        assertTrue(report.toString(), scores.getValue("tilted").rightPct >= FLOOR_TILTED_RIGHT)
        assertTrue(report.toString(), scores.getValue("far").rightPct >= FLOOR_FAR_RIGHT)
    }

    private companion object {
        // Measured 2026-11-13 on 143 labels: photo 71.3 % right / 8.7 % wrong, tilted 69.0 %, far 70.3 %.
        // The version before this benchmark scored 43.5 % / 7.1 %, tilted 5.5 %, far 42.1 %.
        const val FLOOR_PHOTO_RIGHT = 70.0
        const val CEILING_PHOTO_WRONG = 9.5
        const val FLOOR_TILTED_RIGHT = 67.5
        const val FLOOR_FAR_RIGHT = 68.5
    }
}
