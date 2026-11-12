package com.jokin.calorias.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import com.jokin.calorias.R
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos from the system camera, the gallery or app storage, decoded upright and no larger than asked. */
class PhotoInput(private val context: Context) {
    private val cameraShot: File get() = File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")

    /** Where the system camera writes the next shot; read it back with [takeCameraShot]. */
    fun cameraShotUri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", cameraShot)

    suspend fun takeCameraShot(maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        val shot = cameraShot
        try {
            decode(ImageDecoder.createSource(shot), maxSide)
        } finally {
            shot.delete()
        }
    }

    suspend fun fromUri(uri: Uri, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        decode(ImageDecoder.createSource(context.contentResolver, uri), maxSide)
    }

    suspend fun fromFile(file: File, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        decode(ImageDecoder.createSource(file), maxSide)
    }

    /** ImageDecoder applies the EXIF rotation itself, so the result is upright and carries no metadata. */
    private fun decode(source: ImageDecoder.Source, maxSide: Int): Bitmap = try {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val side = max(info.size.width, info.size.height)
            if (side > maxSide) decoder.setTargetSize(info.size.width * maxSide / side, info.size.height * maxSide / side)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: IOException) {
        throw IOException(context.getString(R.string.add_error_image_unreadable), e)
    }
}
