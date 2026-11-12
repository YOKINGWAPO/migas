package com.jokin.calorias.ui.add

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.VoiceCandidate
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.VoiceLanguage
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.MealSelector
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appLocale
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.today
import com.jokin.calorias.ui.theme.WaterColor
import kotlinx.coroutines.CancellationException

/** One editable row of the confirmation card. */
private data class Draft(val candidate: VoiceCandidate, val gramsText: String)

@Composable
fun VoiceScreen(nav: NavController, day: Long, initialMeal: MealType) {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var transcript by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    var busy by remember { mutableStateOf(false) }
    var modeNote by remember { mutableStateOf<String?>(null) }
    var waterMl by remember { mutableIntStateOf(0) }
    val drafts = remember { mutableStateListOf<Draft>() }
    var interpretRequest by remember { mutableIntStateOf(0) }

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            transcript = text
            interpretRequest++
        }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, appLocale().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, res.getString(R.string.add_voice_prompt))
        try {
            recognizer.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            modeNote = res.getString(R.string.add_voice_no_recognizer)
        }
    }

    LaunchedEffect(Unit) { if (transcript.isBlank()) listen() }
    LaunchedEffect(interpretRequest) {
        if (interpretRequest == 0 || transcript.isBlank()) return@LaunchedEffect
        busy = true
        drafts.clear()
        try {
            val interpretation = c.voice.interpret(transcript, VoiceLanguage.of(appLocale().language))
            interpretation.parsed.meal?.let { meal = it }
            waterMl = interpretation.parsed.waterMl
            modeNote = interpretation.aiProblem
                ?: res.getString(if (interpretation.usedAi) R.string.add_voice_mode_ai else R.string.add_voice_mode_basic)
            c.voice.resolve(interpretation.parsed, today()).forEach { cand ->
                drafts += Draft(cand, (cand as? VoiceCandidate.FoodMatch)?.grams?.let { "%.0f".format(it) } ?: "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            modeNote = res.getString(R.string.add_error_prefix, e.message)
        } finally {
            busy = false
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.add_voice_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Muted(stringResource(R.string.add_voice_example))
                }
                LargeFloatingActionButton(onClick = ::listen) { Icon(Icons.Filled.Mic, stringResource(R.string.add_voice_speak), Modifier.size(36.dp)) }
            }
        }
        item {
            OutlinedTextField(
                value = transcript,
                onValueChange = { transcript = it.take(500) },
                label = { Text(stringResource(R.string.add_voice_transcript)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { interpretRequest++ }, enabled = transcript.isNotBlank() && !busy) { Text(stringResource(R.string.add_voice_reinterpret)) }
        }
        item { MealSelector(meal, { meal = it }) }
        if (busy) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Muted(stringResource(R.string.add_voice_interpreting)) } }
        modeNote?.let { item { Muted(it) } }
        itemsIndexed(drafts) { index, d ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        when (val cand = d.candidate) {
                            is VoiceCandidate.FoodMatch -> {
                                Text(cand.food.name, style = MaterialTheme.typography.titleSmall)
                                val g = parseDecimal(d.gramsText) ?: 0.0
                                val said = stringResource(R.string.add_voice_you_said, cand.item.name, kcal(cand.food.forGrams(g).kcal))
                                Muted(if (cand.food.source == FoodSource.ESTIMATED) "$said · ${stringResource(R.string.add_voice_estimated)}" else said)
                            }
                            is VoiceCandidate.ComboMatch -> {
                                Text("${cand.combo.combo.icon} ${cand.combo.combo.name}", style = MaterialTheme.typography.titleSmall)
                                Muted(stringResource(R.string.add_voice_combo, kcal(cand.combo.items.sumOf { it.food.forGrams(it.item.grams).kcal })))
                            }
                            is VoiceCandidate.Unresolved -> {
                                Text(stringResource(R.string.add_quoted, cand.item.name), style = MaterialTheme.typography.titleSmall)
                                Muted(cand.reason)
                                TextButton(onClick = { nav.navigate(Routes.search(day, meal)) }) { Text(stringResource(R.string.add_search_manually)) }
                            }
                        }
                    }
                    IconButton(onClick = { drafts.removeAt(index) }) { Icon(Icons.Filled.Close, stringResource(R.string.add_remove)) }
                }
                if (d.candidate is VoiceCandidate.FoodMatch) {
                    NumberField(d.gramsText, { drafts[index] = d.copy(gramsText = it) }, stringResource(R.string.add_amount), suffix = "g")
                }
            }
        }
        if (waterMl > 0) item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = WaterColor)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_voice_water, waterMl), Modifier.weight(1f))
                    IconButton(onClick = { waterMl = 0 }) { Icon(Icons.Filled.Close, stringResource(R.string.add_voice_remove_water)) }
                }
            }
        }
        val savable = drafts.any { it.candidate !is VoiceCandidate.Unresolved } || waterMl > 0
        if (savable && !busy) item {
            val savedMessage = stringResource(R.string.add_saved_to_meal, stringResource(meal.labelRes()).lowercase())
            Button(
                onClick = {
                    scope.runAction(snackbar, savedMessage) {
                        // Validate everything first so a bad quantity does not leave half the phrase saved.
                        drafts.forEach { d ->
                            val cand = d.candidate
                            if (cand is VoiceCandidate.FoodMatch && (parseDecimal(d.gramsText) ?: 0.0) <= 0) {
                                error(res.getString(R.string.add_check_amount_of, cand.food.name))
                            }
                        }
                        drafts.forEach { d ->
                            when (val cand = d.candidate) {
                                is VoiceCandidate.FoodMatch -> {
                                    c.log.logFood(day, meal, cand.food.id, parseDecimal(d.gramsText)!!)
                                }
                                is VoiceCandidate.ComboMatch -> c.log.logCombo(day, meal, cand.combo.combo.id)
                                is VoiceCandidate.Unresolved -> Unit
                            }
                        }
                        if (waterMl > 0) c.log.addWater(day, waterMl)
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.add_save)) }
        }
        if (drafts.isEmpty() && !busy && transcript.isNotBlank() && waterMl == 0) item {
            FilledTonalButton(onClick = { nav.navigate(Routes.search(day, meal)) }) { Text(stringResource(R.string.add_voice_nothing_understood)) }
        }
    }
}
