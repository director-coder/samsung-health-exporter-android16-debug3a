package eu.apava.healthmqtt

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectReader(
    private val context: Context
) {
    companion object {
        val requiredPermissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        )
    }

    val permissions = requiredPermissions

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(): Set<String> {
        return client.permissionController.getGrantedPermissions()
    }

    suspend fun permissionStatusText(): String {
        val granted = grantedPermissions()
        return "${granted.intersect(requiredPermissions).size}/${requiredPermissions.size} granted"
    }

    suspend fun hasStepsPermission(): Boolean {
        return grantedPermissions().contains(HealthPermission.getReadPermission(StepsRecord::class))
    }

    suspend fun readSnapshot(): HealthSnapshot {
        val debug = linkedMapOf<String, String>()
        val granted = grantedPermissions()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfToday: Instant = today.atStartOfDay(zone).toInstant()
        val now: Instant = Instant.now()
        val last30Days: Instant = now.minus(Duration.ofDays(30))
        val sleepWindowStart: Instant = today.minusDays(2).atTime(18, 0).atZone(zone).toInstant()
        val sleepWindowEnd: Instant = today.atTime(12, 0).atZone(zone).toInstant().let { if (it.isAfter(now)) now else it }

        debug["version"] = "diagnostic-v3a"
        debug["local_zone"] = zone.id
        debug["today_start"] = startOfToday.toString()
        debug["now"] = now.toString()
        debug["sleep_window_start"] = sleepWindowStart.toString()
        debug["sleep_window_end"] = sleepWindowEnd.toString()

        fun has(permission: String): Boolean = granted.contains(permission)
        fun permissionName(permission: String): String = permission.substringAfterLast('.')

        requiredPermissions.forEach { permission ->
            debug["perm_${permissionName(permission)}"] = if (granted.contains(permission)) "granted" else "missing"
        }

        // Broad diagnostics: if today's counters are empty, these show whether Health Connect
        // exposes any records of each type to this app at all, and which app wrote them.
        debugRecordScan<StepsRecord>("scan_steps_today_between", TimeRangeFilter.between(startOfToday, now), debug)
        debugRecordScan<StepsRecord>("scan_steps_today_wide_between", TimeRangeFilter.between(startOfToday, now), debug)
        debugRecordScan<StepsRecord>("scan_steps_7d", TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now), debug)
        debugRecordScan<DistanceRecord>("scan_distance_7d", TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now), debug)
        debugRecordScan<ActiveCaloriesBurnedRecord>("scan_active_calories_7d", TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now), debug)
        debugRecordScan<TotalCaloriesBurnedRecord>("scan_total_calories_7d", TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now), debug)
        debugRecordScan<HeartRateRecord>("scan_heart_rate_30d", TimeRangeFilter.between(last30Days, now), debug)
        debugRecordScan<SleepSessionRecord>("scan_sleep_30d", TimeRangeFilter.between(last30Days, now), debug)
        debugRecordScan<RespiratoryRateRecord>("scan_respiratory_rate_30d", TimeRangeFilter.between(last30Days, now), debug)
        debugRecordScan<OxygenSaturationRecord>("scan_oxygen_saturation_30d", TimeRangeFilter.between(last30Days, now), debug)
        debugRecordScan<WeightRecord>("scan_weight_30d", TimeRangeFilter.between(last30Days, now), debug)
        debugRecordScan<HeightRecord>("scan_height_30d", TimeRangeFilter.between(last30Days, now), debug)

        val stepsToday = if (has(HealthPermission.getReadPermission(StepsRecord::class))) {
            readStepsToday(startOfToday, now, debug)
        } else null

        val distanceMetersToday = if (has(HealthPermission.getReadPermission(DistanceRecord::class))) {
            readDistanceMeters(startOfToday, now, debug)
        } else null

        val activeCaloriesKcalToday = if (has(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
            readActiveCaloriesKcal(startOfToday, now, debug)
        } else null

        val totalCaloriesKcalToday = if (has(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
            readTotalCaloriesKcal(startOfToday, now, debug)
        } else null

        val floorsClimbedToday = if (has(HealthPermission.getReadPermission(FloorsClimbedRecord::class))) {
            readFloors(startOfToday, now, debug)
        } else null

        val heartRateStats = if (has(HealthPermission.getReadPermission(HeartRateRecord::class))) {
            readHeartRateStats(startOfToday, now, debug)
        } else HeartRateStats()

        return HealthSnapshot(
            generatedAt = now.toString(),
            healthConnectAvailable = isAvailable(),
            grantedPermissions = granted.intersect(requiredPermissions).size,
            requestedPermissions = requiredPermissions.size,
            stepsToday = stepsToday,
            distanceMetersToday = distanceMetersToday,
            activeCaloriesKcalToday = activeCaloriesKcalToday,
            totalCaloriesKcalToday = totalCaloriesKcalToday,
            floorsClimbedToday = floorsClimbedToday,
            heartRateAvgBpm = heartRateStats.avg,
            heartRateMinBpm = heartRateStats.min,
            heartRateMaxBpm = heartRateStats.max,
            latestWeightKg = if (has(HealthPermission.getReadPermission(WeightRecord::class))) {
                readLatestWeightKg(last30Days, now, debug)
            } else null,
            latestHeightCm = if (has(HealthPermission.getReadPermission(HeightRecord::class))) {
                readLatestHeightCm(last30Days, now, debug)
            } else null,
            sleepMinutesLastNight = if (has(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                readSleepMinutes(sleepWindowStart, sleepWindowEnd, debug)
            } else null,
            exerciseSessionsToday = if (has(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                readExerciseCount(startOfToday, now, debug)
            } else null,
            exerciseMinutesToday = if (has(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                readExerciseMinutes(startOfToday, now, debug)
            } else null,
            latestRespiratoryRateBpm = if (has(HealthPermission.getReadPermission(RespiratoryRateRecord::class))) {
                readLatestRespiratoryRate(last30Days, now, debug)
            } else null,
            latestOxygenSaturationPercent = if (has(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
                readLatestOxygenSaturationPercent(last30Days, now, debug)
            } else null,
            debug = debug
        )
    }


    private suspend inline fun <reified T : Record> debugRecordScan(
        label: String,
        timeRangeFilter: TimeRangeFilter,
        debug: MutableMap<String, String>
    ) {
        try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = timeRangeFilter
                )
            )
            debug["${label}_count"] = response.records.size.toString()
            val origins = response.records
                .map { it.metadata.dataOrigin.packageName }
                .distinct()
                .sorted()
                .take(8)
            debug["${label}_origins"] = origins.joinToString(",").ifBlank { "none" }
            val latest = response.records
                .mapNotNull { recordLastInstant(it) }
                .maxOrNull()
            if (latest != null) {
                debug["${label}_latest"] = latest.toString()
            }
        } catch (e: Throwable) {
            debug["${label}_error"] = shortError(e)
        }
    }

    private fun recordLastInstant(record: Record): Instant? {
        return when (record) {
            is StepsRecord -> record.endTime
            is DistanceRecord -> record.endTime
            is ActiveCaloriesBurnedRecord -> record.endTime
            is TotalCaloriesBurnedRecord -> record.endTime
            is FloorsClimbedRecord -> record.endTime
            is HeartRateRecord -> record.samples.maxOfOrNull { it.time } ?: record.endTime
            is SleepSessionRecord -> record.endTime
            is ExerciseSessionRecord -> record.endTime
            is RespiratoryRateRecord -> record.time
            is OxygenSaturationRecord -> record.time
            is WeightRecord -> record.time
            is HeightRecord -> record.time
            else -> null
        }
    }

    private suspend fun <T : Any> readAggregate(
        label: String,
        metric: AggregateMetric<T>,
        start: Instant,
        end: Instant,
        debug: MutableMap<String, String>
    ): T? {
        return try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(metric),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val value = response[metric]
            debug["${label}_aggregate"] = value?.toString() ?: "null"
            value
        } catch (e: Throwable) {
            debug["${label}_aggregate_error"] = shortError(e)
            null
        }
    }

    private suspend fun readStepsToday(start: Instant, end: Instant, debug: MutableMap<String, String>): Long? {
        val aggregate = readAggregate("steps", StepsRecord.COUNT_TOTAL, start, end, debug) as? Long
        if (aggregate != null) {
            debug["steps_source"] = "aggregate"
            return aggregate
        }

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["steps_records_count"] = response.records.size.toString()
            debug["steps_records_origins"] = response.records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString(",").ifBlank { "none" }
            debug["steps_source"] = "records_sum_between"
            response.records.sumOf { it.count }
        } catch (e: Throwable) {
            debug["steps_records_error"] = shortError(e)
            null
        }
    }

    private suspend fun readDistanceMeters(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        val aggregate = readAggregate("distance", DistanceRecord.DISTANCE_TOTAL, start, end, debug)?.inMeters
        if (aggregate != null) {
            debug["distance_source"] = "aggregate"
            return aggregate
        }

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["distance_records_count"] = response.records.size.toString()
            debug["distance_records_origins"] = response.records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString(",").ifBlank { "none" }
            debug["distance_source"] = "records_sum_between"
            response.records.sumOf { it.distance.inMeters }
        } catch (e: Throwable) {
            debug["distance_records_error"] = shortError(e)
            null
        }
    }

    private suspend fun readActiveCaloriesKcal(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        val aggregate = readAggregate("active_calories", ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, start, end, debug)?.inKilocalories
        if (aggregate != null) {
            debug["active_calories_source"] = "aggregate"
            return aggregate
        }

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["active_calories_records_count"] = response.records.size.toString()
            debug["active_calories_records_origins"] = response.records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString(",").ifBlank { "none" }
            debug["active_calories_source"] = "records_sum_between"
            response.records.sumOf { it.energy.inKilocalories }
        } catch (e: Throwable) {
            debug["active_calories_records_error"] = shortError(e)
            null
        }
    }

    private suspend fun readTotalCaloriesKcal(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        return readAggregate("total_calories", TotalCaloriesBurnedRecord.ENERGY_TOTAL, start, end, debug)?.inKilocalories
    }

    private suspend fun readFloors(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        val aggregate = readAggregate("floors", FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL, start, end, debug)
        if (aggregate != null) {
            debug["floors_source"] = "aggregate"
            return aggregate
        }

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = FloorsClimbedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["floors_records_count"] = response.records.size.toString()
            debug["floors_records_origins"] = response.records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString(",").ifBlank { "none" }
            debug["floors_source"] = "records_sum_between"
            response.records.sumOf { it.floors }
        } catch (e: Throwable) {
            debug["floors_records_error"] = shortError(e)
            null
        }
    }

    private suspend fun readHeartRateStats(start: Instant, end: Instant, debug: MutableMap<String, String>): HeartRateStats {
        val avg = readAggregate("heart_rate_avg", HeartRateRecord.BPM_AVG, start, end, debug) as? Long
        val min = readAggregate("heart_rate_min", HeartRateRecord.BPM_MIN, start, end, debug) as? Long
        val max = readAggregate("heart_rate_max", HeartRateRecord.BPM_MAX, start, end, debug) as? Long

        if (avg != null || min != null || max != null) {
            debug["heart_rate_source"] = "aggregate"
            return HeartRateStats(avg = avg, min = min, max = max)
        }

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val samples = response.records.flatMap { it.samples }
            val bpms = samples.map { it.beatsPerMinute }
            debug["heart_rate_records_count"] = response.records.size.toString()
            debug["heart_rate_records_origins"] = response.records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString(",").ifBlank { "none" }
            debug["heart_rate_samples_count"] = samples.size.toString()
            debug["heart_rate_source"] = "records_samples"
            if (bpms.isEmpty()) {
                HeartRateStats()
            } else {
                HeartRateStats(
                    avg = bpms.average().toLong(),
                    min = bpms.minOrNull(),
                    max = bpms.maxOrNull()
                )
            }
        } catch (e: Throwable) {
            debug["heart_rate_records_error"] = shortError(e)
            HeartRateStats()
        }
    }

    private suspend fun readLatestWeightKg(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["weight_records_count"] = response.records.size.toString()
            response.records.maxByOrNull { it.time }?.weight?.inKilograms
        } catch (e: Throwable) {
            debug["weight_error"] = shortError(e)
            null
        }
    }

    private suspend fun readLatestHeightCm(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["height_records_count"] = response.records.size.toString()
            response.records.maxByOrNull { it.time }?.height?.inMeters?.times(100.0)
        } catch (e: Throwable) {
            debug["height_error"] = shortError(e)
            null
        }
    }

    private suspend fun readSleepMinutes(start: Instant, end: Instant, debug: MutableMap<String, String>): Long? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["sleep_records_count"] = response.records.size.toString()
            debug["sleep_records_origins"] = response.records.map { it.metadata.dataOrigin.packageName }.distinct().joinToString(",").ifBlank { "none" }
            response.records.forEachIndexed { index, record ->
                if (index < 3) {
                    debug["sleep_${index}_start"] = record.startTime.toString()
                    debug["sleep_${index}_end"] = record.endTime.toString()
                }
            }
            response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        } catch (e: Throwable) {
            debug["sleep_error"] = shortError(e)
            null
        }
    }

    private suspend fun readExerciseCount(start: Instant, end: Instant, debug: MutableMap<String, String>): Long? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["exercise_records_count"] = response.records.size.toString()
            response.records.size.toLong()
        } catch (e: Throwable) {
            debug["exercise_count_error"] = shortError(e)
            null
        }
    }

    private suspend fun readExerciseMinutes(start: Instant, end: Instant, debug: MutableMap<String, String>): Long? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        } catch (e: Throwable) {
            debug["exercise_minutes_error"] = shortError(e)
            null
        }
    }

    private suspend fun readLatestRespiratoryRate(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["respiratory_rate_records_count"] = response.records.size.toString()
            response.records.maxByOrNull { it.time }?.rate
        } catch (e: Throwable) {
            debug["respiratory_rate_error"] = shortError(e)
            null
        }
    }

    private suspend fun readLatestOxygenSaturationPercent(start: Instant, end: Instant, debug: MutableMap<String, String>): Double? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            debug["oxygen_saturation_records_count"] = response.records.size.toString()
            response.records.maxByOrNull { it.time }?.percentage?.value
        } catch (e: Throwable) {
            debug["oxygen_saturation_error"] = shortError(e)
            null
        }
    }

    private fun shortError(e: Throwable): String {
        val name = e.javaClass.simpleName.ifBlank { e.javaClass.name.substringAfterLast('.') }
        val message = e.message?.take(140)
        return if (message.isNullOrBlank()) name else "$name: $message"
    }
}

private data class HeartRateStats(
    val avg: Long? = null,
    val min: Long? = null,
    val max: Long? = null
)

data class HealthSnapshot(
    val generatedAt: String,
    val healthConnectAvailable: Boolean,
    val grantedPermissions: Int,
    val requestedPermissions: Int,
    val stepsToday: Long? = null,
    val distanceMetersToday: Double? = null,
    val activeCaloriesKcalToday: Double? = null,
    val totalCaloriesKcalToday: Double? = null,
    val floorsClimbedToday: Double? = null,
    val heartRateAvgBpm: Long? = null,
    val heartRateMinBpm: Long? = null,
    val heartRateMaxBpm: Long? = null,
    val latestWeightKg: Double? = null,
    val latestHeightCm: Double? = null,
    val sleepMinutesLastNight: Long? = null,
    val exerciseSessionsToday: Long? = null,
    val exerciseMinutesToday: Long? = null,
    val latestRespiratoryRateBpm: Double? = null,
    val latestOxygenSaturationPercent: Double? = null,
    val debug: Map<String, String> = emptyMap()
)
