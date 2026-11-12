package com.jokin.calorias.ui.exercise

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.ExerciseAiProblem
import com.jokin.calorias.domain.ExerciseParser
import com.jokin.calorias.domain.ExerciseRecord
import com.jokin.calorias.domain.LoggedSet
import com.jokin.calorias.domain.ParsedExercise
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SaveButton
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appLocale
import com.jokin.calorias.ui.common.dayTitle
import com.jokin.calorias.ui.common.kilos
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.shortDate
import com.jokin.calorias.ui.common.today
import kotlinx.coroutines.CancellationException

/** One editable row; everything stays text until saved so half-typed numbers are allowed. */
private data class ExerciseDraft(val name: String = "", val sets: String = "", val reps: String = "", val weight: String = "") {
    constructor(p: ParsedExercise) : this(p.name, "${p.sets}", "${p.reps}", p.weightKg?.let(::kilos).orEmpty())
}

/** Returns the set ready to store, or why it cannot be stored. */
private fun ExerciseDraft.validated(resources: Resources): Result<ParsedExercise> {
    val label = name.trim().ifEmpty { return Result.failure(IllegalArgumentException(resources.getString(R.string.mine_exercise_missing_name))) }
    val s = sets.trim().toIntOrNull()?.takeIf { it in 1..ExerciseParser.MAX_SETS }
        ?: return Result.failure(IllegalArgumentException(resources.getString(R.string.mine_exercise_check_sets, label, ExerciseParser.MAX_SETS)))
    val r = reps.trim().toIntOrNull()?.takeIf { it in 1..ExerciseParser.MAX_REPS }
        ?: return Result.failure(IllegalArgumentException(resources.getString(R.string.mine_exercise_check_reps, label, ExerciseParser.MAX_REPS)))
    val w = if (weight.isBlank()) null else parseDecimal(weight)?.takeIf { it > 0 && it <= ExerciseParser.MAX_WEIGHT_KG }
        ?: return Result.failure(IllegalArgumentException(resources.getString(R.string.mine_exercise_check_weight, label)))
    return Result.success(ParsedExercise(label.take(60), s, r, w))
}

@Composable
fun ExerciseScreen(nav: NavController, day: Long, id: Long) {
    if (id == 0L) NewExercisesScreen(nav, day) else EditExerciseScreen(nav, id)
}

