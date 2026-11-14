package com.jokin.calorias.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.jokin.calorias.R
import com.jokin.calorias.data.db.AppDatabase
import com.jokin.calorias.data.db.FoodEntity
import com.jokin.calorias.data.db.FoodSource
import com.jokin.calorias.data.db.MeasureEntity
import com.jokin.calorias.data.db.RecentFood
import com.jokin.calorias.data.ocr.LabelOcr
import com.jokin.calorias.data.remote.Barcodes
import com.jokin.calorias.data.remote.EstimatedFood
import com.jokin.calorias.data.remote.Macro
import com.jokin.calorias.data.remote.MealEstimate
import com.jokin.calorias.data.remote.OffLookup
import com.jokin.calorias.data.remote.OffProduct
import com.jokin.calorias.data.remote.OpenFoodFactsClient
import com.jokin.calorias.data.settings.LabelMode
import com.jokin.calorias.data.settings.SettingsRepository
import com.jokin.calorias.domain.LabelParser
import com.jokin.calorias.domain.LabelReading
import com.jokin.calorias.domain.OcrLines
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.domain.VoiceParser
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface BarcodeResult {
    data class Found(val food: FoodEntity) : BarcodeResult
    /** Not in Open Food Facts or incomplete there: the user creates it, prefilled with what is known. */
    data class Missing(val barcode: String, val name: String?, val brand: String?) : BarcodeResult
}

class NoApiKeyException(message: String) : IllegalStateException(message)

class LabelNotReadException(message: String) : IllegalStateException(message)

class FoodInUseException(message: String) : IllegalStateException(message)

const val QUICK_SERVING_GRAMS = 1000.0
private const val QUICK_MAX_KCAL = 5000.0
private const val QUICK_MAX_MACRO_G = 500.0

class FoodRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val off: OpenFoodFactsClient,
    private val ai: AiGateway,
    private val ocr: LabelOcr,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val foods = db.foodDao()

    fun observe(id: Long): Flow<FoodEntity?> = foods.observe(id)

    suspend fun byId(id: Long): FoodEntity? = foods.byId(id)

    fun observeRecent(sinceDay: Long, limit: Int = 20): Flow<List<RecentFood>> = foods.observeRecent(sinceDay, limit)

    fun observeOwn(): Flow<List<FoodEntity>> = foods.observeOwn()

    fun observeMeasures(foodId: Long): Flow<List<MeasureEntity>> = db.measureDao().observeForFood(foodId)

    suspend fun addMeasure(foodId: Long, name: String, grams: Double) {
        require(name.isNotBlank() && grams > 0) { context.getString(R.string.add_error_measure_incomplete) }
        db.measureDao().insert(MeasureEntity(foodId = foodId, name = name.trim().take(40), grams = grams))
    }

    suspend fun deleteMeasure(id: Long) = db.measureDao().delete(id)

    /** Local search; retries with the singular ("platanos" → "platano") when nothing matches. */
    suspend fun searchLocal(query: String, limit: Int = 40): List<FoodEntity> {
        val q = VoiceParser.normalize(query)
        if (q.isBlank()) return emptyList()
        val direct = foods.search(q, limit)
        if (direct.isNotEmpty()) return direct
        val singular = singularOf(q)
        return if (singular != q) foods.search(singular, limit) else emptyList()
    }

    suspend fun searchOnline(query: String): List<OffProduct> = off.search(query)

    /** Local cache first, so a product scanned once works offline from then on. */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        foods.byBarcode(barcode)?.let { return BarcodeResult.Found(it) }
        return when (val r = off.product(barcode)) {
            is OffLookup.Found -> BarcodeResult.Found(cacheOffProduct(r.product))
            is OffLookup.Incomplete -> BarcodeResult.Missing(barcode, r.name, r.brand)
            OffLookup.NotFound -> BarcodeResult.Missing(barcode, null, null)
        }
    }

    suspend fun cacheOffProduct(p: OffProduct): FoodEntity {
        foods.byBarcode(p.barcode)?.let { return it }
        val now = clock()
        val entity = FoodEntity(
            barcode = p.barcode, name = p.name, brand = p.brand, searchName = searchKey(p.name, p.brand),
            kcal = p.nutrients.kcal, protein = p.nutrients.protein, carbs = p.nutrients.carbs, fat = p.nutrients.fat,
            extrasJson = encodeExtras(p.nutrients.extras),
            servingGrams = p.servingGrams, servingName = p.servingName,
            source = FoodSource.OPEN_FOOD_FACTS, createdAt = now, updatedAt = now,
        )
        return entity.copy(id = foods.insert(entity))
    }

    /** Creates or updates a food; editing a cached product keeps its barcode and becomes the local truth. */
    suspend fun save(
        id: Long,
        name: String,
        brand: String?,
        barcode: String?,
        nutrients: Nutrients,
        servingGrams: Double?,
        servingName: String?,
        source: FoodSource,
    ): FoodEntity {
        require(name.isNotBlank()) { context.getString(R.string.add_error_food_needs_name) }
        require(nutrients.kcal in 0.0..950.0) { context.getString(R.string.add_error_kcal_per_100g_range) }
        val cleanBarcode = barcode?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanBarcode == null || Barcodes.isValid(cleanBarcode)) { context.getString(R.string.add_error_invalid_barcode) }
        val now = clock()
        val existing = if (id != 0L) foods.byId(id) else null
        val entity = FoodEntity(
            id = id,
            barcode = cleanBarcode,
            name = name.trim().take(80),
            brand = brand?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
            searchName = searchKey(name, brand),
            kcal = nutrients.kcal, protein = nutrients.protein, carbs = nutrients.carbs, fat = nutrients.fat,
            extrasJson = encodeExtras(nutrients.extras),
            servingGrams = servingGrams?.takeIf { it > 0 },
            servingName = servingName?.trim()?.takeIf { it.isNotEmpty() },
            source = source,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return if (existing != null) entity.also { foods.update(it) } else entity.copy(id = foods.insert(entity))
    }

    suspend fun delete(food: FoodEntity) {
        if (foods.usageCount(food.id) > 0) throw FoodInUseException(context.getString(R.string.add_error_food_in_use, food.name))
        foods.delete(food)
    }

    /** Asks the model for typical values and stores the food marked as estimated. */
    suspend fun estimateWithAi(name: String): FoodEntity {
        val e = (ai.session() ?: throw noApiKey()).estimateFood(name)
        return save(0, e.name, e.brand, null, e.nutrients, e.servingGrams, e.servingGrams?.let { context.getString(R.string.add_one_serving) }, FoodSource.ESTIMATED)
    }

    /** Photo of a plated meal (eating out): AI only, there is no offline way to tell portions. */
    suspend fun estimateMeal(photo: Bitmap, hint: String): MealEstimate {
        val session = ai.session() ?: throw noApiKey()
        return session.estimateMeal(jpegForUpload(photo), hint.trim())
    }

    /** A meal known only by its totals; the name defaults to "Quick" so a quick 800 kcal needs one field. */
    suspend fun saveQuick(name: String, total: Nutrients): FoodEntity {
        require(total.kcal in 1.0..QUICK_MAX_KCAL) { context.getString(R.string.add_error_quick_kcal_range, QUICK_MAX_KCAL.toInt()) }
        require(listOf(total.protein, total.carbs, total.fat).all { it in 0.0..QUICK_MAX_MACRO_G }) { context.getString(R.string.add_error_check_macros) }
        // Values are stored per 100 g of a 1000 g serving, so up to 9500 kcal fit under the 950 kcal/100 g limit.
        val per100g = total.scaledTo(100 * 100 / QUICK_SERVING_GRAMS)
        val defaultName = context.getString(R.string.add_quick_default_name)
        return save(0, name.ifBlank { defaultName }, null, null, per100g, QUICK_SERVING_GRAMS, context.getString(R.string.serving), FoodSource.QUICK)
    }

    /** Each component of a photographed meal becomes its own estimated food, so it can be corrected or reused. */
    suspend fun saveEstimatedDish(name: String, per100g: Nutrients): FoodEntity =
        save(0, name, null, null, per100g, null, null, FoodSource.ESTIMATED)

    /** The basic mode leaves name and brand blank: OCR cannot tell them apart from the rest of the pack. */
    /** [live] is what the camera preview read on device; the basic mode weighs it with the photo and falls back to it. */
    suspend fun readLabel(photo: Bitmap, live: LabelReading?): EstimatedFood {
        val s = settings.current()
        return when (s.labelMode) {
            LabelMode.AI -> (ai.session() ?: throw noApiKey()).readLabel(jpegForUpload(photo))
            LabelMode.BASIC -> {
                val reading = LabelParser.parseBest(ocr.passes(photo).map(OcrLines::upright), hint = live)
                    ?: throw LabelNotReadException(context.getString(R.string.add_error_label_not_read))
                val unread = reading.unreadMacros.map { Macro.valueOf(it.name) }.toSet()
                EstimatedFood(name = "", brand = null, reading.nutrients, reading.servingGrams, unread)
            }
        }
    }

    private fun noApiKey() = NoApiKeyException(context.getString(R.string.add_error_no_api_key))

    private fun singularOf(q: String): String = q.split(' ').joinToString(" ") { w ->
        when {
            w.length > 4 && w.endsWith("es") && w[w.length - 3] !in "aeiou" -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") -> w.dropLast(1)
            else -> w
        }
    }
}

/** Camera photos are several MB; a label or a plate stays readable at 1600 px and uploads much faster. */
private suspend fun jpegForUpload(photo: Bitmap): ByteArray = withContext(Dispatchers.Default) {
    val scale = 1600f / maxOf(photo.width, photo.height)
    val bitmap = if (scale < 1f) photo.scale((photo.width * scale).toInt(), (photo.height * scale).toInt()) else photo
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}
