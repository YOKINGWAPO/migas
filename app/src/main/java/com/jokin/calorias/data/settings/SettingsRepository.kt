package com.jokin.calorias.data.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jokin.calorias.R
import com.jokin.calorias.data.remote.ClaudeClient
import com.jokin.calorias.domain.MascotKind
import com.jokin.calorias.domain.PetStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.mine_theme_system), LIGHT(R.string.mine_theme_light), DARK(R.string.mine_theme_dark),
}

enum class Accent(@StringRes val labelRes: Int, val argb: Long) {
    GREEN(R.string.mine_accent_green, 0xFF2E7D5B),
    BLUE(R.string.mine_accent_blue, 0xFF2F6FB5),
    ORANGE(R.string.mine_accent_orange, 0xFFD9682B),
    PURPLE(R.string.mine_accent_purple, 0xFF7253B8),
    PINK(R.string.mine_accent_pink, 0xFFC2457A),
    TEAL(R.string.mine_accent_teal, 0xFF16867F),
    CYAN(R.string.mine_accent_cyan, 0xFF1F94B4),
    /** Buttons keep a fixed slate; the marks (the +, rings, highlights) slowly turn round the colour wheel. */
    RGB(R.string.mine_accent_rgb, 0xFF52677A),
}

enum class WaterUnit(@StringRes val labelRes: Int) { LITERS(R.string.mine_water_liters), GLASSES(R.string.mine_water_glasses) }

enum class LabelMode(@StringRes val labelRes: Int) {
    BASIC(R.string.mine_label_mode_basic),
    AI(R.string.mine_label_mode_ai),
}

/** Names are stored in settings and in the backup: add entries, never rename them. */
enum class AiProvider(val label: String) { NANO_GPT("NanoGPT"), CLAUDE("Claude") }