@Composable
private fun NewExercisesScreen(nav: NavController, day: Long) {
    val c = appContainer()
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var transcript by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var modeNote by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateListOf<ExerciseDraft>() }
    var interpretRequest by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { recent = c.exercises.recentNames() }

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
            .putExtra(RecognizerIntent.EXTRA_PROMPT, resources.getString(R.string.mine_exercise_prompt))
        try {
            recognizer.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            modeNote = resources.getString(R.string.mine_exercise_no_recognizer)
        }
    }

    LaunchedEffect(interpretRequest) {
        if (interpretRequest == 0 || transcript.isBlank()) return@LaunchedEffect
        busy = true
        try {
            val interpretation = c.exercises.interpret(transcript)
            drafts += interpretation.sets.map(::ExerciseDraft)
            val problem = interpretation.aiProblem
            modeNote = when {
                interpretation.sets.isEmpty() -> resources.getString(R.string.mine_exercise_not_understood)
                problem != null -> describe(resources, problem)
                interpretation.usedAi -> resources.getString(R.string.mine_exercise_used_ai)
                else -> resources.getString(R.string.mine_exercise_basic_mode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            modeNote = resources.getString(R.string.mine_exercise_error, e.message.toString())
        } finally {
            busy = false
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mine_exercise_title, dayTitle(day, today())), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Muted(stringResource(R.string.mine_exercise_examples))
                }
                LargeFloatingActionButton(onClick = ::listen) { Icon(Icons.Filled.Mic, stringResource(R.string.mine_exercise_speak), Modifier.size(36.dp)) }
            }
        }
        item {
            OutlinedTextField(
                value = transcript,
                onValueChange = { transcript = it.take(500) },
                label = { Text(stringResource(R.string.mine_exercise_transcript)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { interpretRequest++ }, enabled = transcript.isNotBlank() && !busy) {
                Text(stringResource(R.string.mine_exercise_interpret))
            }
        }
        if (busy) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Muted(stringResource(R.string.mine_exercise_interpreting))
            }
        }
        modeNote?.let { item { Muted(it) } }
        itemsIndexed(drafts) { index, d ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        d.name, { drafts[index] = d.copy(name = it.take(60)) }, label = { Text(stringResource(R.string.mine_exercise_name)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { drafts.removeAt(index) }) { Icon(Icons.Filled.Close, stringResource(R.string.mine_remove)) }
                }
                RecordHint(d.name, day)
                if (index == drafts.lastIndex && d.name.isBlank() && recent.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { n -> SuggestionChip(onClick = { drafts[index] = d.copy(name = n) }, label = { Text(n) }) }
                    }
                }
                SetFields(d) { drafts[index] = it }
            }
        }
        item {
            OutlinedButton(onClick = { drafts += ExerciseDraft() }) { Text(stringResource(R.string.mine_exercise_add_by_hand)) }
        }
        if (drafts.isNotEmpty() && !busy) item {
            SaveButton(
                text = stringResource(R.string.mine_save),
                onSave = {
                    // Validate everything first so one bad row does not leave half the list saved.
                    val sets = drafts.map { it.validated(resources).getOrThrow() }
                    c.exercises.add(day, sets)
                },
                onSaved = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun EditExerciseScreen(nav: NavController, id: Long) {
    val c = appContainer()
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<ExerciseDraft?>(null) }
    var day by remember { mutableLongStateOf(today()) }
    LaunchedEffect(id) {
        val e = c.exercises.byId(id) ?: return@LaunchedEffect
        draft = ExerciseDraft(ParsedExercise(e.name, e.sets, e.reps, e.weightKg))
        day = e.epochDay
    }
    val d = draft ?: return

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.mine_exercise_edit))
        OutlinedTextField(
            d.name, { draft = d.copy(name = it.take(60)) }, label = { Text(stringResource(R.string.mine_exercise_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        RecordHint(d.name, day)
        SetFields(d) { draft = it }
        SaveButton(
            text = stringResource(R.string.mine_save),
            onSave = {
                val p = d.validated(resources).getOrThrow()
                val e = c.exercises.byId(id) ?: error(resources.getString(R.string.mine_exercise_gone))
                c.exercises.update(e.copy(name = p.name, sets = p.sets, reps = p.reps, weightKg = p.weightKg))
            },
            onSaved = { nav.popBackStack() },
        )
        val deleted = stringResource(R.string.mine_deleted)
        FilledTonalButton(onClick = { scope.runAction(snackbar, deleted) { c.exercises.delete(id); nav.popBackStack() } }) {
            Text(stringResource(R.string.mine_delete))
        }
    }
}

/** Previous session and best set, so the user can aim a bit higher (progressive overload). */
@Composable
private fun RecordHint(name: String, day: Long) {
    val c = appContainer()
    val record by produceState<ExerciseRecord?>(null, name.trim().lowercase(), day) { value = c.exercises.record(name, day) }
    val r = record ?: return
    val best = r.best.weightKg?.let { "${kilos(it)} kg × ${r.best.reps}" } ?: pluralStringResource(R.plurals.mine_exercise_reps, r.best.reps, r.best.reps)
    Muted(
        listOfNotNull(
            r.last?.let { stringResource(R.string.mine_exercise_last_time, shortDate(it.epochDay), setText(it)) },
            stringResource(R.string.mine_exercise_best, best),
        ).joinToString(" · ")
    )
}

private fun describe(resources: Resources, problem: ExerciseAiProblem): String = when (problem) {
    is ExerciseAiProblem.Unreachable ->
        resources.getString(R.string.mine_exercise_ai_fallback, problem.detail ?: resources.getString(R.string.mine_exercise_ai_no_connection, problem.provider))
    ExerciseAiProblem.UnexpectedFormat -> resources.getString(R.string.mine_exercise_ai_bad_format)
}

private fun setText(s: LoggedSet) = "${s.sets}×${s.reps}" + (s.weightKg?.let { " · ${kilos(it)} kg" } ?: "")

@Composable
private fun SetFields(d: ExerciseDraft, onChange: (ExerciseDraft) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(d.sets, { onChange(d.copy(sets = it)) }, stringResource(R.string.mine_exercise_sets), Modifier.weight(1f), decimals = false)
        NumberField(d.reps, { onChange(d.copy(reps = it)) }, stringResource(R.string.mine_exercise_reps_field), Modifier.weight(1f), decimals = false)
        NumberField(d.weight, { onChange(d.copy(weight = it)) }, stringResource(R.string.mine_weight), Modifier.weight(1.2f), suffix = "kg")
    }
}
