package com.jokin.calorias.ui.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.jokin.calorias.data.BarcodeResult
import com.jokin.calorias.data.remote.Barcodes
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.CameraBox
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.appContainer
import kotlinx.coroutines.CancellationException

private sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val code: String) : ScanState
    data class Failed(val code: String, val message: String) : ScanState
}

@Composable
fun ScanScreen(nav: NavController, day: Long, meal: MealType) {
    val context = LocalContext.current
    val res = LocalResources.current
    val c = appContainer()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) askPermission.launch(Manifest.permission.CAMERA) }

    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var manualCode by remember { mutableStateOf("") }

    // Runs the lookup for whatever code is in Looking state; the camera stops analysing meanwhile.
    LaunchedEffect(state) {
        val s = state as? ScanState.Looking ?: return@LaunchedEffect
        state = try {
            when (val r = c.foods.lookupBarcode(s.code)) {
                is BarcodeResult.Found -> {
                    nav.navigate(Routes.log(r.food.id, day, meal)) { popUpTo(Routes.SCAN) { inclusive = true } }
                    return@LaunchedEffect
                }
                is BarcodeResult.Missing -> {
                    nav.navigate(Routes.food(0, r.barcode, r.name, r.brand)) { popUpTo(Routes.SCAN) { inclusive = true } }
                    return@LaunchedEffect
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ScanState.Failed(s.code, e.message ?: res.getString(R.string.add_network_error))
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_scan_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPermission) {
                BarcodeCamera(enabled = state is ScanState.Scanning) { code -> state = ScanState.Looking(code) }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Muted(stringResource(R.string.add_scan_needs_camera))
                    Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.add_grant_permission)) }
                }
            }
            if (state is ScanState.Looking) CircularProgressIndicator()
        }
        when (val s = state) {
            is ScanState.Looking -> Muted(stringResource(R.string.add_scan_looking, s.code))
            is ScanState.Failed -> {
                Text(stringResource(R.string.add_scan_failed, s.code, s.message), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { state = ScanState.Looking(s.code) }) { Text(stringResource(R.string.add_retry)) }
                    OutlinedButton(onClick = { nav.navigate(Routes.food(0, s.code)) }) { Text(stringResource(R.string.add_scan_create_manually)) }
                }
            }
            ScanState.Scanning -> Muted(stringResource(R.string.add_scan_aim))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualCode,
                onValueChange = { v -> if (v.length <= 14 && v.all(Char::isDigit)) manualCode = v },
                label = { Text(stringResource(R.string.add_scan_manual_code)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state = ScanState.Looking(manualCode) }, enabled = Barcodes.isValid(manualCode)) { Text(stringResource(R.string.add_search)) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BarcodeCamera(enabled: Boolean, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val controller = remember { LifecycleCameraController(context) }
    var box by remember { mutableStateOf<Rect?>(null) }
    // Analyzer callbacks keep arriving until the next recomposition; this lets only the first code through.
    var accepted by remember { mutableStateOf(false) }
    val isEnabled by rememberUpdatedState(enabled)
    val currentOnCode by rememberUpdatedState(onCode)
    LaunchedEffect(enabled) { if (enabled) { accepted = false; box = null } }

    DisposableEffect(controller) {
        val main = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_QR_CODE,
            ).build()
        )
        controller.setImageAnalysisAnalyzer(
            main,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, main) { result ->
                if (!isEnabled || accepted) return@MlKitAnalyzer
                // QR codes can carry any text; only digit-only food codes are accepted.
                val found = result.getValue(scanner).orEmpty().firstOrNull { Barcodes.isValid(it.rawValue?.trim().orEmpty()) }
                box = found?.boundingBox?.toComposeRect()
                if (found != null) {
                    accepted = true
                    currentOnCode(found.rawValue!!.trim())
                }
            },
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            scanner.close()
        }
    }
    CameraBox(controller, box, locked = accepted, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
}
