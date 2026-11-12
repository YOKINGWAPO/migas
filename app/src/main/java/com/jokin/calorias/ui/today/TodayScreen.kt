package com.jokin.calorias.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.HealthWrite
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.domain.BirthDate
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.domain.PetStage
import com.jokin.calorias.domain.RewardPicker
import com.jokin.calorias.domain.StreakCalculator
import com.jokin.calorias.domain.StreakResult
import com.jokin.calorias.domain.StreakReward
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.add.LocalAddSheet
import com.jokin.calorias.ui.calendar.OPEN_DAY_KEY
import com.jokin.calorias.ui.common.LocalAppScope
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.SectionCard
import com.jokin.calorias.ui.common.SectionTitle
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.appLocale
import com.jokin.calorias.ui.common.appViewModel
import com.jokin.calorias.ui.common.dayTitle
import com.jokin.calorias.ui.common.formatDay
import com.jokin.calorias.ui.common.runAction
import com.jokin.calorias.ui.common.today
import com.jokin.calorias.ui.mascot.Mascot
import com.jokin.calorias.ui.mascot.poseFor
import com.jokin.calorias.ui.theme.LocalMarkColor
import com.jokin.calorias.ui.theme.OverColor
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavController) {
    val vm = appViewModel { TodayViewModel(it) }
    val c = appContainer()
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val appScope = LocalAppScope.current
    val sheet = LocalAddSheet.current
    val scope = rememberCoroutineScope()

    val day by vm.day.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val water by vm.waterMl.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val energy by vm.energy.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val lastLogged by vm.lastLoggedDay.collectAsStateWithLifecycle()
    val proposals by vm.proposals.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val bodyMeasures by vm.bodyMeasures.collectAsStateWithLifecycle()
    val bodyPhotos by vm.bodyPhotos.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val burnReview by vm.burnReview.collectAsStateWithLifecycle()
    val cheer by vm.cheer.collectAsStateWithLifecycle()
    val newTier by vm.newTier.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    // The + in the bottom bar adds to the day open here, and the sheet is sucked into this day's ring.
    DisposableEffect(day) {
        sheet.todayDay = day
        onDispose {
            sheet.todayDay = null
            sheet.ringTarget = null
        }
    }

    // A cheer plays once: the id seen survives leaving the screen (saved state), the cheer itself lives in the view model.
    var seenCheer by rememberSaveable { mutableIntStateOf(cheer?.id ?: 0) }
    val playing = cheer?.takeIf { it.id != seenCheer }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(playing?.id) {
        val id = playing?.id ?: return@LaunchedEffect
        cheering = true
        delay(CHEER_MS)
        cheering = false
        seenCheer = id
    }

    // The calendar asks to open a day by leaving it in this entry's saved state.
    val openDay = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow<Long?>(OPEN_DAY_KEY, null)?.collectAsStateWithLifecycle()
    LaunchedEffect(openDay?.value) {
        openDay?.value?.let { vm.setDay(it); nav.currentBackStackEntry?.savedStateHandle?.set<Long?>(OPEN_DAY_KEY, null) }
    }

    var expandedMeals by rememberSaveable { mutableStateOf("") }
    val expanded = expandedMeals.split(',').mapNotNull { n -> MealType.entries.firstOrNull { it.name == n } }.toSet()
    var details by remember { mutableStateOf(false) }
    var streakSheet by remember { mutableStateOf(false) }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    var askComboFor by remember { mutableStateOf<MealType?>(null) }

    val eaten = Nutrients.sum(entries.map { it.food.forGrams(it.entry.grams) })
    val today = today()

    val swipeOffset = remember { Animatable(0f) }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    var widthPx by remember { mutableIntStateOf(0) }
    val goToDay = { target: Long ->
        scope.launch {
            vm.setDay(target)
            swipeOffset.snapTo((if (target > day) 1f else -1f) * widthPx * SLIDE_IN_FRACTION)
            swipeOffset.animateTo(0f)
        }
        Unit
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx = it.width }
                .graphicsLayer { translationX = swipeOffset.value }
                .daySwipe(day, today, swipeOffset, swipeThreshold, scope, vm::setDay),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DayHeader(day, today, streak, lastLogged, settings.mascot, cheering, playing?.id,
                    onToday = { if (day != today) goToDay(today) },
                    onMascot = { streakSheet = true },
                )
            }
            item { WeekStrip(week, day, today, onPick = { if (it != day) goToDay(it) }) }
            goal?.birthEpochDay?.takeIf { day == today && BirthDate.isBirthday(it, today) }?.let { birth ->
                item { BirthdayCard(BirthDate.ageOn(birth, today)) }
            }
            newTier?.let { tier ->
                item { NewRewardsCard(tier, streak.current, settings.mascot, onDone = { scope.runAction(snackbar) { vm.celebrateTier(tier) } }) }
            }
            burnReview?.takeIf { day == today }?.let { r ->
                item {
                    BurnReviewCard(r,
                        onAccept = { scope.runAction(snackbar, resources.getString(R.string.today_goal_adjusted)) { vm.answerBurnReview(accept = true) } },
                        onLater = { scope.runAction(snackbar) { vm.answerBurnReview(accept = false) } },
                    )
                }
            }
            proposals.firstOrNull()?.let { p ->
                item {
                    ComboProposalCard(p, onAccept = { name ->
                        scope.runAction(snackbar, resources.getString(R.string.today_combo_saved, name)) {
                            c.combos.save(0, name, "🍽️", p.foods.map { it.id to (p.typicalGrams[it.id] ?: 100.0) })
                        }
                    }, onDismiss = { scope.runAction(snackbar) { c.combos.dismiss(p.suggestion.signature) } })
                }
            }
            item {
                when (val e = energy) {
                    EnergyState.Loading -> SectionCard { Muted(stringResource(R.string.today_calculating)) }
                    EnergyState.NoGoal -> SectionCard {
                        SectionTitle(stringResource(R.string.today_setup_goal_title))
                        Muted(stringResource(R.string.today_setup_goal_text))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { nav.navigate(Routes.GOAL) }) { Text(stringResource(R.string.today_start)) }
                    }
                    is EnergyState.Failed -> SectionCard {
                        SectionTitle(stringResource(R.string.today_health_failed))
                        Muted(e.message)
                        TextButton(onClick = vm::refresh) { Text(stringResource(R.string.today_retry)) }
                    }
                    is EnergyState.Ready -> EnergyHero(e, eaten, onDetails = { details = true }, onRingPlaced = { center -> sheet.ringTarget = center?.let { day to it } })
                }
            }
            item {
                MealsCard(
                    entries = entries,
                    expanded = expanded,
                    highlighted = playing?.entryIds.orEmpty(),
                    onToggle = { meal -> expandedMeals = (if (meal in expanded) expanded - meal else expanded + meal).joinToString(",") { it.name } },
                    onAdd = { meal, origin -> sheet.open(day, meal, origin) },
                    onOpen = { e -> nav.navigate(Routes.log(e.food.id, day, e.entry.meal, e.entry.id)) },
                    onDelete = { e ->
                        scope.runAction(snackbar) {
                            c.log.delete(e.entry)
                            // The offer to undo outlives this screen if the user leaves meanwhile.
                            appScope.runAction(snackbar) {
                                val r = snackbar.showSnackbar(
                                    resources.getString(R.string.today_deleted, e.food.name),
                                    actionLabel = resources.getString(R.string.today_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (r == SnackbarResult.ActionPerformed) c.log.restore(e.entry)
                            }
                        }
                    },
                    onCopyYesterday = { meal ->
                        scope.runAction(snackbar) {
                            val n = c.log.copyMeal(day - 1, meal, day, meal)
                            snackbar.showSnackbar(
                                if (n == 0) resources.getString(R.string.today_nothing_yesterday, resources.getString(meal.labelRes()).lowercase())
                                else resources.getQuantityString(R.plurals.today_copied_from_yesterday, n, n)
                            )
                        }
                    },
                    onSaveCombo = { askComboFor = it },
                )
            }
            item {
                WaterRow(water, settings,
                    onAdd = { ml ->
                        scope.runAction(snackbar) {
                            when (val r = c.log.addWater(day, ml)) {
                                HealthWrite.Written, HealthWrite.NotAllowed -> Unit
                                is HealthWrite.Failed -> snackbar.showSnackbar(resources.getString(R.string.today_water_health_failed, r.reason))
                            }
                        }
                    },
                    onUndo = { scope.runAction(snackbar) { c.log.undoLastWater(day) } },
                )
            }
            if (exercises.isNotEmpty()) {
                item { ExerciseCard(exercises, onAdd = { nav.navigate(Routes.exercise(day)) }, onOpen = { nav.navigate(Routes.exercise(day, it.id)) }) }
            }
            if (bodyMeasures.isNotEmpty() || bodyPhotos.isNotEmpty()) {
                item { BodyCard(bodyMeasures, bodyPhotos.size, onOpen = { nav.navigate(Routes.body(day)) }) }
            }
            val noteText = note?.text.orEmpty()
            if (noteText.isNotBlank() || noteOpen) {
                item { NoteCard(noteText) { text -> scope.runAction(snackbar, resources.getString(R.string.today_note_saved)) { c.log.setNote(day, text) } } }
            }
            item {
                MoreForTheDay(
                    exercise = exercises.isEmpty(),
                    body = bodyMeasures.isEmpty() && bodyPhotos.isEmpty(),
                    note = noteText.isBlank() && !noteOpen,
                    onExercise = { nav.navigate(Routes.exercise(day)) },
                    onBody = { nav.navigate(Routes.body(day)) },
                    onNote = { noteOpen = true },
                )
            }
        }
    }

    (energy as? EnergyState.Ready)?.takeIf { details }?.let { e ->
        DayDetailsSheet(e, eaten, onRefresh = vm::refresh, onDismiss = { details = false })
    }
    if (streakSheet) StreakSheet(streak, lastLogged, today, settings.mascot, onDismiss = { streakSheet = false })
    askComboFor?.let { meal ->
        NameDialog(stringResource(R.string.today_combo_name), initial = "", onDismiss = { askComboFor = null }) { name ->
            askComboFor = null
            scope.runAction(snackbar, resources.getString(R.string.today_combo_saved, name)) { c.combos.createFromMeal(day, meal, name) }
        }
    }
}

