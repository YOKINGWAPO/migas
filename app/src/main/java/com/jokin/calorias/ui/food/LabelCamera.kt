package com.jokin.calorias.ui.food

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jokin.calorias.R
import com.jokin.calorias.data.ocr.toOcrLines
import com.jokin.calorias.data.ocr.toTextLines
import com.jokin.calorias.data.remote.EstimatedFood
import com.jokin.calorias.domain.LabelParser
import com.jokin.calorias.domain.LabelReading
import com.jokin.calorias.domain.Macro
import com.jokin.calorias.domain.OcrLines
import com.jokin.calorias.domain.TextLine
import com.jokin.calorias.ui.common.CameraBox
import com.jokin.calorias.ui.common.decimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private sealed interface LabelShot {
    data class Aiming(val error: String? = null) : LabelShot
    data class Reading(val photo: Bitmap) : LabelShot
}

/**
 * Full-screen camera for a nutrition label: frames the table while aiming, then reads the shot
 * with [read] behind a progress overlay that can be cancelled. With [liveReading] the values read
 * on device from the last frames show while aiming and go to [read] along with the shot.
 */
@Composable
fun LabelCameraDialog(
    modeLabel: String,
    liveReading: Boolean,
    read: suspend (Bitmap, LabelReading?) -> EstimatedFood,
    onResult: (EstimatedFood) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            LabelCamera(modeLabel, liveReading, read, onResult, onDismiss)
        }
    }
}

@Composable
private fun LabelCamera(
    modeLabel: String,
    liveReading: Boolean,
    read: suspend (Bitmap, LabelReading?) -> EstimatedFood,
    onResult: (EstimatedFood) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) askPermission.launch(Manifest.permission.CAMERA) }

    var shot by remember { mutableStateOf<LabelShot>(LabelShot.Aiming()) }
    var live by remember { mutableStateOf<LabelReading?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS) }
    }

    fun shoot() {
        job = scope.launch {
            try {
                val seen = live
                val photo = controller.takeUprightPhoto()
                shot = LabelShot.Reading(photo)
                onResult(read(photo, seen))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shot = LabelShot.Aiming(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.add_close), tint = Color.White) }
            Text(
                stringResource(R.string.add_label_camera_title, modeLabel), color = Color.White,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                !hasPermission -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.add_label_needs_camera), color = Color.White)
                    Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.add_grant_permission)) }
                }
                else -> LabelPreview(controller, aiming = shot is LabelShot.Aiming, onReading = { if (liveReading) live = it })
            }
            (shot as? LabelShot.Reading)?.let { reading ->
                Image(reading.photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(56.dp), color = Color.White)
                        Text(stringResource(R.string.add_label_reading), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { job?.cancel(); shot = LabelShot.Aiming() }) { Text(stringResource(R.string.add_cancel), color = Color.White) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val error = (shot as? LabelShot.Aiming)?.error
            Text(
                error ?: stringResource(R.string.add_label_aim),
                color = if (error != null) MaterialTheme.colorScheme.error else Color.White,
                textAlign = TextAlign.Center,
            )
            live?.takeIf { shot is LabelShot.Aiming }?.let { LiveReading(it) }
            FilledIconButton(onClick = ::shoot, enabled = hasPermission && shot is LabelShot.Aiming, modifier = Modifier.size(72.dp), shape = CircleShape) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.add_take_photo), modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** What the last frames read, so the user can tell whether the table is legible before shooting. */
@Composable
private fun LiveReading(r: LabelReading) {
    fun grams(m: Macro, v: Double) = if (m in r.unreadMacros) "–" else decimal(v)
    Text(
        stringResource(
            R.string.add_label_live, decimal(r.nutrients.kcal, 0),
            grams(Macro.PROTEIN, r.nutrients.protein), grams(Macro.CARBS, r.nutrients.carbs), grams(Macro.FAT, r.nutrients.fat),
        ),
        color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
    )
}

/**
 * Live preview that frames the nutrition table found by on-device OCR, whatever the reading mode,
 * and hands [onReading] what the last [LIVE_FRAMES] frames read together (null while they read nothing).
 */
@Composable
private fun LabelPreview(controller: LifecycleCameraController, aiming: Boolean, onReading: (LabelReading?) -> Unit) {
    val context = LocalContext.current
    var box by remember { mutableStateOf<Rect?>(null) }
    val isAiming by rememberUpdatedState(aiming)
    val report by rememberUpdatedState(onReading)
    val frames = remember { ArrayDeque<List<TextLine>>() }

    DisposableEffect(controller) {
        val main = ContextCompat.getMainExecutor(context)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        controller.setImageAnalysisAnalyzer(
            main,
            MlKitAnalyzer(listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                if (!isAiming) return@MlKitAnalyzer
                val text = result.getValue(recognizer)
                box = text?.let { LabelParser.tableBounds(it.toTextLines()) }
                    ?.let { b -> Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()) }
                frames.addLast(text?.let { OcrLines.upright(it.toOcrLines()) }.orEmpty())
                while (frames.size > LIVE_FRAMES) frames.removeFirst()
                report(LabelParser.parseBest(frames.toList()))
            },
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            recognizer.close()
        }
    }
    CameraBox(controller, box, locked = false, modifier = Modifier.fillMaxSize())
}

// About a second of preview: enough frames to outvote one misread, short enough to follow the aim.
private const val LIVE_FRAMES = 6

/** Captures a photo rotated upright and no larger than [maxSide] px, which is plenty for a label. */
private suspend fun LifecycleCameraController.takeUprightPhoto(maxSide: Int = 2048): Bitmap = suspendCancellableCoroutine { cont ->
    takePicture(Dispatchers.Default.asExecutor(), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val photo = try {
                image.toUpright(maxSide)
            } catch (e: Exception) {
                cont.resumeWithException(e)
                return
            } finally {
                image.close()
            }
            cont.resume(photo)
        }

        override fun onError(exception: ImageCaptureException) {
            cont.resumeWithException(exception)
        }
    })
}

private fun ImageProxy.toUpright(maxSide: Int): Bitmap {
    val source = toBitmap()
    val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
