package com.jokin.calorias.data.remote

import android.content.Context
import android.util.Base64
import com.jokin.calorias.R
import com.jokin.calorias.domain.ParsedExercise
import com.jokin.calorias.domain.ParsedVoice
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI-compatible chat client for NanoGPT. It goes through the subscription endpoint, which
 * only accepts models included in the subscription, so a request can never spend balance.
 */
class NanoGptClient(private val http: OkHttpClient, private val context: Context) : AiClient {
    override val name = "NanoGPT"

    /** Any non-empty reply proves key and model work; some models garble even this trivial JSON. */
    override suspend fun testConnection(key: String, model: String): String {
        complete(key, model, userText(AiPrompts.TEST))
        return context.getString(R.string.add_ai_connection_ok, model)
    }

    override suspend fun interpretVoice(key: String, model: String, text: String, glassMl: Int, language: Locale): ParsedVoice =
        voiceFromJson(chat(key, model, userText(AiPrompts.voice(text, glassMl, language))))

    override suspend fun interpretExercises(key: String, model: String, text: String, language: Locale): List<ParsedExercise> =
        exercisesFromJson(chat(key, model, userText(AiPrompts.exercises(text, language))))

    override suspend fun estimateFood(key: String, model: String, name: String, language: Locale): EstimatedFood =
        readFood(chat(key, model, userText(AiPrompts.food(name, language))), fallbackName = name)

    /** The model must support images (DeepSeek 4.1 Flash does). */
    override suspend fun readLabel(key: String, model: String, jpeg: ByteArray): EstimatedFood =
        readFood(chat(key, model, userTextWithImage(AiPrompts.LABEL, jpeg)), context.getString(R.string.add_ai_product_fallback))

    override suspend fun estimateMeal(key: String, model: String, jpeg: ByteArray, hint: String, language: Locale): MealEstimate =
        mealFromJson(chat(key, model, userTextWithImage(AiPrompts.meal(hint, language), jpeg)))

    private fun readFood(reply: JsonObject, fallbackName: String): EstimatedFood = try {
        foodFromJson(reply, fallbackName)
    } catch (e: MissingKcalException) {
        throw AiException(context.getString(R.string.add_ai_no_kcal))
    }

    private fun userText(text: String) = buildJsonObject { put("role", "user"); put("content", text) }

    private fun userTextWithImage(text: String, jpeg: ByteArray) = buildJsonObject {
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        put("role", "user")
        putJsonArray("content") {
            addJsonObject { put("type", "text"); put("text", text) }
            addJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", dataUri) } }
        }
    }

    private suspend fun chat(key: String, model: String, message: JsonObject): JsonObject =
        parseModelJson(complete(key, model, message))

    /** Returns the model's raw reply text. */
    private suspend fun complete(key: String, model: String, message: JsonObject): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") { add(message) }
            putJsonObject("response_format") { put("type", "json_object") }
            put("temperature", 0)
        }
        val request = Request.Builder()
            .url(CHAT_URL)
            .header("Authorization", "Bearer $key")
            .post(JSON.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) throw AiException(httpErrorMessage(response.code, raw, model))
            val content = JSON.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            if (content.isNullOrBlank()) throw AiException(context.getString(R.string.add_nano_empty, model))
            content
        }
    }

    /** The error body may echo request details, so only its error code is read, never shown. */
    private fun httpErrorMessage(status: Int, body: String, model: String): String {
        val code = runCatching { JSON.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content }.getOrNull()
        return when {
            status == 401 || status == 403 -> context.getString(R.string.add_nano_key_rejected, status)
            status == 402 -> context.getString(R.string.add_nano_not_in_subscription, status)
            code == "image_input_not_supported" -> context.getString(R.string.add_nano_no_images, model)
            status == 429 -> context.getString(R.string.add_nano_rate_limited)
            else -> context.getString(R.string.add_nano_status, status)
        }
    }

    private companion object {
        const val CHAT_URL = "https://nano-gpt.com/api/subscription/v1/chat/completions"
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

private val JSON = Json { ignoreUnknownKeys = true }
