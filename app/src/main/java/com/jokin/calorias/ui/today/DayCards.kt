package com.jokin.calorias.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jokin.calorias.R
import com.jokin.calorias.data.ComboProposal
import com.jokin.calorias.data.db.BodyMeasureEntity
import com.jokin.calorias.data.db.ExerciseEntity
import com.jokin.calorias.domain.BurnReview
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.domain.PetStage
import com.jokin.calorias.domain.StreakReward
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.decimal
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.kilos
import com.jokin.calorias.ui.mascot.Mascot
import com.jokin.calorias.ui.mascot.Pose

@Composable
fun BirthdayCard(age: Int) {
    SectionCard {
        Text(stringResource(R.string.today_happy_birthday), style = MaterialTheme.typography.titleLarge)
        Muted(pluralStringResource(R.plurals.today_birthday_age, age, age))
    }
}

@Composable
fun NewRewardsCard(tier: PetStage, streak: Int, kind: MascotKind, onDone: () -> Unit) {
    val rewards = StreakReward.entries.filter { it.tier == tier }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(kind, Pose.HAPPY, rewards.first(), Modifier.size(96.dp), jumpKey = tier)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.today_new_rewards))
                Muted(pluralStringResource(R.plurals.today_new_rewards_text, streak, streak))
            }
        }
        Text(rewards.map { stringResource(it.labelRes()) }.joinToString(" · "), modifier = Modifier.padding(vertical = 8.dp))
        FilledTonalButton(onClick = onDone) { Text(stringResource(R.string.today_lets_go)) }
    }
}

@Composable
fun BurnReviewCard(r: BurnReview, onAccept: () -> Unit, onLater: () -> Unit) {
    SectionCard {
        SectionTitle(stringResource(R.string.today_burn_review_title))
        Text(stringResource(R.string.today_burn_review_real, kcal(r.realTdee), kcal(r.garminAvg)))
        Muted(stringResource(R.string.today_burn_review_explain))
        Row(Modifier.padding(top = 6.dp)) {
            FilledTonalButton(onClick = onAccept) { Text(stringResource(R.string.today_burn_review_adjust, signedKcal(r.correctionKcal))) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onLater) { Text(stringResource(R.string.today_not_now)) }
        }
    }
}

fun signedKcal(v: Int): String = if (v > 0) "+$v" else "$v"

@Composable
fun ComboProposalCard(p: ComboProposal, onAccept: (String) -> Unit, onDismiss: () -> Unit) {
    var askName by remember { mutableStateOf(false) }
    SectionCard {
        SectionTitle(stringResource(R.string.today_combo_question))
        Muted(pluralStringResource(R.plurals.today_combo_eaten_together, p.suggestion.occurrences, p.suggestion.occurrences))
        Text(p.foods.joinToString(" + ") { it.name }, modifier = Modifier.padding(vertical = 6.dp))
        Row {
            FilledTonalButton(onClick = { askName = true }) { Text(stringResource(R.string.today_save_combo)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_no_thanks)) }
        }
    }
    if (askName) {
        NameDialog(stringResource(R.string.today_combo_name), p.foods.joinToString(stringResource(R.string.today_and)) { it.name }.take(60), { askName = false }) {
            askName = false
            onAccept(it)
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text(stringResource(R.string.today_name)) }) },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.today_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_cancel)) } },
    )
}

@Composable
fun ExerciseCard(sets: List<ExerciseEntity>, onAdd: () -> Unit, onOpen: (ExerciseEntity) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.today_exercise))
                val setCount = sets.sumOf { it.sets }
                val repCount = sets.sumOf { it.sets * it.reps }
                Muted(pluralStringResource(R.plurals.today_exercise_sets, setCount, setCount) + " · " + pluralStringResource(R.plurals.today_exercise_reps, repCount, repCount))
            }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, stringResource(R.string.today_log_exercise)) }
        }
        sets.forEach { s ->
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Row(Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.sets}×${s.reps}" + (s.weightKg?.let { " · ${kilos(it)} kg" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun BodyCard(measures: List<BodyMeasureEntity>, photoCount: Int, onOpen: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.today_measures))
                val photos = if (photoCount == 0) null else pluralStringResource(R.plurals.today_photos, photoCount, photoCount)
                Muted(
                    listOfNotNull(
                        measures.sortedBy { it.part.ordinal }.map { "${stringResource(it.part.labelRes())} ${decimal(it.cm)}" }.joinToString(" · ").ifEmpty { null },
                        photos,
                    ).joinToString(" · ")
                )
            }
            IconButton(onClick = onOpen) { Icon(Icons.Filled.Add, stringResource(R.string.today_log_measures)) }
        }
    }
}

@Composable
fun NoteCard(current: String, onSave: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    SectionCard {
        SectionTitle(stringResource(R.string.today_day_note))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            placeholder = { Text(stringResource(R.string.today_note_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (text != current) {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.today_save_note)) }
        }
    }
}

/** What else can go into the day, for the parts that have nothing yet and so are not on screen. */
@Composable
fun MoreForTheDay(exercise: Boolean, body: Boolean, note: Boolean, onExercise: () -> Unit, onBody: () -> Unit, onNote: () -> Unit) {
    if (!exercise && !body && !note) return
    Column(Modifier.padding(top = 4.dp)) {
        Muted(stringResource(R.string.today_add_to_day))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (exercise) MoreChip(stringResource(R.string.today_exercise), Icons.Outlined.FitnessCenter, onExercise)
            if (body) MoreChip(stringResource(R.string.today_measures), Icons.Outlined.Straighten, onBody)
            if (note) MoreChip(stringResource(R.string.today_note), Icons.Outlined.EditNote, onNote)
        }
    }
}

@Composable
private fun MoreChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(18.dp)) })
}
