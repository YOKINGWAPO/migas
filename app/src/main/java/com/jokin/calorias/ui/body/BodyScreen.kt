package com.jokin.calorias.ui.body

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.BodyPhotoEntity
import com.jokin.calorias.domain.BodyMeasures
import com.jokin.calorias.domain.BodyPart
import com.jokin.calorias.domain.BodyValue
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SaveButton
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appLocale
import com.jokin.calorias.ui.common.dayTitle
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.shortDate
import com.jokin.calorias.ui.common.today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val MAX_GALLERY_PICK = 6

/** Returns the measurements ready to store, or why they cannot be stored. Blank fields are left out. */
private fun validated(resources: Resources, fields: Map<BodyPart, String>): Result<Map<BodyPart, Double>> {
    val out = mutableMapOf<BodyPart, Double>()
    fields.forEach { (part, text) ->
        if (text.isBlank()) return@forEach
        out[part] = parseDecimal(text)?.takeIf { it in BodyMeasures.CM_RANGE }
            ?: return Result.failure(
                IllegalArgumentException(resources.getString(R.string.mine_body_check_part, resources.getString(part.labelRes()).lowercase(appLocale())))
            )
    }
    return Result.success(out)
}

@Composable
fun BodyScreen(nav: NavController, day: Long) {
    val c = appContainer()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    val fields = remember { mutableStateMapOf<BodyPart, String>() }
    var previous by remember { mutableStateOf<Map<BodyPart, BodyValue>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var busyPhoto by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<BodyPhotoEntity?>(null) }
    val photos by c.body.observePhotos(day).collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(day) {
        c.body.observeDay(day).first().forEach { fields[it.part] = decimal(it.cm) }
        previous = c.body.previous(day)
        loaded = true
    }

    fun savePhoto(block: suspend () -> Unit) {
        busyPhoto = true
        scope.runAction(snackbar, resources.getString(R.string.mine_body_photo_saved)) { try { block() } finally { busyPhoto = false } }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) savePhoto { c.body.addCameraShot(day) }
    }
    fun openCamera() {
        try {
            takePicture.launch(c.photos.cameraShotUri())
        } catch (e: ActivityNotFoundException) {
            scope.runAction(snackbar) { snackbar.showSnackbar(resources.getString(R.string.mine_body_no_camera_app)) }
        }
    }
    // The manifest declares CAMERA for the scanner, so the system camera refuses to open without it.
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else scope.runAction(snackbar) { snackbar.showSnackbar(resources.getString(R.string.mine_body_no_camera_permission)) }
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_PICK)) { uris ->
        if (uris.isNotEmpty()) savePhoto { uris.forEach { c.body.addGalleryPhoto(day, it) } }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.mine_body_title, dayTitle(day, today())), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SectionCard {
            SectionTitle(stringResource(R.string.mine_body_tape))
            Muted(stringResource(R.string.mine_body_tape_hint))
            Spacer(Modifier.size(8.dp))
            BodyPart.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { part ->
                        Column(Modifier.weight(1f)) {
                            NumberField(fields[part].orEmpty(), { fields[part] = it }, stringResource(part.labelRes()), Modifier.fillMaxWidth(), suffix = "cm")
                            Muted(previous[part]?.let { stringResource(R.string.mine_body_before, decimal(it.cm), shortDate(it.epochDay)) } ?: " ")
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.size(8.dp))
            SaveButton(
                text = stringResource(R.string.mine_body_save),
                onSave = { c.body.saveDay(day, validated(resources, fields).getOrThrow()) },
                onSaved = { nav.popBackStack() },
                enabled = loaded,
            )
        }
        SectionCard {
            SectionTitle(stringResource(R.string.mine_body_photos))
            Muted(stringResource(R.string.mine_body_photos_hint))
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                    else askCamera.launch(Manifest.permission.CAMERA)
                }, enabled = !busyPhoto) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mine_body_camera))
                }
                FilledTonalButton(onClick = {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = !busyPhoto) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mine_body_gallery))
                }
                if (busyPhoto) CircularProgressIndicator(Modifier.size(24.dp))
            }
            if (photos.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { p -> PhotoThumb(p, Modifier.size(120.dp).clickable { opened = p }) }
                }
            }
        }
    }

    opened?.let { p ->
        PhotoDialog(p, onDismiss = { opened = null }, onDelete = {
            opened = null
            scope.runAction(snackbar, resources.getString(R.string.mine_body_photo_deleted)) { c.body.deletePhoto(p) }
        })
    }
}

private sealed interface Thumb {
    data object Loading : Thumb
    data class Ready(val bitmap: Bitmap) : Thumb
    data class Failed(val message: String) : Thumb
}

@Composable
private fun rememberPhoto(photo: BodyPhotoEntity, maxSide: Int): Thumb {
    val c = appContainer()
    val openFailed = stringResource(R.string.mine_open_failed)
    val thumb by produceState<Thumb>(Thumb.Loading, photo.id, maxSide) {
        value = try {
            Thumb.Ready(c.body.thumbnail(photo, maxSide))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Thumb.Failed(e.message ?: openFailed)
        }
    }
    return thumb
}

@Composable
private fun PhotoThumb(photo: BodyPhotoEntity, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        when (val t = rememberPhoto(photo, maxSide = 360)) {
            Thumb.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
            is Thumb.Ready -> Image(t.bitmap.asImageBitmap(), contentDescription = stringResource(R.string.mine_body_photo_of, shortDate(photo.epochDay)), contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            is Thumb.Failed -> Muted(t.message, Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun PhotoDialog(photo: BodyPhotoEntity, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirming) stringResource(R.string.mine_body_delete_photo_question) else shortDate(photo.epochDay)) },
        text = {
            if (confirming) {
                Text(stringResource(R.string.mine_body_delete_photo_warning))
            } else {
                when (val t = rememberPhoto(photo, maxSide = 1600)) {
                    Thumb.Loading -> CircularProgressIndicator()
                    is Thumb.Ready -> Image(t.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                    is Thumb.Failed -> Muted(t.message)
                }
            }
        },
        confirmButton = {
            if (confirming) TextButton(onClick = onDelete) { Text(stringResource(R.string.mine_delete)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.mine_close)) }
        },
        dismissButton = {
            if (confirming) TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.mine_cancel)) }
            else TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.mine_delete)) }
        },
    )
}
