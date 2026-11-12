package com.jokin.calorias.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jokin.calorias.AppContainer
import com.jokin.calorias.data.ComboProposal
import com.jokin.calorias.data.DayEnergy
import com.jokin.calorias.data.db.BodyMeasureEntity
import com.jokin.calorias.data.db.BodyPhotoEntity
import com.jokin.calorias.data.db.EntryWithFood
import com.jokin.calorias.data.db.ExerciseEntity
import com.jokin.calorias.data.db.GoalEntity
import com.jokin.calorias.data.db.NoteEntity
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.data.health.SleepDay
import com.jokin.calorias.data.health.Workout
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.domain.BurnReview
import com.jokin.calorias.domain.PetStage
import com.jokin.calorias.domain.StreakCalculator
import com.jokin.calorias.domain.StreakResult
import com.jokin.calorias.domain.Trends
import com.jokin.calorias.ui.common.today
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One day of the week strip: what was eaten and that day's target, null until it is known or in the future. */
data class WeekDay(val epochDay: Long, val eatenKcal: Double, val targetKcal: Int?)

/** Monday to Sunday of the week that contains [day]. */
fun weekOf(day: Long): LongRange {
    val monday = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
    return monday..monday + 6
}

/** Entries that appeared while the day was open: the mascot cheers and the rows light up, once per [id]. */
data class Cheer(val id: Int, val entryIds: Set<Long>)

sealed interface EnergyState {
    data object Loading : EnergyState
    data object NoGoal : EnergyState
    data class Ready(val energy: DayEnergy, val sleep: SleepDay?, val restingHr: Double?, val workouts: List<Workout>) : EnergyState
    data class Failed(val message: String) : EnergyState
}

