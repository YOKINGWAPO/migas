package com.jokin.calorias.data.remote

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.jokin.calorias.R
import com.jokin.calorias.domain.ParsedExercise
import com.jokin.calorias.domain.ParsedVoice
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** A Claude model the user can pick; [id] is the API model id. */
data class ClaudeModel(val id: String, val label: String, @StringRes val note: Int)

/**
 * Claude through the official Anthropic SDK. Replies come as structured outputs, so the JSON always
 * matches [AiSchemas]; the shared readers in [AiPrompts] still validate the values.
 */
class ClaudeClient(private val context: Context) : AiClient {
    override val name = "Claude"

    private var cached: Pair<String, AnthropicClient>? = null

    override suspend fun testConnection(key: String, model: String): String {
        ask(key, model, AiPrompts.TEST, jpeg = null, AiSchemas.TEST, OutputConfig.Effort.LOW)
        return context.getString(R.string.add_ai_connection_ok, labelOf(model))
    }

    override suspend fun interpretVoice(key: String, model: String, text: String, glassMl: Int, language: Locale): ParsedVoice =
        voiceFromJson(ask(key, model, AiPrompts.voice(text, glassMl, language), jpeg = null, AiSchemas.VOICE, OutputConfig.Effort.LOW))

    override suspend fun interpretExercises(key: String, model: String, text: String, language: Locale): List<ParsedExercise> =
        exercisesFromJson(ask(key, model, AiPrompts.exercises(text, language), jpeg = null, AiSchemas.EXERCISES, OutputConfig.Effort.LOW))

    override suspend fun estimateFood(key: String, model: String, name: String, language: Locale): EstimatedFood =
        readFood(ask(key, model, AiPrompts.food(name, language), jpeg = null, AiSchemas.FOOD, OutputConfig.Effort.LOW), fallbackName = name)

    override suspend fun readLabel(key: String, model: String, jpeg: ByteArray): EstimatedFood =
        readFood(ask(key, model, AiPrompts.LABEL, jpeg, AiSchemas.FOOD, OutputConfig.Effort.MEDIUM), context.getString(R.string.add_ai_product_fallback))

    /** Medium effort: estimating portions from a photo is where more thinking pays off. */
    override suspend fun estimateMeal(key: String, model: String, jpeg: ByteArray, hint: String, language: Locale): MealEstimate =
        mealFromJson(ask(key, model, AiPrompts.meal(hint, language), jpeg, AiSchemas.MEAL, OutputConfig.Effort.MEDIUM))

    private fun readFood(reply: JsonObject, fallbackName: String): EstimatedFood = try {
        foodFromJson(reply, fallbackName)
    } catch (e: MissingKcalException) {
        throw AiException(context.getString(R.string.add_ai_no_kcal))
    }

    private suspend fun ask(
        key: String,
        model: String,
        prompt: String,
        jpeg: ByteArray?,
        schema: Map<String, Any?>,
        effort: OutputConfig.Effort,
    ): JsonObject = withContext(Dispatchers.IO) {
        val content = buildList {
            // The photo goes before the question, as the API recommends for images.
            jpeg?.let {
                add(ContentBlockParam.ofImage(
                    Base64ImageSource.builder().data(Base64.encodeToString(it, Base64.NO_WRAP)).mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build()
                ))
            }
            add(ContentBlockParam.ofText(prompt))
        }
        val format = JsonOutputFormat.builder()
            .schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(schema.mapValues { JsonValue.from(it.value) }).build())
            .build()
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .addUserMessageOfContentBlockParams(content)
            .outputConfig(OutputConfig.builder().effort(effort).format(format).build())
            .apply {
                // A safety decline is retried on the model Anthropic picks for its category; Haiku has no server-side fallback.
                if (model in SERVER_FALLBACK_MODELS) {
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()
        val message = try {
            client(key).messages().create(params)
        } catch (e: AnthropicException) {
            throw AiException(errorMessage(e))
        }
        parseModelJson(replyText(message))
    }

    private fun replyText(message: Message): String {
        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw AiException(context.getString(R.string.add_claude_refused))
            StopReason.MAX_TOKENS -> throw AiException(context.getString(R.string.add_claude_cut_off))
            else -> Unit
        }
        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        if (text.isBlank()) throw AiException(context.getString(R.string.add_claude_empty))
        return text
    }

    /** One client per key, so changing the key in Settings takes effect at once. */
    @Synchronized
    private fun client(key: String): AnthropicClient {
        cached?.let { (k, c) -> if (k == key) return c }
        cached?.second?.close()
        return AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofSeconds(TIMEOUT_S))
            .maxRetries(1)
            .build()
            .also { cached = key to it }
    }

    /** Only the status is used: the error body can echo the request, photo included. */
    private fun errorMessage(e: AnthropicException): String = when (e) {
        is UnauthorizedException -> context.getString(R.string.add_claude_key_rejected)
        is PermissionDeniedException -> context.getString(R.string.add_claude_no_model_access)
        is NotFoundException -> context.getString(R.string.add_claude_model_not_found)
        is RateLimitException -> context.getString(R.string.add_claude_rate_limited)
        is InternalServerException -> context.getString(R.string.add_claude_overloaded, e.statusCode())
        is AnthropicServiceException -> when (e.statusCode()) {
            402 -> context.getString(R.string.add_claude_no_credit)
            400 -> context.getString(R.string.add_claude_bad_request)
            else -> context.getString(R.string.add_claude_status, e.statusCode())
        }
        is AnthropicIoException -> context.getString(R.string.add_ai_no_connection, name)
        else -> context.getString(R.string.add_claude_unexpected)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"

        val MODELS = listOf(
            ClaudeModel("claude-sonnet-5-5", "Sonnet 5.5", R.string.add_claude_model_sonnet_note),
            ClaudeModel("claude-opus-5-5", "Opus 5.5", R.string.add_claude_model_opus_note),
            ClaudeModel("claude-haiku-5-5", "Haiku 5.5", R.string.add_claude_model_haiku_note),
        )

        fun labelOf(model: String): String = MODELS.firstOrNull { it.id == model }?.label ?: model

        private val SERVER_FALLBACK_MODELS = setOf("claude-sonnet-5-5", "claude-opus-5-5")
        private const val MAX_TOKENS = 16_000L
        private const val TIMEOUT_S = 90L
    }
}
