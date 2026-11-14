package com.jokin.calorias.data.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jokin.calorias.domain.LabelParser
import com.jokin.calorias.domain.OcrLine
import com.jokin.calorias.domain.OcrLines
import com.jokin.calorias.domain.OcrPoint
import com.jokin.calorias.domain.TextBox
import com.jokin.calorias.domain.TextLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition; the Latin model ships inside the APK, so it works offline. */
class LabelOcr {
    /**
     * Reads the photo twice when it can: once whole, and once cropped to the nutrition table and
     * enlarged, because small print in a wide shot is where the OCR misreads digits. A label shot
     * sideways is turned upright first. The passes come back in the coordinates of the (turned)
     * photo, for the parser to reconcile.
     */
    suspend fun passes(photo: Bitmap): List<List<OcrLine>> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val first = recognizer.read(photo)
            val turn = OcrLines.quarterTurn(first)
            val image = if (turn == 0) photo else Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
            val whole = if (turn == 0) first else recognizer.read(image)
            val table = LabelParser.tableBounds(whole.map { it.axisAligned() }) ?: return listOf(whole)
            val crop = TableCrop.of(table, image.width, image.height) ?: return listOf(whole)
            val enlarged = Bitmap.createBitmap(image, crop.left, crop.top, crop.width, crop.height)
                .scale((crop.width * crop.scale).toInt(), (crop.height * crop.scale).toInt())
            val zoomed = recognizer.read(enlarged).map { line -> line.copy(corners = line.corners.map(crop::toPhoto)) }
            return listOf(whole, zoomed)
        } finally {
            recognizer.close()
        }
    }

    private suspend fun TextRecognizer.read(bitmap: Bitmap): List<OcrLine> = process(InputImage.fromBitmap(bitmap, 0)).await().toOcrLines()
}

/** The table's box grown by a margin, and the zoom that brings its text to a size the OCR reads well. */
internal class TableCrop(val left: Int, val top: Int, val width: Int, val height: Int, val scale: Float) {
    fun toPhoto(p: OcrPoint) = OcrPoint(left + p.x / scale, top + p.y / scale)

    companion object {
        private const val TARGET_SIDE = 2000f
        private const val MAX_SCALE = 3f

        fun of(table: TextBox, photoWidth: Int, photoHeight: Int): TableCrop? {
            val marginX = (table.right - table.left) / 4
            val marginY = (table.bottom - table.top) / 6
            val left = (table.left - marginX).coerceIn(0, photoWidth - 1)
            val top = (table.top - marginY).coerceIn(0, photoHeight - 1)
            val width = ((table.right + marginX).coerceAtMost(photoWidth) - left)
            val height = ((table.bottom + marginY).coerceAtMost(photoHeight) - top)
            if (width < 16 || height < 16) return null
            val scale = (TARGET_SIDE / maxOf(width, height)).coerceIn(1f, MAX_SCALE)
            // Zooming adds nothing when the table already fills the shot.
            if (scale < 1.25f) return null
            return TableCrop(left, top, width, height, scale)
        }
    }
}

/** Axis-aligned lines in the image's own coordinates: what the camera overlay draws. */
fun Text.toTextLines(): List<TextLine> = textBlocks.flatMap { it.lines }.mapNotNull { line ->
    line.boundingBox?.let { b -> TextLine(line.text, TextBox(b.left, b.top, b.right, b.bottom)) }
}

fun Text.toOcrLines(): List<OcrLine> = textBlocks.flatMap { it.lines }.mapNotNull { line ->
    line.cornerPoints?.takeIf { it.size == 4 }?.let { pts -> OcrLine(line.text, pts.map { OcrPoint(it.x.toFloat(), it.y.toFloat()) }, line.angle) }
}

private fun OcrLine.axisAligned() = TextLine(
    text, TextBox(corners.minOf { it.x }.toInt(), corners.minOf { it.y }.toInt(), corners.maxOf { it.x }.toInt(), corners.maxOf { it.y }.toInt()),
)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
