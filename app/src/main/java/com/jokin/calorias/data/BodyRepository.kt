package com.jokin.calorias.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.BodyMeasureEntity
import com.jokin.calorias.data.db.BodyPhotoEntity
import com.jokin.calorias.domain.BodyMeasures
import com.jokin.calorias.domain.BodyPart
import com.jokin.calorias.domain.BodyValue
import com.jokin.calorias.i18n.labelRes
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class PhotoImport(val added: Int, val alreadyThere: Int, val ignored: Int)

class BodyRepository(private val context: Context, private val db: AppDatabase, private val photos: PhotoInput) {
    private val photoDir: File get() = File(context.filesDir, "body_photos").apply { mkdirs() }

    fun observeDay(day: Long): Flow<List<BodyMeasureEntity>> = db.bodyDao().observeDay(day)

    fun observePhotos(day: Long): Flow<List<BodyPhotoEntity>> = db.bodyDao().observePhotos(day)

    suspend fun previous(day: Long): Map<BodyPart, BodyValue> = BodyMeasures.previous(db.bodyDao().upTo(day).map { it.toValue() }, day)

    suspend fun values(from: Long, to: Long): List<BodyValue> = db.bodyDao().range(from, to).map { it.toValue() }

    /** Replaces the day's measurements with [cm]; parts not in the map are removed. */
    suspend fun saveDay(day: Long, cm: Map<BodyPart, Double>) {
        cm.forEach { (part, v) -> require(v in BodyMeasures.CM_RANGE) { context.getString(R.string.mine_body_part_out_of_range, context.getString(part.labelRes())) } }
        db.withTransaction {
            db.bodyDao().clearDay(day)
            db.bodyDao().insertAll(cm.map { (part, v) -> BodyMeasureEntity(day, part, v) })
        }
    }

    suspend fun addCameraShot(day: Long) = addPhoto(day, photos.takeCameraShot(MAX_SIDE))

    suspend fun addGalleryPhoto(day: Long, uri: Uri) = addPhoto(day, photos.fromUri(uri, MAX_SIDE))

