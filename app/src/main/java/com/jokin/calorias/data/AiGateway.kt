package com.jokin.calorias.data

import com.jokin.calorias.data.remote.AiClient
import com.jokin.calorias.data.remote.ClaudeClient
import com.jokin.calorias.data.remote.EstimatedFood
import com.jokin.calorias.data.remote.MealEstimate
import com.jokin.calorias.data.remote.NanoGptClient
import com.jokin.calorias.data.settings.AiProvider
import com.jokin.calorias.data.settings.SettingsRepository
import com.jokin.calorias.domain.ParsedExercise
import com.jokin.calorias.domain.ParsedVoice
import java.util.Locale

/**
 * The AI provider chosen in Settings, so the rest of the app never asks which one it is. [language] is
 * read per session because Android sets the default locale to the app's language, which can change.
 */
class AiGateway(
    private val nano: NanoGptClient,
    private val claude: ClaudeClient,
    private val settings: SettingsRepository,
    private val language: () -> Locale = Locale::getDefault,
) {
    /** The chosen provider with its key and model, or null when it has no key (the app then works in basic mode). */
    suspend fun session(): AiSession? {
        val s = settings.current()
        return when (s.aiProvider) {
            AiProvider.NANO_GPT -> settings.nanoKey()?.let { AiSession(nano, it, s.nanoModel, language()) }
            AiProvider.CLAUDE -> settings.claudeKey()?.let { AiSession(claude, it, s.claudeModel, language()) }
        }
    }

    fun client(provider: AiProvider): AiClient = when (provider) {
        AiProvider.NANO_GPT -> nano
        AiProvider.CLAUDE -> claude
    }
}

/** An AI client bound to a key, a model and the language the user reads its answers in. */
class AiSession(private val client: AiClient, private val key: String, private val model: String, private val language: Locale) {
    val providerName: String get() = client.name

    suspend fun interpretVoice(text: String, glassMl: Int): ParsedVoice = client.interpretVoice(key, model, text, glassMl, language)
    suspend fun interpretExercises(text: String): List<ParsedExercise> = client.interpretExercises(key, model, text, language)
    suspend fun estimateFood(name: String): EstimatedFood = client.estimateFood(key, model, name, language)
    suspend fun readLabel(jpeg: ByteArray): EstimatedFood = client.readLabel(key, model, jpeg)
    suspend fun estimateMeal(jpeg: ByteArray, hint: String): MealEstimate = client.estimateMeal(key, model, jpeg, hint, language)
}
