package com.jokin.calorias.debug

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jokin.calorias.data.ocr.LabelOcr
import com.jokin.calorias.domain.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Benchmark harness, debug builds only. Reads every JPEG in files/labels-in, runs it through the
 * app's own [LabelOcr] as the camera would hand it over (and in harder variants), and writes the
 * OCR lines as JSON to files/labels-out, ending with a "done" marker. The JVM tests replay them.
 * Start it once before pushing so that it creates labels-in itself.
 *
 *   adb push *.jpg /sdcard/Android/data/com.jokin.calorias/files/labels-in/
 *   adb shell am start -n com.jokin.calorias/.debug.LabelOcrDumpActivity
 */
class LabelOcrDumpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Created by the app so it owns it: a folder made by adb is not listable by the app.
        val input = File(getExternalFilesDir(null), "labels-in").apply { mkdirs() }
        val output = File(getExternalFilesDir(null), "labels-out").apply { mkdirs() }
        File(output, "done").delete()
        lifecycleScope.launch {
            val ocr = LabelOcr()
            val photos = checkNotNull(input.listFiles { f -> f.extension == "jpg" }) { "cannot list $input" }.sortedBy { it.name }
            for (photo in photos) {
                val source = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photo.path) }
                if (source == null) {
                    Log.w(TAG, "cannot decode ${photo.name}")
                    continue
                }
                for ((variant, bitmap) in variants(source)) {
                    val json = JSONObject()
                        .put("width", bitmap.width)
                        .put("height", bitmap.height)
                        .put("passes", JSONArray(ocr.passes(bitmap).map(::passJson)))
                    withContext(Dispatchers.IO) { File(output, "${photo.nameWithoutExtension}.$variant.json").writeText(json.toString()) }
                }
                Log.i(TAG, "read ${photo.name}")
            }
            File(output, "done").writeText(photos.size.toString())
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun passJson(lines: List<OcrLine>) = JSONArray(
        lines.map { l ->
            JSONObject().put("text", l.text).put("angle", l.angle.toDouble())
                .put("corners", JSONArray(l.corners.flatMap { listOf(it.x.toDouble(), it.y.toDouble()) }))
        },
    )

    /** "photo" is what the camera hands over; "tilted" and "far" are the usual ways a handheld shot gets worse. */
    private fun variants(source: Bitmap): List<Pair<String, Bitmap>> {
        val photo = scaled(source, 2048)
        return listOf("photo" to photo, "tilted" to rotated(photo, 8f), "far" to scaled(source, 900))
    }

    private fun scaled(source: Bitmap, maxSide: Int): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }

    private fun rotated(source: Bitmap, degrees: Float): Bitmap {
        val turned = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees) }, true)
        // Corners left by the rotation are transparent; the camera would show background there, not black text.
        return Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(turned, 0f, 0f, null) }
        }
    }

    private companion object {
        const val TAG = "LabelOcrDump"
    }
}
