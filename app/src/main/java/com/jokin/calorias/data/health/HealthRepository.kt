package com.jokin.calorias.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.jokin.calorias.domain.DayBurn
import com.jokin.calorias.domain.DayValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

data class HealthDay(
    val epochDay: Long,
    val totalKcal: Double?,
    val activeKcal: Double?,
    val steps: Long?,
    val distanceM: Double?,
    /** Packages of the apps that wrote this day's figures (Garmin Connect, Samsung Health...). */
    val sources: Set<String> = emptySet(),
) {
    val burn: DayBurn get() = DayBurn(totalKcal, activeKcal)

    /** Some brands write only active calories, others only the total: either one is real watch data. */
    val hasBurn: Boolean get() = totalKcal != null || activeKcal != null
}

data class Workout(
    val epochDay: Long,
    val type: WorkoutType,
    val title: String?,
    val start: Instant,
    val minutes: Long,
    val distanceM: Double?,
    val kcal: Double?,
)

enum class WorkoutType {
    RUNNING, WALKING, CYCLING, SWIMMING,
    HIKING, STRENGTH, YOGA, OTHER;

    companion object {
        fun of(hcType: Int): WorkoutType = when (hcType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> RUNNING
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> WALKING
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> CYCLING
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> SWIMMING
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> HIKING
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> STRENGTH
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> YOGA
            else -> OTHER
        }
    }
}

data class SleepDay(val epochDay: Long, val minutes: Long)

enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

/**
 * Reads what the user's watch app (Garmin Connect, Samsung Health, Fitbit...) writes into Health Connect. Nothing is copied into Room: every
 * screen reads fresh values, so a late watch sync shows up on the next refresh.
 */
