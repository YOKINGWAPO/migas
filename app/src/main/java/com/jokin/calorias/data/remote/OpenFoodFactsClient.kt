package com.jokin.calorias.data.remote

import android.content.Context
import com.jokin.calorias.BuildConfig
import com.jokin.calorias.R
import com.jokin.calorias.domain.ExtraNutrient
import com.jokin.calorias.domain.Nutrients
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class OffProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val nutrients: Nutrients,
    val servingGrams: Double?,
    val servingName: String?,
)

sealed interface OffLookup {
    data class Found(val product: OffProduct) : OffLookup
    /** The code exists but lacks a name or energy value; the user has to complete it. */
    data class Incomplete(val barcode: String, val name: String?, val brand: String?) : OffLookup
    data object NotFound : OffLookup
}

class OpenFoodFactsClient(private val http: OkHttpClient, private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://world.openfoodfacts.org".toHttpUrl()
    private val searchBase = "https://search.openfoodfacts.org".toHttpUrl()

    suspend fun product(barcode: String): OffLookup {
        require(Barcodes.isValid(barcode)) { "invalid barcode" }
        val language = language()
        val url = base.newBuilder()
            .addPathSegments("api/v2/product").addPathSegment("$barcode.json")
            .addQueryParameter("fields", fields(language))
            .addQueryParameter("lc", language)
            .build()
        val root = get(url)
        if ((root["status"] as? JsonPrimitive)?.intOrNull != 1) return OffLookup.NotFound
        val product = root["product"]?.jsonObject ?: return OffLookup.NotFound
        return parse(barcode, product, language)
    }

    /**
     * Name search through search-a-licious: the legacy cgi/search.pl answers with an HTML
     * "temporarily unavailable" page under load, this one returns JSON reliably.
     */
    suspend fun search(query: String): List<OffProduct> {
        val clean = query.replace("\"", " ").trim().take(80)
        if (clean.isEmpty()) return emptyList()
        val language = language()
        val url = searchBase.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", "$clean countries_tags:\"en:spain\"")
            .addQueryParameter("page_size", "25")
            .addQueryParameter("fields", "code," + fields(language))
            .build()
        val hits = get(url)["hits"] as? JsonArray ?: return emptyList()
        return hits.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val code = (obj["code"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            if (!Barcodes.isValid(code)) return@mapNotNull null
            (parse(code, obj, language) as? OffLookup.Found)?.product
        }.distinctBy { it.barcode }
    }

    private suspend fun get(url: HttpUrl): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            // OFF answers 404 with a JSON body for unknown products, so only 5xx and others are errors.
            if (!response.isSuccessful && response.code != 404) throw IOException(context.getString(R.string.add_off_status, response.code))
            val body = response.body.string()
            if (!body.trimStart().startsWith("{")) throw IOException(context.getString(R.string.add_off_unavailable))
            json.parseToJsonElement(body).jsonObject
        }
    }

    /** Product names in the app's language when OFF has them; the generic name is the fallback. */
    private fun language(): String = context.resources.configuration.locales[0].language

    private fun fields(language: String) = "product_name,product_name_$language,$FIELDS"

    private fun parse(barcode: String, p: JsonObject, language: String): OffLookup {
        val name = p.text("product_name_$language") ?: p.text("product_name")
        // The product API returns brands as "A, B"; the search API as ["A", "B"].
        val brand = ((p["brands"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: p.text("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val n = p["nutriments"] as? JsonObject
        val kcal = n?.num("energy-kcal_100g") ?: n?.num("energy_100g")?.let { it / KJ_PER_KCAL }
        if (name == null || n == null || kcal == null || kcal !in 0.0..MAX_KCAL_PER_100G) {
            return OffLookup.Incomplete(barcode, name, brand)
        }
        val nutrients = Nutrients(
            kcal = kcal,
            protein = n.grams("proteins_100g"),
            carbs = n.grams("carbohydrates_100g"),
            fat = n.grams("fat_100g"),
            extras = EXTRA_FIELDS.mapNotNull { (key, field) ->
                n.num(field.first)?.takeIf { it >= 0 }?.let { key to it * field.second }
            }.toMap(),
        )
        val servingGrams = (p["serving_quantity"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 && it < 5000 }
        return OffLookup.Found(OffProduct(barcode, name, brand, nutrients, servingGrams, p.text("serving_size")))
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.take(120)

    /** OFF stores numbers either as JSON numbers or as strings. */
    private fun JsonObject.num(key: String): Double? = when (val v: JsonElement? = this[key]) {
        is JsonPrimitive -> v.doubleOrNull ?: v.content.replace(',', '.').toDoubleOrNull()
        else -> null
    }

    private fun JsonObject.grams(key: String): Double = num(key)?.coerceIn(0.0, 100.0) ?: 0.0

    private companion object {
        const val USER_AGENT = "Migas/${BuildConfig.VERSION_NAME} (Android; github.com/YOKINGWAPO/migas)"
        const val KJ_PER_KCAL = 4.184
        const val MAX_KCAL_PER_100G = 950.0
        const val FIELDS = "brands,serving_quantity,serving_size,nutriments"

        /** OFF field and the factor from its base unit (grams) to ours. */
        val EXTRA_FIELDS: Map<ExtraNutrient, Pair<String, Double>> = mapOf(
            ExtraNutrient.SUGARS to ("sugars_100g" to 1.0),
            ExtraNutrient.FIBER to ("fiber_100g" to 1.0),
            ExtraNutrient.SATURATED_FAT to ("saturated-fat_100g" to 1.0),
            ExtraNutrient.SALT to ("salt_100g" to 1.0),
            ExtraNutrient.VITAMIN_A to ("vitamin-a_100g" to 1e6),
            ExtraNutrient.VITAMIN_D to ("vitamin-d_100g" to 1e6),
            ExtraNutrient.VITAMIN_E to ("vitamin-e_100g" to 1e3),
            ExtraNutrient.VITAMIN_C to ("vitamin-c_100g" to 1e3),
            ExtraNutrient.VITAMIN_B1 to ("vitamin-b1_100g" to 1e3),
            ExtraNutrient.VITAMIN_B2 to ("vitamin-b2_100g" to 1e3),
            ExtraNutrient.VITAMIN_B6 to ("vitamin-b6_100g" to 1e3),
            ExtraNutrient.VITAMIN_B12 to ("vitamin-b12_100g" to 1e6),
            ExtraNutrient.FOLATE to ("vitamin-b9_100g" to 1e6),
            ExtraNutrient.CALCIUM to ("calcium_100g" to 1e3),
            ExtraNutrient.IRON to ("iron_100g" to 1e3),
            ExtraNutrient.MAGNESIUM to ("magnesium_100g" to 1e3),
            ExtraNutrient.ZINC to ("zinc_100g" to 1e3),
            ExtraNutrient.POTASSIUM to ("potassium_100g" to 1e3),
            ExtraNutrient.PHOSPHORUS to ("phosphorus_100g" to 1e3),
        )
    }
}

object Barcodes {
    /** EAN-8, UPC-A, EAN-13 and GTIN-14 are all digits; anything else is not a food code. */
    fun isValid(code: String): Boolean = code.length in 8..14 && code.all { it in '0'..'9' }
}