/** Days of history the review reads; Trends.burnReview looks at the 28 finished days before today. */
private const val BURN_REVIEW_WINDOW = 28L

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val _day = MutableStateFlow(today())
    val day: StateFlow<Long> = _day.asStateFlow()

    private fun <T> StateFlow<Long>.perDay(initial: T, source: (Long) -> kotlinx.coroutines.flow.Flow<T>): StateFlow<T> =
        flatMapLatest(source).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val entries: StateFlow<List<EntryWithFood>> = day.perDay(emptyList()) { c.log.observeDay(it) }
    val waterMl: StateFlow<Int> = day.perDay(0) { c.log.observeWater(it) }
    val note: StateFlow<NoteEntity?> = day.perDay(null) { c.log.observeNote(it) }
    val exercises: StateFlow<List<ExerciseEntity>> = day.perDay(emptyList()) { c.exercises.observeDay(it) }
    val bodyMeasures: StateFlow<List<BodyMeasureEntity>> = day.perDay(emptyList()) { c.body.observeDay(it) }
    val bodyPhotos: StateFlow<List<BodyPhotoEntity>> = day.perDay(emptyList()) { c.body.observePhotos(it) }
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val goal: StateFlow<GoalEntity?> = c.energy.observeGoal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val streak: StateFlow<StreakResult> = c.log.observeLoggedDays()
        .map { StreakCalculator.compute(it.toSet(), today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakResult(0, 0, false, emptySet()))

    val lastLoggedDay: StateFlow<Long?> = c.log.observeLoggedDays().map { days -> days.filter { it <= today() }.maxOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val proposals: StateFlow<List<ComboProposal>> = c.combos.observeProposals(today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _energy = MutableStateFlow<EnergyState>(EnergyState.Loading)
    val energy: StateFlow<EnergyState> = _energy.asStateFlow()

    private val _burnReview = MutableStateFlow<BurnReview?>(null)
    val burnReview: StateFlow<BurnReview?> = _burnReview.asStateFlow()

    private val _weekTargets = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val week: StateFlow<List<WeekDay>> = day.map { weekOf(it) }.distinctUntilChanged()
        .flatMapLatest { range ->
            c.log.observeRange(range.first, range.last).map { list ->
                val eaten = list.groupBy { it.entry.epochDay }.mapValues { (_, l) -> l.sumOf { it.food.forGrams(it.entry.grams).kcal } }
                range.map { it to (eaten[it] ?: 0.0) }
            }
        }
        .combine(_weekTargets) { days, targets -> days.map { (d, eaten) -> WeekDay(d, eaten, targets[d]) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _cheer = MutableStateFlow<Cheer?>(null)
    val cheer: StateFlow<Cheer?> = _cheer.asStateFlow()

    private val realStreak = c.log.observeLoggedDays().map { StreakCalculator.compute(it.toSet(), today()) }

    /** A streak tier reached and not celebrated yet. */
    val newTier: StateFlow<PetStage?> = combine(realStreak, c.settings.settings) { st, set ->
        PetStage.forStreak(st.current).takeIf { it != PetStage.EGG && it > set.celebratedTier }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { watchNewEntries() }
        viewModelScope.launch {
            // A broken streak lowers the celebrated tier, so reaching it again is celebrated again.
            combine(realStreak, c.settings.settings) { st, set -> PetStage.forStreak(st.current) to set.celebratedTier }
                .collect { (tier, celebrated) -> if (tier < celebrated) c.settings.update { it.copy(celebratedTier = tier) } }
        }
        viewModelScope.launch {
            // The correction lives in settings, so accepting or clearing it must recompute the target too.
            val correction = c.settings.settings.map { it.burnCorrectionKcal }.distinctUntilChanged()
            combine(day, c.energy.observeGoal(), correction) { d, g, _ -> d to g }.collect { (d, g) -> loadEnergy(d, g) }
        }
    }

    /** Accepting or postponing both hide the card for a week. */
    suspend fun answerBurnReview(accept: Boolean) {
        val review = _burnReview.value ?: return
        c.energy.answerBurnReview(today(), if (accept) review.correctionKcal else null)
        _burnReview.value = null
    }

    suspend fun celebrateTier(tier: PetStage) = c.settings.update { it.copy(celebratedTier = tier) }

    /**
     * Compares each list of the open day with the previous one. The first list of a day only sets
     * the baseline, so opening a day or the app never cheers by itself.
     */
    private suspend fun watchNewEntries() {
        var lastDay: Long? = null
        var lastIds: Set<Long> = emptySet()
        day.flatMapLatest { d -> c.log.observeDay(d).map { d to it } }.collect { (d, list) ->
            val ids = list.map { it.entry.id }.toSet()
            val added = if (d == lastDay) ids - lastIds else emptySet()
            if (added.isNotEmpty()) _cheer.value = Cheer((_cheer.value?.id ?: 0) + 1, added)
            lastDay = d
            lastIds = ids
        }
    }

    fun setDay(day: Long) {
        _day.value = day
    }

    /** Watch data only reaches Health Connect when the watch syncs, so the user can pull it again. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                loadEnergy(day.value, c.energy.goal())
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Null unless a week has passed since the last answer and the data supports a change. */
    private suspend fun burnReview(goal: GoalEntity, today: Long): BurnReview? {
        if (!c.energy.burnReviewDue(today)) return null
        val from = today - BURN_REVIEW_WINDOW
        val resting = c.energy.restingKcal(goal, today)
        return Trends.burnReview(
            intakeByDay = c.export.summaries(from, today).filter { it.nutrients.kcal > 0 }.associate { it.epochDay to it.nutrients.kcal },
            weights = c.health.weights(from, today),
            garminTotalByDay = c.health.days(from, today).mapNotNull { (d, h) -> c.energy.burnTotal(h, resting)?.let { d to it } }.toMap(),
            today = today,
            currentCorrection = c.energy.burnCorrectionKcal(),
        )
    }

    private suspend fun loadEnergy(day: Long, goal: GoalEntity?) {
        if (goal == null) {
            _energy.value = EnergyState.NoGoal
            return
        }
        _energy.value = try {
            val today = today()
            // Only from today's screen: the review is about what to eat from now on.
            if (day == today) _burnReview.value = burnReview(goal, today)
            val dayEnergy = c.energy.forDay(goal, day, today)
            // Each day of the strip has its own target (dynamic mode follows that day's burn).
            _weekTargets.value = c.energy.targetsFor(goal, weekOf(day), today)
            EnergyState.Ready(
                energy = dayEnergy,
                sleep = c.health.sleep(day, day).firstOrNull(),
                restingHr = c.health.restingHeartRate(day, day).firstOrNull()?.value,
                workouts = c.health.workouts(day, day),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EnergyState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