/** Everything here goes into the JSON backup; the API keys live apart, encrypted, and never do. */
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.GREEN,
    val dynamicColor: Boolean = false,
    val waterUnit: WaterUnit = WaterUnit.GLASSES,
    val glassMl: Int = 250,
    val waterGoalMl: Int = 2000,
    val nanoModel: String = DEFAULT_NANO_MODEL,
    val aiProvider: AiProvider = AiProvider.NANO_GPT,
    val claudeModel: String = ClaudeClient.DEFAULT_MODEL,
    val labelMode: LabelMode = LabelMode.BASIC,
    val remindersEnabled: Boolean = true,
    val reminderHour: Int = 14,
    val reminderMinute: Int = 30,
    val dismissedCombos: Set<String> = emptySet(),
    /** The watch app's full-day "resting" calories, copied by hand (named after Garmin, kept for backup compatibility); null means estimate with Mifflin-St Jeor. */
    val garminRestingKcal: Int? = null,
    /** Accepted recalibration of Garmin's burn from the weight trend (see Trends.burnReview); 0 = none. */
    val burnCorrectionKcal: Int = 0,
    /** Last day the weekly burn review was answered, so it asks again a week later. */
    val burnReviewDay: Long? = null,
    val mascot: MascotKind = MascotKind.POTATO,
    /** Highest streak tier already celebrated, so the "new rewards" card shows once per tier reached. */
    val celebratedTier: PetStage = PetStage.EGG,
) {
    companion object {
        const val DEFAULT_NANO_MODEL = "deepseek/deepseek-v4.1-flash"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, private val secretBox: SecretBox) {
    private val store = context.dataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val waterUnit = stringPreferencesKey("water_unit")
        val glassMl = intPreferencesKey("glass_ml")
        val waterGoal = intPreferencesKey("water_goal_ml")
        val nanoModel = stringPreferencesKey("nano_model")
        val nanoKey = stringPreferencesKey("nano_key_encrypted")
        val aiProvider = stringPreferencesKey("ai_provider")
        val claudeModel = stringPreferencesKey("claude_model")
        val claudeKey = stringPreferencesKey("claude_key_encrypted")
        val labelMode = stringPreferencesKey("label_mode")
        val reminders = booleanPreferencesKey("reminders")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val dismissedCombos = stringSetPreferencesKey("dismissed_combos")
        val garminResting = intPreferencesKey("garmin_resting_kcal")
        val burnCorrection = intPreferencesKey("burn_correction_kcal")
        val burnReviewDay = longPreferencesKey("burn_review_day")
        val mascot = stringPreferencesKey("mascot")
        val celebratedTier = stringPreferencesKey("celebrated_tier")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = enumOr(p[Keys.theme], d.themeMode),
            accent = enumOr(p[Keys.accent], d.accent),
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            waterUnit = enumOr(p[Keys.waterUnit], d.waterUnit),
            glassMl = p[Keys.glassMl] ?: d.glassMl,
            waterGoalMl = p[Keys.waterGoal] ?: d.waterGoalMl,
            nanoModel = p[Keys.nanoModel] ?: d.nanoModel,
            aiProvider = enumOr(p[Keys.aiProvider], d.aiProvider),
            claudeModel = p[Keys.claudeModel] ?: d.claudeModel,
            labelMode = enumOr(p[Keys.labelMode], d.labelMode),
            remindersEnabled = p[Keys.reminders] ?: d.remindersEnabled,
            reminderHour = p[Keys.reminderHour] ?: d.reminderHour,
            reminderMinute = p[Keys.reminderMinute] ?: d.reminderMinute,
            dismissedCombos = p[Keys.dismissedCombos] ?: d.dismissedCombos,
            garminRestingKcal = p[Keys.garminResting],
            burnCorrectionKcal = p[Keys.burnCorrection] ?: d.burnCorrectionKcal,
            burnReviewDay = p[Keys.burnReviewDay],
            mascot = enumOr(p[Keys.mascot], d.mascot),
            celebratedTier = enumOr(p[Keys.celebratedTier], d.celebratedTier),
        )
    }

    val hasNanoKey: Flow<Boolean> = store.data.map { it[Keys.nanoKey] != null }
    val hasClaudeKey: Flow<Boolean> = store.data.map { it[Keys.claudeKey] != null }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(s: AppSettings) {
        store.edit { p ->
            p[Keys.theme] = s.themeMode.name
            p[Keys.accent] = s.accent.name
            p[Keys.dynamicColor] = s.dynamicColor
            p[Keys.waterUnit] = s.waterUnit.name
            p[Keys.glassMl] = s.glassMl
            p[Keys.waterGoal] = s.waterGoalMl
            p[Keys.nanoModel] = s.nanoModel
            p[Keys.aiProvider] = s.aiProvider.name
            p[Keys.claudeModel] = s.claudeModel
            p[Keys.labelMode] = s.labelMode.name
            p[Keys.reminders] = s.remindersEnabled
            p[Keys.reminderHour] = s.reminderHour
            p[Keys.reminderMinute] = s.reminderMinute
            p[Keys.dismissedCombos] = s.dismissedCombos
            val resting = s.garminRestingKcal
            if (resting == null) p.remove(Keys.garminResting) else p[Keys.garminResting] = resting
            p[Keys.burnCorrection] = s.burnCorrectionKcal
            val reviewDay = s.burnReviewDay
            if (reviewDay == null) p.remove(Keys.burnReviewDay) else p[Keys.burnReviewDay] = reviewDay
            p[Keys.mascot] = s.mascot.name
            p[Keys.celebratedTier] = s.celebratedTier.name
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) = save(transform(current()))

    suspend fun setNanoKey(plain: String?) {
        store.edit { p ->
            if (plain.isNullOrBlank()) p.remove(Keys.nanoKey) else p[Keys.nanoKey] = secretBox.encrypt(plain.trim())
        }
    }

    /** Null when no key is configured; decryption errors propagate (a Keystore reset must be visible). */
    suspend fun nanoKey(): String? = store.data.first()[Keys.nanoKey]?.let(secretBox::decrypt)

    suspend fun setClaudeKey(plain: String?) {
        store.edit { p ->
            if (plain.isNullOrBlank()) p.remove(Keys.claudeKey) else p[Keys.claudeKey] = secretBox.encrypt(plain.trim())
        }
    }

    /** Same contract as [nanoKey]. */
    suspend fun claudeKey(): String? = store.data.first()[Keys.claudeKey]?.let(secretBox::decrypt)

    private inline fun <reified E : Enum<E>> enumOr(raw: String?, default: E): E =
        raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
