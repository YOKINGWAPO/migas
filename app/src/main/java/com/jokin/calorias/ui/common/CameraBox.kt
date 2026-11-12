package com.jokin.calorias.ui.common

import androidx.camera.core.TorchState
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jokin.calorias.R

/**
 * Camera preview driven by [controller], with a torch toggle and a frame around [box]. The box is
 * in preview-view pixels, which is what ML Kit returns with COORDINATE_SYSTEM_VIEW_REFERENCED.
 * [locked] marks a detection that has been accepted.
 */
@Composable
fun CameraBox(controller: LifecycleCameraController, box: Rect?, locked: Boolean, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, controller) {
        controller.bindToLifecycle(lifecycleOwner)
        // The torch goes off on its own when the camera closes (app in background), so follow its real state.
        val torchObserver = Observer<Int> { torchOn = it == TorchState.ON }
        controller.torchState.observe(lifecycleOwner, torchObserver)
        onDispose {
            controller.torchState.removeObserver(torchObserver)
            controller.unbind()
        }
    }

    val frame = if (locked) MaterialTheme.colorScheme.primary else Color.White
    Box(modifier) {
        AndroidView(factory = { PreviewView(it).apply { this.controller = controller } }, modifier = Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            val r = box?.inflate(8.dp.toPx()) ?: return@Canvas
            val corner = CornerRadius(12.dp.toPx())
            if (locked) drawRoundRect(frame.copy(alpha = 0.25f), r.topLeft, r.size, corner, Fill)
            drawRoundRect(frame, r.topLeft, r.size, corner, Stroke(3.dp.toPx()))
        }
        FilledTonalIconButton(onClick = { controller.enableTorch(!torchOn) }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(
                if (torchOn) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                contentDescription = stringResource(if (torchOn) R.string.torch_off else R.string.torch_on),
            )
        }
    }
}