/** Day name and date on the left; the mascot with the streak on the right (tap it for the streak). */
@Composable
private fun DayHeader(
    day: Long,
    today: Long,
    streak: StreakResult,
    lastLogged: Long?,
    kind: MascotKind,
    cheering: Boolean,
    cheerId: Int?,
    onToday: () -> Unit,
    onMascot: () -> Unit,
) {
    val reward = RewardPicker.forDay(streak.current, today)
    val mood = StreakCalculator.petMood(lastLogged, today, LocalTime.now().hour)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToday)) {
            Text(dayTitle(day, today), style = MaterialTheme.typography.headlineMedium)
            Muted(formatDay(day, "EEEEdMMMM"))
        }
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onMascot)) {
            Mascot(kind, poseFor(mood, reward, cheering), reward, Modifier.size(64.dp), jumpKey = cheerId?.let { "cheer$it" })
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 2.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text("${streak.current}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

/** Monday to Sunday, each day a small ring of what was eaten against its own target. */
@Composable
private fun WeekStrip(week: List<WeekDay>, selected: Long, today: Long, onPick: (Long) -> Unit) {
    if (week.isEmpty()) return
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val marks = LocalMarkColor.current
    Row(Modifier.fillMaxWidth()) {
        week.forEachIndexed { i, d ->
            val future = d.epochDay > today
            val fraction = d.targetKcal?.takeIf { it > 0 }?.let { (d.eatenKcal / it).toFloat() } ?: 0f
            val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "weekday")
            val over = fraction > OVER_FRACTION
            val isSelected = d.epochDay == selected
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !future) { onPick(d.epochDay) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(30.dp)) {
                    val stroke = 3.5.dp.toPx()
                    drawCircle(track, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                    if (sweep > 0f) {
                        drawArc(
                            if (over) OverColor else marks.value, -90f, 360f * sweep, false, Offset(stroke / 2, stroke / 2),
                            Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, appLocale()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        future -> MaterialTheme.colorScheme.outlineVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The streak in full: the mascot big (tap it for another reward), best streak and protector. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreakSheet(streak: StreakResult, lastLogged: Long?, today: Long, kind: MascotKind, onDismiss: () -> Unit) {
    var shuffle by rememberSaveable { mutableIntStateOf(0) }
    val reward = RewardPicker.forDay(streak.current, today, shuffle)
    val mood = StreakCalculator.petMood(lastLogged, today, LocalTime.now().hour)
    val unlocked = RewardPicker.unlocked(streak.current).size
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot(
                kind, poseFor(mood, reward, cheering = false), reward,
                Modifier.size(168.dp).clickable(enabled = reward != null) { shuffle++ },
                jumpKey = shuffle.takeIf { it > 0 },
            )
            Text("${streak.current}", style = MaterialTheme.typography.displayMedium)
            Muted(pluralStringResource(R.plurals.today_streak_days, streak.current))
            Spacer(Modifier.height(12.dp))
            Text(
                reward?.let { stringResource(it.labelRes()) }
                    ?: pluralStringResource(R.plurals.today_first_reward_in, PetStage.CHICK.minStreak, PetStage.CHICK.minStreak),
                style = MaterialTheme.typography.titleMedium,
            )
            Muted(
                listOfNotNull(
                    stringResource(R.string.today_best_streak, streak.best),
                    stringResource(R.string.today_rewards_unlocked, unlocked, StreakReward.entries.size),
                    stringResource(R.string.today_protector_used).takeIf { streak.protectedDays.isNotEmpty() },
                ).joinToString(" · ")
            )
            if (reward != null) Muted(stringResource(R.string.today_tap_mascot))
        }
    }
}

/**
 * Horizontal swipe on the day: the content follows the finger and, past [threshold], moves to the
 * next or previous day, which slides in from that side. Days after today resist instead.
 */
private fun Modifier.daySwipe(
    day: Long,
    today: Long,
    offset: Animatable<Float, AnimationVector1D>,
    threshold: Float,
    scope: CoroutineScope,
    onDay: (Long) -> Unit,
): Modifier = pointerInput(day, today) {
    detectHorizontalDragGestures(
        onDragEnd = {
            scope.launch {
                val dx = offset.value
                val target = when {
                    dx <= -threshold && day < today -> day + 1
                    dx >= threshold -> day - 1
                    else -> null
                }
                if (target != null) {
                    onDay(target)
                    offset.snapTo((if (dx < 0) 1f else -1f) * size.width * SLIDE_IN_FRACTION)
                }
                offset.animateTo(0f)
            }
        },
        onDragCancel = { scope.launch { offset.animateTo(0f) } },
    ) { change, amount ->
        change.consume()
        val damped = if (day >= today && offset.value + amount < 0) amount * 0.3f else amount
        scope.launch { offset.snapTo(offset.value + damped) }
    }
}

private const val SLIDE_IN_FRACTION = 0.25f

/** How long the mascot smiles after something is logged: covers the row highlight. */
private const val CHEER_MS = 1_600L

/** A day more than 10 % over its target shows its ring in red, as Progress counts "in goal". */
private const val OVER_FRACTION = 1.1f
