package com.jokin.calorias.ui.add

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.remote.EstimatedDish
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.MealSelector
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.dayTitle
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.today

/** The photo only has to show the plate; 1600 px is what gets uploaded anyway. */
private const val PHOTO_SIDE = 1600

/** One editable component of the estimate; grams stay text until saved so half-typed numbers are allowed. */
private data class DishDraft(val dish: EstimatedDish, val name: String, val gramsText: String) {
    constructor(dish: EstimatedDish) : this(dish, dish.name, "%.0f".format(dish.grams))

    val grams: Double? get() = parseDecimal(gramsText)?.takeIf { it > 0 && it < 10_000 }
}

@Composable
fun MealPhotoScreen(nav: NavController, day: Long, initialMeal: MealType) {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    var hint by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var estimated by remember { mutableStateOf(false) }
    var confidence by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateListOf<DishDraft>() }

    fun estimate() {
        val p = photo ?: return
        busy = true
        drafts.clear()
        estimated = false
        scope.runAction(snackbar) {
            try {
                val r = c.foods.estimateMeal(p, hint)
                drafts += r.dishes.map(::DishDraft)
                confidence = r.confidence
                note = r.note ?: if (r.dishes.isEmpty()) res.getString(R.string.add_meal_no_food_seen) else null
                estimated = true
            } finally { busy = false }
        }
    }
    fun usePhoto(load: suspend () -> Bitmap) {
        busy = true
        scope.runAction(snackbar) {
            try { photo = load() } finally { busy = false }
            estimate()
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) usePhoto { c.photos.takeCameraShot(PHOTO_SIDE) }
    }
    fun openCamera() {
        try {
            takePicture.launch(c.photos.cameraShotUri())
        } catch (e: ActivityNotFoundException) {
            scope.runAction(snackbar) { snackbar.showSnackbar(res.getString(R.string.add_no_camera_app)) }
        }
    }
    // The manifest declares CAMERA for the scanner, so the system camera refuses to open without it.
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else scope.runAction(snackbar) { snackbar.showSnackbar(res.getString(R.string.add_meal_no_camera_permission)) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePhoto { c.photos.fromUri(uri, PHOTO_SIDE) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_meal_title, dayTitle(day, today())), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Muted(stringResource(R.string.add_meal_intro))
        OutlinedTextField(
            value = hint,
            onValueChange = { hint = it.take(200) },
            label = { Text(stringResource(R.string.add_meal_hint_label)) },
            placeholder = { Text(stringResource(R.string.add_meal_hint_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else askCamera.launch(Manifest.permission.CAMERA)
            }, enabled = !busy) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_camera))
            }
            FilledTonalButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_gallery))
            }
        }
        photo?.let { p ->
            Image(
                p.asImageBitmap(), contentDescription = stringResource(R.string.add_meal_photo), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Muted(stringResource(R.string.add_meal_looking))
                }
            } else if (!estimated) {
                TextButton(onClick = ::estimate) { Text(stringResource(R.string.add_meal_estimate_again)) }
            }
        }
        if (estimated) EstimateCard(drafts, confidence, note, meal, { meal = it }, onRetry = ::estimate, onSave = {
            scope.runAction(snackbar) {
                // Validate everything first so a bad quantity does not leave half the plate saved.
                drafts.forEach { d ->
                    require(d.name.isNotBlank()) { res.getString(R.string.add_meal_component_needs_name) }
                    requireNotNull(d.grams) { res.getString(R.string.add_meal_check_grams_of, d.name) }
                }
                drafts.forEach { d ->
                    val food = c.foods.saveEstimatedDish(d.name, d.dish.nutrients)
                    c.log.logFood(day, meal, food.id, d.grams!!)
                }
                val total = kcal(drafts.sumOf { it.dish.nutrients.scaledTo(it.grams!!).kcal })
                snackbar.showSnackbar(res.getString(R.string.add_meal_added, res.getString(meal.labelRes()).lowercase(), total))
                nav.popBackStack()
            }
        })
    }
}

@Composable
private fun EstimateCard(
    drafts: MutableList<DishDraft>,
    confidence: String?,
    note: String?,
    meal: MealType,
    onMeal: (MealType) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    SectionCard {
        val total = drafts.sumOf { d -> d.grams?.let { d.dish.nutrients.scaledTo(it).kcal } ?: 0.0 }
        SectionTitle(stringResource(R.string.add_meal_about_kcal, kcal(total)))
        val confidenceText = confidenceRes(confidence)?.let { stringResource(it) }
        Muted(listOfNotNull(confidenceText, note).joinToString(" · ").ifEmpty { stringResource(R.string.add_meal_check_grams) })
        drafts.forEachIndexed { index, d ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(d.name, { drafts[index] = d.copy(name = it.take(80)) }, label = { Text(stringResource(R.string.add_meal_component)) }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { drafts.removeAt(index) }) { Icon(Icons.Filled.Close, stringResource(R.string.add_remove)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(d.gramsText, { drafts[index] = d.copy(gramsText = it) }, stringResource(R.string.add_amount), Modifier.width(140.dp), suffix = "g")
                Spacer(Modifier.width(12.dp))
                val n = d.dish.nutrients
                Muted((d.grams?.let { "${kcal(n.scaledTo(it).kcal)} kcal · " } ?: "") + "${kcal(n.kcal)} kcal/100 g")
            }
        }
        Spacer(Modifier.size(8.dp))
        MealSelector(meal, onMeal)
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = drafts.isNotEmpty()) { Text(stringResource(R.string.add_save)) }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.add_meal_estimate_again)) }
        }
    }
}

/** The model answers with the Spanish words the prompt asks for, whatever the app's language. */
@StringRes
private fun confidenceRes(confidence: String?): Int? = when (confidence) {
    "alta" -> R.string.add_meal_confidence_high
    "media" -> R.string.add_meal_confidence_medium
    "baja" -> R.string.add_meal_confidence_low
    else -> null
}
