package com.jokin.calorias.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.db.GoalEntity
import com.jokin.calorias.domain.BirthDate
import com.jokin.calorias.domain.EnergyMath
import com.jokin.calorias.domain.GoalPace
import com.jokin.calorias.domain.Profile
import com.jokin.calorias.domain.Sex
import com.jokin.calorias.domain.TargetMode
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.formatDay
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.today
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

@Composable
fun GoalScreen(nav: NavController) {
    val c = appContainer()
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(false) }
    var isNew by rememberSaveable { mutableStateOf(true) }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    var legacyAge by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingBirth by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var pace by rememberSaveable { mutableStateOf(GoalPace.LOSE_GENTLE) }
    var mode by rememberSaveable { mutableStateOf(TargetMode.DYNAMIC) }
    var proteinPerKg by rememberSaveable { mutableStateOf(decimal(1.6)) }
    var fatPercent by rememberSaveable { mutableStateOf("25") }
    var targetWeight by rememberSaveable { mutableStateOf("") }
    var scaleNote by rememberSaveable { mutableStateOf<String?>(null) }
    var garminResting by rememberSaveable { mutableStateOf("") }
    var burnCorrection by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        c.energy.goal()?.let { g ->
            isNew = false
            weight = decimal(g.weightKg); height = decimal(g.heightCm, 0); birth = g.birthEpochDay
            if (g.birthEpochDay == null) legacyAge = g.ageYears
            sex = g.sex; pace = g.pace; mode = g.mode
            proteinPerKg = decimal(g.proteinPerKg); fatPercent = g.fatPercent.toString()
            targetWeight = g.targetWeightKg?.let { decimal(it) }.orEmpty()
        }
        garminResting = c.energy.garminRestingKcal()?.toString().orEmpty()
        burnCorrection = c.energy.burnCorrectionKcal()
        // The scale (via Health Connect) wins over a typed weight when it has a recent value.
        try {
            c.energy.latestScaleWeight(today())?.let {
                weight = decimal(it); scaleNote = resources.getString(R.string.mine_goal_scale_weight, decimal(it))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            scaleNote = resources.getString(R.string.mine_goal_scale_error, e.message.toString())
        }
        loaded = true
    }

    val w = parseDecimal(weight); val h = parseDecimal(height); val a = birth?.let { BirthDate.ageOn(it, today()) }
    val bmr = if (w != null && h != null && a != null) EnergyMath.mifflinStJeor(Profile(w, h, a, sex)) else null
    val tw = parseDecimal(targetWeight)?.takeIf { it > 0 }
    val reference = if (w != null && h != null) EnergyMath.referenceWeightKg(w, h, tw) else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (isNew) R.string.mine_goal_title_new else R.string.mine_goal_title_edit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SectionCard {
            SectionTitle(stringResource(R.string.mine_goal_you))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(weight, { weight = it }, stringResource(R.string.mine_weight), Modifier.weight(1f), suffix = "kg")
                NumberField(height, { height = it }, stringResource(R.string.mine_height), Modifier.weight(1f), suffix = "cm")
            }
            OutlinedButton(onClick = { pickingBirth = true }, modifier = Modifier.fillMaxWidth()) {
                Text(birth?.let { stringResource(R.string.mine_goal_birth, formatDay(it, "dMMMMyyyy")) } ?: stringResource(R.string.mine_goal_set_birth))
            }
            val oldAge = legacyAge
            when {
                a != null -> Muted(pluralStringResource(R.plurals.mine_goal_age, a, a))
                oldAge != null -> Muted(pluralStringResource(R.plurals.mine_goal_legacy_age, oldAge, oldAge))
            }
            scaleNote?.let { Muted(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { s -> FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(stringResource(s.labelRes())) }) }
            }
            bmr?.let { Muted(stringResource(R.string.mine_goal_bmr, kcal(it))) }
            NumberField(garminResting, { garminResting = it }, stringResource(R.string.mine_goal_watch_resting), Modifier.fillMaxWidth(),
                suffix = stringResource(R.string.mine_kcal_per_day), decimals = false)
            Muted(stringResource(R.string.mine_goal_watch_resting_hint))
            if (burnCorrection != 0) {
                Text(stringResource(R.string.mine_goal_burn_correction, "${if (burnCorrection > 0) "+" else ""}$burnCorrection"))
                Muted(stringResource(R.string.mine_goal_burn_correction_hint))
                val removed = stringResource(R.string.mine_goal_correction_removed)
                TextButton(onClick = {
                    scope.runAction(snackbar, removed) { c.energy.clearBurnCorrection(); burnCorrection = 0 }
                }) { Text(stringResource(R.string.mine_goal_remove_correction)) }
            }
        }
        SectionCard {
            SectionTitle(stringResource(R.string.mine_goal_goal))
            GoalPace.entries.forEach { p ->
                Row(Modifier.fillMaxWidth().selectable(selected = pace == p, onClick = { pace = p }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = pace == p, onClick = { pace = p })
                    Column {
                        Text(stringResource(p.labelRes()))
                        Muted(
                            if (p.kgPerWeek == 0.0) stringResource(R.string.mine_goal_pace_no_change)
                            else stringResource(R.string.mine_goal_pace_detail, "${if (p.dailyKcal > 0) "+" else ""}${p.dailyKcal}", decimal(kotlin.math.abs(p.kgPerWeek), 2))
                        )
                    }
                }
            }
            NumberField(targetWeight, { targetWeight = it }, stringResource(R.string.mine_goal_target_weight), Modifier.fillMaxWidth(), suffix = "kg")
            Muted(stringResource(R.string.mine_goal_deficit_limit, EnergyMath.minTargetKcal(sex)))
        }
        SectionCard {
            SectionTitle(stringResource(R.string.mine_goal_how))
            TargetMode.entries.forEach { m ->
                Row(Modifier.fillMaxWidth().selectable(selected = mode == m, onClick = { mode = m }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == m, onClick = { mode = m })
                    Text(stringResource(m.labelRes()))
                }
            }
        }
        SectionCard {
            SectionTitle(stringResource(R.string.mine_goal_macros))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(proteinPerKg, { proteinPerKg = it }, stringResource(R.string.mine_protein), Modifier.weight(1f), suffix = "g/kg")
                NumberField(fatPercent, { fatPercent = it }, stringResource(R.string.mine_fat), Modifier.weight(1f), suffix = "% kcal", decimals = false)
            }
            Muted(stringResource(R.string.mine_goal_macros_hint, reference?.let { stringResource(R.string.mine_goal_macros_reference, decimal(it)) } ?: ""))
        }
        val saved = stringResource(R.string.mine_goal_saved)
        Button(
            onClick = {
                scope.runAction(snackbar, saved) {
                    val goal = GoalEntity(
                        weightKg = w ?: error(resources.getString(R.string.mine_goal_missing_weight)),
                        heightCm = h ?: error(resources.getString(R.string.mine_goal_missing_height)),
                        ageYears = a ?: error(resources.getString(R.string.mine_goal_missing_birth)),
                        birthEpochDay = birth,
                        sex = sex, pace = pace, mode = mode,
                        proteinPerKg = parseDecimal(proteinPerKg) ?: error(resources.getString(R.string.mine_goal_check_protein)),
                        fatPercent = fatPercent.toIntOrNull() ?: error(resources.getString(R.string.mine_goal_check_fat)),
                        targetWeightKg = tw,
                    )
                    val resting = garminResting.trim().takeIf { it.isNotEmpty() }?.let {
                        it.toIntOrNull() ?: error(resources.getString(R.string.mine_goal_check_resting))
                    }
                    // Resting first: saving the goal is what makes Hoy recompute.
                    c.energy.saveGarminRestingKcal(resting)
                    c.energy.saveGoal(goal)
                    nav.popBackStack()
                }
            },
            enabled = bmr != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.mine_save)) }
    }

    if (pickingBirth) {
        BirthDateDialog(birth, onPick = { birth = it; pickingBirth = false }, onDismiss = { pickingBirth = false })
    }
    // Closing the dialog hands focus back to the first text field, which pops the keyboard up for nothing.
    LaunchedEffect(pickingBirth) { if (!pickingBirth) focusManager.clearFocus() }
}

private const val MILLIS_PER_DAY = 86_400_000L

/** The Material picker works in UTC midnight millis; converting through epoch days keeps the chosen date exact. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateDialog(current: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val today = today()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current?.let { it * MILLIS_PER_DAY },
        yearRange = LocalDate.ofEpochDay(today).year - BirthDate.AGE_RANGE.last..LocalDate.ofEpochDay(today).year,
        initialDisplayMode = DisplayMode.Input,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.mine_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mine_cancel)) } },
    ) {
        DatePicker(state, title = { Text(stringResource(R.string.mine_goal_birth_date), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
