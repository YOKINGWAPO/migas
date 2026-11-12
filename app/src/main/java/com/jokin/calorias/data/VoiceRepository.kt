package com.jokin.calorias.data

import android.content.Context
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.ComboWithItems
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.settings.SettingsRepository
import com.jokin.calorias.domain.ParsedItem
import com.jokin.calorias.domain.ParsedVoice
import com.jokin.calorias.domain.Portion
import com.jokin.calorias.domain.PortionResolver
import com.jokin.calorias.domain.VoiceLanguage
import com.jokin.calorias.domain.VoiceParser
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException

data class VoiceInterpretation(
    val parsed: ParsedVoice,
    val usedAi: Boolean,
    /** Why the AI mode was skipped or failed, shown so the fallback to basic mode is never silent. */
    val aiProblem: String?,
)

sealed interface VoiceCandidate {
    val item: ParsedItem

    data class FoodMatch(override val item: ParsedItem, val food: FoodEntity, val grams: Double) : VoiceCandidate
    data class ComboMatch(override val item: ParsedItem, val combo: ComboWithItems) : VoiceCandidate
    data class Unresolved(override val item: ParsedItem, val reason: String) : VoiceCandidate
}

class VoiceRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val foods: FoodRepository,
    private val ai: AiGateway,
    private val settings: SettingsRepository,
) {
    suspend fun interpret(text: String, language: VoiceLanguage): VoiceInterpretation {
        val s = settings.current()
        val basic = { VoiceParser.parse(text, s.glassMl, language) }
        val session = ai.session() ?: return VoiceInterpretation(basic(), usedAi = false, aiProblem = null)
        return try {
            VoiceInterpretation(session.interpretVoice(text, s.glassMl), usedAi = true, aiProblem = null)
        } catch (e: IOException) {
            VoiceInterpretation(basic(), usedAi = false, aiProblem = context.getString(R.string.add_basic_mode_because, e.message ?: context.getString(R.string.add_ai_no_connection, session.providerName)))
        } catch (e: SerializationException) {
            VoiceInterpretation(basic(), usedAi = false, aiProblem = context.getString(R.string.add_basic_mode_because, context.getString(R.string.add_ai_unexpected_format)))
        }
    }

    /**
     * Order from the plan: combos and recents, own products, BEDCA, Open Food Facts. If nothing
     * matches, the AI mode estimates the food (stored as "estimated"); the basic mode leaves it
     * for manual search.
     */
    suspend fun resolve(parsed: ParsedVoice, today: Long): List<VoiceCandidate> {
        val s = settings.current()
        val combos = db.comboDao().observeAll().first()
        val recent = foods.observeRecent(today - 30, limit = 200).first()
        val hasKey = ai.session() != null
        return parsed.items.map { item ->
            combos.firstOrNull { matches(VoiceParser.normalize(it.combo.name), item.name) }
                ?.let { return@map VoiceCandidate.ComboMatch(item, it) }

            val known = recent.firstOrNull { matches(it.food.searchName, item.name) }?.food
                ?: foods.searchLocal(item.name, limit = 1).firstOrNull()
                ?: searchOnline(item.name)
            val food = when {
                known != null -> known
                !hasKey -> return@map VoiceCandidate.Unresolved(item, context.getString(R.string.add_voice_not_in_your_data))
                else -> try {
                    foods.estimateWithAi(item.name)
                } catch (e: IOException) {
                    return@map VoiceCandidate.Unresolved(item, context.getString(R.string.add_voice_ai_could_not_estimate, e.message))
                }
            }
            val portions = db.measureDao().ownOf(food.id).map { Portion(it.name, it.grams) } +
                listOfNotNull(food.servingName?.let { name -> food.servingGrams?.let { Portion(name, it) } })
            VoiceCandidate.FoodMatch(item, food, PortionResolver.grams(item.quantity, item.unit, s.glassMl, food.servingGrams, portions))
        }
    }

    /** Online lookups are optional: offline the item just falls through to the next source. */
    private suspend fun searchOnline(name: String): FoodEntity? = try {
        foods.searchOnline(name).firstOrNull()?.let { foods.cacheOffProduct(it) }
    } catch (e: IOException) {
        null
    }

    /** "sandwich" matches "mi sandwich de siempre" and the other way round. */
    private fun matches(candidate: String, spoken: String): Boolean =
        candidate == spoken || candidate.startsWith("$spoken ") || (candidate.length >= 4 && spoken.contains(candidate))
}