    /** Re-encodes to a JPEG of at most [MAX_SIDE] px: smaller, upright, and without the original EXIF (GPS included). */
    private suspend fun addPhoto(day: Long, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val file = File(photoDir, "${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { out -> if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw IOException(context.getString(R.string.mine_body_photo_save_failed)) }
            db.bodyDao().insertPhoto(BodyPhotoEntity(epochDay = day, fileName = file.name, takenAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun thumbnail(photo: BodyPhotoEntity, maxSide: Int): Bitmap = photos.fromFile(photoFile(photo), maxSide)

    /** File first: if it cannot be deleted the photo stays listed instead of leaving an orphan file. */
    suspend fun deletePhoto(photo: BodyPhotoEntity) = withContext(Dispatchers.IO) {
        val file = photoFile(photo)
        if (file.exists() && !file.delete()) throw IOException(context.getString(R.string.mine_body_photo_delete_failed))
        db.bodyDao().deletePhoto(photo.id)
    }

    suspend fun exportPhotos(): File = withContext(Dispatchers.IO) {
        val photos = db.bodyDao().allPhotos()
        if (photos.isEmpty()) throw IOException(context.getString(R.string.mine_body_no_photos))
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Old exports are temporary share payloads; keep only the newest one.
        dir.listFiles()?.forEach { it.delete() }
        File(dir, context.getString(R.string.mine_body_photos_zip_file, LocalDate.now().toString())).apply {
            ZipOutputStream(outputStream().buffered()).use { zip ->
                photos.forEach { p ->
                    zip.putNextEntry(ZipEntry(PhotoArchive.entryName(p.epochDay, p.fileName)))
                    photoFile(p).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Adds the photos of a ZIP made by [exportPhotos]; photos already present are skipped, nothing is replaced.
     * All or nothing: rows go in one transaction at the end, and on any failure the files written so far are removed.
     */
    suspend fun importPhotos(uri: Uri): PhotoImport = withContext(Dispatchers.IO) {
        val existing = db.bodyDao().photoFileNames().toMutableSet()
        val pending = mutableListOf<BodyPhotoEntity>()
        var alreadyThere = 0
        var ignored = 0
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException(context.getString(R.string.mine_open_file_failed))
            ZipInputStream(input.buffered()).use { zip ->
                var seen = 0
                while (true) {
                    val entry = nextEntry(zip) ?: break
                    if (++seen > MAX_ARCHIVE_ENTRIES) throw IOException(context.getString(R.string.mine_body_zip_too_many_files))
                    val parsed = PhotoArchive.parse(entry.name)
                    when {
                        entry.isDirectory || parsed == null -> ignored++
                        parsed.fileName in existing -> alreadyThere++
                        else -> {
                            val bytes = zip.readAtMost(MAX_PHOTO_BYTES)
                            if (bytes == null || !isJpeg(bytes)) {
                                ignored++
                            } else {
                                File(photoDir, parsed.fileName).writeBytes(bytes)
                                pending += BodyPhotoEntity(epochDay = parsed.epochDay, fileName = parsed.fileName, takenAt = System.currentTimeMillis())
                                existing += parsed.fileName
                            }
                        }
                    }
                }
            }
            db.withTransaction { pending.forEach { db.bodyDao().insertPhoto(it) } }
        } catch (e: Exception) {
            pending.forEach { File(photoDir, it.fileName).delete() }
            throw e
        }
        PhotoImport(pending.size, alreadyThere, ignored)
    }

    /** Android 14+ refuses entries such as "../x" inside ZipInputStream itself, before our own name check. */
    private fun nextEntry(zip: ZipInputStream): ZipEntry? = try {
        zip.nextEntry
    } catch (e: ZipException) {
        throw IOException(context.getString(R.string.mine_body_zip_foreign, e.message?.take(80).toString()), e)
    }

    /** Names come from the database, which an imported ZIP also fills: never trust them as a path. */
    private fun photoFile(photo: BodyPhotoEntity): File {
        require(PhotoArchive.isPhotoFileName(photo.fileName)) { context.getString(R.string.mine_body_bad_photo_name) }
        return File(photoDir, photo.fileName)
    }

    private fun isJpeg(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outMimeType == "image/jpeg" && bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun BodyMeasureEntity.toValue() = BodyValue(epochDay, part, cm)

    private companion object {
        const val MAX_SIDE = 2048
        const val MAX_PHOTO_BYTES = 20 * 1024 * 1024
        const val MAX_ARCHIVE_ENTRIES = 10_000
    }
}

/** Reads the rest of the current entry, or null if it is larger than [limit] (a zip bomb or not one of our photos). */
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val bytes = readNBytes(limit + 1)
    return bytes.takeIf { it.size <= limit }
}

/** ZIP entry names carry the day, so a restored photo lands on the same date: "2026-10-15_<uuid>.jpg". */
internal object PhotoArchive {
    data class Entry(val epochDay: Long, val fileName: String)

    private val FILE_NAME = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg""")
    private val ENTRY_NAME = Regex("""(\d{4}-\d{2}-\d{2})_(${FILE_NAME.pattern})""")

    fun isPhotoFileName(name: String) = FILE_NAME.matches(name)

    fun entryName(epochDay: Long, fileName: String) = "${LocalDate.ofEpochDay(epochDay)}_$fileName"

    /** Null for anything else, including names with a directory part. */
    fun parse(entryName: String): Entry? {
        val m = ENTRY_NAME.matchEntire(entryName) ?: return null
        val day = try {
            LocalDate.parse(m.groupValues[1]).toEpochDay()
        } catch (e: DateTimeParseException) {
            return null
        }
        return Entry(day, m.groupValues[2])
    }
}
