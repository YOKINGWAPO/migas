package com.jokin.calorias.data.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jokin.calorias.domain.TextBox
import com.jokin.calorias.domain.TextLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition; the Latin model ships inside the APK, so it works offline. */
class LabelOcr {
    suspend fun lines(photo: Bitmap): List<TextLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return recognizer.process(InputImage.fromBitmap(photo, 0)).await().toTextLines()
        } finally {
            recognizer.close()
        }
    }
}

fun Text.toTextLines(): List<TextLine> = textBlocks.flatMap { it.lines }.mapNotNull { line ->
    line.boundingBox?.let { b -> TextLine(line.text, TextBox(b.left, b.top, b.right, b.bottom)) }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