class HealthRepository(private val context: Context) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val readPermissions: Set<String> = setOf(
        TotalCaloriesBurnedRecord::class, ActiveCaloriesBurnedRecord::class, StepsRecord::class, DistanceRecord::class,
        WeightRecord::class, BodyFatRecord::class, SleepSessionRecord::class, RestingHeartRateRecord::class,
        ExerciseSessionRecord::class,
    ).map { HealthPermission.getReadPermission(it) }.toSet()

    val allPermissions: Set<String> = readPermissions +
        HealthPermission.getWritePermission(HydrationRecord::class) +
        WEIGHT_WRITE_PERMISSION +
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.UNAVAILABLE
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    suspend fun grantedPermissions(): Set<String> =
        client()?.permissionController?.getGrantedPermissions() ?: emptySet()

    /** Daily burn, steps and distance for [from]..[to] (inclusive). Empty when nothing is granted. */
    suspend fun days(from: Long, to: Long): Map<Long, HealthDay> {
        val client = client() ?: return emptyMap()
        val granted = client.permissionController.getGrantedPermissions()
        val metrics = buildSet {
            if (granted.has(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (granted.has(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (granted.has(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (granted.has(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
        }
        if (metrics.isEmpty()) return emptyMap()
        val result = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = metrics,
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDate.ofEpochDay(from).atStartOfDay(),
                    LocalDate.ofEpochDay(to + 1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        return result.associate { bucket ->
            val day = bucket.startTime.toLocalDate().toEpochDay()
            val r = bucket.result
            day to HealthDay(
                epochDay = day,
                totalKcal = r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                activeKcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                steps = r[StepsRecord.COUNT_TOTAL],
                distanceM = r[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                sources = r.dataOrigins.map { it.packageName }.toSet(),
            )
        }
    }

    /** Last weigh-in of each day. */
    suspend fun weights(from: Long, to: Long): List<DayValue> =
        read(WeightRecord::class, from, to).map { DayValue(dayOf(it.time), it.weight.inKilograms) }
            .groupBy { it.epochDay }.map { it.value.last() }

    suspend fun bodyFat(from: Long, to: Long): List<DayValue> =
        read(BodyFatRecord::class, from, to).map { DayValue(dayOf(it.time), it.percentage.value) }
            .groupBy { it.epochDay }.map { it.value.last() }

    suspend fun restingHeartRate(from: Long, to: Long): List<DayValue> =
        read(RestingHeartRateRecord::class, from, to).map { DayValue(dayOf(it.time), it.beatsPerMinute.toDouble()) }
            .groupBy { it.epochDay }.map { it.value.last() }

    /** Sleep is attributed to the day you wake up. */
    /** A night belongs to the day it ends on, so the search starts a day earlier: bedtime is usually before midnight. */
    suspend fun sleep(from: Long, to: Long): List<SleepDay> =
        read(SleepSessionRecord::class, from - 1, to).groupBy { dayOf(it.endTime) }
            .map { (day, sessions) -> SleepDay(day, sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }) }
            .filter { it.epochDay in from..to }

    suspend fun workouts(from: Long, to: Long): List<Workout> {
        val client = client() ?: return emptyList()
        val granted = client.permissionController.getGrantedPermissions()
        return read(ExerciseSessionRecord::class, from, to).map { s ->
            val window = TimeRangeFilter.between(s.startTime, s.endTime)
            val metrics = buildSet {
                if (granted.has(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
                if (granted.has(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            }
            val agg = if (metrics.isEmpty()) null else client.aggregate(AggregateRequest(metrics, window))
            Workout(
                epochDay = dayOf(s.startTime),
                type = WorkoutType.of(s.exerciseType),
                title = s.title,
                start = s.startTime,
                minutes = java.time.Duration.between(s.startTime, s.endTime).toMinutes(),
                distanceM = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters?.takeIf { it > 0 },
                kcal = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            )
        }
    }

    /** Returns false when Health Connect is missing or the write permission was not granted. */
    suspend fun writeHydration(ml: Int, at: Instant): Boolean {
        val client = client() ?: return false
        if (!client.permissionController.getGrantedPermissions().has(HydrationRecord::class, write = true)) return false
        val offset = zone.rules.getOffset(at)
        client.insertRecords(
            listOf(
                HydrationRecord(
                    startTime = at, startZoneOffset = offset,
                    endTime = at.plusSeconds(1), endZoneOffset = offset,
                    volume = Volume.milliliters(ml.toDouble()),
                    metadata = Metadata.manualEntry(),
                )
            )
        )
        return true
    }

    suspend fun canWriteWeight(): Boolean = WEIGHT_WRITE_PERMISSION in grantedPermissions()

    /**
     * Imported weigh-ins, one per day at 08:00. The id is the date, so importing the same file again
     * overwrites those records instead of duplicating them; the version is the write time so it always wins.
     * Returns false when Health Connect is missing or the write permission was not granted.
     */
    suspend fun writeImportedWeights(kgByDay: Map<Long, Double>): Boolean {
        val client = client() ?: return false
        if (!client.permissionController.getGrantedPermissions().has(WeightRecord::class, write = true)) return false
        val version = System.currentTimeMillis()
        kgByDay.entries.chunked(INSERT_BATCH).forEach { chunk ->
            client.insertRecords(chunk.map { (day, kg) ->
                val at = LocalDate.ofEpochDay(day).atTime(IMPORTED_WEIGH_IN).atZone(zone)
                WeightRecord(
                    time = at.toInstant(), zoneOffset = at.offset, weight = Mass.kilograms(kg),
                    metadata = Metadata.manualEntry(clientRecordId = "import-weight-$day", clientRecordVersion = version),
                )
            })
        }
        return true
    }

    private suspend fun <T : Record> read(type: KClass<T>, from: Long, to: Long): List<T> {
        val client = client() ?: return emptyList()
        if (!client.permissionController.getGrantedPermissions().has(type)) return emptyList()
        val filter = TimeRangeFilter.between(
            LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant(),
            LocalDate.ofEpochDay(to + 1).atStartOfDay(zone).toInstant(),
        )
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageToken = pageToken))
            out += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    private fun dayOf(instant: Instant): Long = instant.atZone(zone).toLocalDate().toEpochDay()

    companion object {
        val WEIGHT_WRITE_PERMISSION: String = HealthPermission.getWritePermission(WeightRecord::class)
        private val IMPORTED_WEIGH_IN: LocalTime = LocalTime.of(8, 0)
        /** Health Connect rejects very large inserts; this stays well under its limit. */
        private const val INSERT_BATCH = 1000
    }

    private fun Set<String>.has(type: KClass<out Record>, write: Boolean = false): Boolean =
        contains(if (write) HealthPermission.getWritePermission(type) else HealthPermission.getReadPermission(type))
}
