package eu.apava.healthmqtt

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.time.Instant
import java.util.Locale
import java.util.UUID

class MqttPublisher(private val settings: AppSettings) {
    fun publishSnapshot(snapshot: HealthSnapshot) {
        withClient { client ->
            publishAllDiscovery(client)
            publish(client, "${settings.baseTopic}/availability", "online", retained = true)
            publish(client, "${settings.baseTopic}/last_sync", snapshot.generatedAt, retained = true)
            publish(client, "${settings.baseTopic}/health_connect_available/state", if (snapshot.healthConnectAvailable) "ON" else "OFF", retained = true)
            publish(client, "${settings.baseTopic}/permission_status/state", "${snapshot.grantedPermissions}/${snapshot.requestedPermissions}", retained = true)

            publishNullable(client, "steps_today/state", snapshot.stepsToday)
            publishNullable(client, "distance_meters_today/state", snapshot.distanceMetersToday)
            publishNullable(client, "active_calories_kcal_today/state", snapshot.activeCaloriesKcalToday)
            publishNullable(client, "total_calories_kcal_today/state", snapshot.totalCaloriesKcalToday)
            publishNullable(client, "floors_climbed_today/state", snapshot.floorsClimbedToday)
            publishNullable(client, "heart_rate_avg_bpm/state", snapshot.heartRateAvgBpm)
            publishNullable(client, "heart_rate_min_bpm/state", snapshot.heartRateMinBpm)
            publishNullable(client, "heart_rate_max_bpm/state", snapshot.heartRateMaxBpm)
            publishNullable(client, "latest_weight_kg/state", snapshot.latestWeightKg)
            publishNullable(client, "latest_height_cm/state", snapshot.latestHeightCm)
            publishNullable(client, "sleep_minutes_last_night/state", snapshot.sleepMinutesLastNight)
            publishNullable(client, "exercise_sessions_today/state", snapshot.exerciseSessionsToday)
            publishNullable(client, "exercise_minutes_today/state", snapshot.exerciseMinutesToday)
            publishNullable(client, "latest_respiratory_rate_bpm/state", snapshot.latestRespiratoryRateBpm)
            publishNullable(client, "latest_oxygen_saturation_percent/state", snapshot.latestOxygenSaturationPercent)

            val debugState = snapshot.debug.entries.joinToString("; ") { "${it.key}=${it.value}" }.take(900)
            publish(client, "${settings.baseTopic}/debug/state", debugState.ifBlank { "no_debug" }, retained = true)
            snapshot.debug.forEach { (key, value) ->
                publish(client, "${settings.baseTopic}/debug/$key", value, retained = true)
            }
        }
    }

    fun publishTest() {
        val now = Instant.now().toString()
        val snapshot = HealthSnapshot(
            generatedAt = now,
            healthConnectAvailable = true,
            grantedPermissions = 0,
            requestedPermissions = HealthConnectReader.requiredPermissions.size,
            stepsToday = 12345,
            distanceMetersToday = 987.6,
            activeCaloriesKcalToday = 123.4,
            totalCaloriesKcalToday = 2100.0,
            floorsClimbedToday = 3.0,
            heartRateAvgBpm = 72,
            heartRateMinBpm = 55,
            heartRateMaxBpm = 118,
            latestWeightKg = 80.5,
            latestHeightCm = 180.0,
            sleepMinutesLastNight = 430,
            exerciseSessionsToday = 1,
            exerciseMinutesToday = 32,
            latestRespiratoryRateBpm = 14.5,
            latestOxygenSaturationPercent = 98.0,
            debug = mapOf(
                "version" to "test",
                "steps_source" to "test_payload",
                "steps_records_count" to "1"
            )
        )
        publishSnapshot(snapshot)
    }

    private fun withClient(block: (MqttClient) -> Unit) {
        val clientId = "health-mqtt-" + UUID.randomUUID().toString().take(8)
        val client = MqttClient(settings.brokerUri, clientId, MemoryPersistence())
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            if (settings.username.isNotBlank()) {
                userName = settings.username
                password = settings.password.toCharArray()
            }
        }

        client.connect(options)
        try {
            block(client)
        } finally {
            client.disconnect()
            client.close()
        }
    }

    private fun publishAllDiscovery(client: MqttClient) {
        publishSensorConfig(client, "steps_today", "Steps Today", "steps", "mdi:walk")
        publishSensorConfig(client, "distance_meters_today", "Distance Today", "m", "mdi:map-marker-distance")
        publishSensorConfig(client, "active_calories_kcal_today", "Active Calories Today", "kcal", "mdi:fire")
        publishSensorConfig(client, "total_calories_kcal_today", "Total Calories Today", "kcal", "mdi:food-apple")
        publishSensorConfig(client, "floors_climbed_today", "Floors Climbed Today", null, "mdi:stairs")
        publishSensorConfig(client, "heart_rate_avg_bpm", "Heart Rate Avg", "bpm", "mdi:heart-pulse")
        publishSensorConfig(client, "heart_rate_min_bpm", "Heart Rate Min", "bpm", "mdi:heart-pulse")
        publishSensorConfig(client, "heart_rate_max_bpm", "Heart Rate Max", "bpm", "mdi:heart-pulse")
        publishSensorConfig(client, "latest_weight_kg", "Latest Weight", "kg", "mdi:scale-bathroom")
        publishSensorConfig(client, "latest_height_cm", "Latest Height", "cm", "mdi:human-male-height")
        publishSensorConfig(client, "sleep_minutes_last_night", "Sleep Last Night", "min", "mdi:sleep")
        publishSensorConfig(client, "exercise_sessions_today", "Exercise Sessions Today", null, "mdi:run")
        publishSensorConfig(client, "exercise_minutes_today", "Exercise Minutes Today", "min", "mdi:timer-outline")
        publishSensorConfig(client, "latest_respiratory_rate_bpm", "Latest Respiratory Rate", "breaths/min", "mdi:lungs")
        publishSensorConfig(client, "latest_oxygen_saturation_percent", "Latest Blood Oxygen", "%", "mdi:water-percent")
        publishSensorConfig(client, "debug_state", "Debug State", null, "mdi:bug-outline", stateTopicSuffix = "debug/state")
        publishSensorConfig(client, "last_sync", "Last Sync", null, "mdi:clock-outline", deviceClass = "timestamp", stateTopicSuffix = "last_sync")
        publishSensorConfig(client, "permission_status", "Permission Status", null, "mdi:shield-check", stateTopicSuffix = "permission_status/state")
        publishBinarySensorConfig(client, "health_connect_available", "Health Connect Available", "${settings.baseTopic}/health_connect_available/state")
    }

    private fun publishSensorConfig(
        client: MqttClient,
        objectId: String,
        label: String,
        unit: String?,
        icon: String,
        deviceClass: String? = null,
        stateTopicSuffix: String = "$objectId/state"
    ) {
        val uniqueId = "health_mqtt_${settings.baseTopic.replace('/', '_')}_$objectId"
        val configTopic = "homeassistant/sensor/health_mqtt_$objectId/config"
        val unitLine = unit?.let { ",\n  \"unit_of_measurement\":\"${escape(it)}\"" } ?: ""
        val deviceClassLine = deviceClass?.let { ",\n  \"device_class\":\"${escape(it)}\"" } ?: ""
        val json = """
            {
              "name":"${escape(settings.deviceName)} $label",
              "unique_id":"${escape(uniqueId)}",
              "state_topic":"${escape(settings.baseTopic)}/$stateTopicSuffix",
              "availability_topic":"${escape(settings.baseTopic)}/availability",
              "icon":"${escape(icon)}"$unitLine$deviceClassLine,
              "device":${deviceJson()}
            }
        """.trimIndent()
        publish(client, configTopic, json, retained = true)
    }

    private fun publishBinarySensorConfig(
        client: MqttClient,
        objectId: String,
        label: String,
        stateTopic: String
    ) {
        val uniqueId = "health_mqtt_${settings.baseTopic.replace('/', '_')}_$objectId"
        val configTopic = "homeassistant/binary_sensor/health_mqtt_$objectId/config"
        val json = """
            {
              "name":"${escape(settings.deviceName)} $label",
              "unique_id":"${escape(uniqueId)}",
              "state_topic":"${escape(stateTopic)}",
              "availability_topic":"${escape(settings.baseTopic)}/availability",
              "payload_on":"ON",
              "payload_off":"OFF",
              "device_class":"connectivity",
              "device":${deviceJson()}
            }
        """.trimIndent()
        publish(client, configTopic, json, retained = true)
    }

    private fun deviceJson(): String {
        return """
            {
              "identifiers":["${escape(settings.baseTopic)}"],
              "name":"${escape(settings.deviceName)}",
              "manufacturer":"APAVA",
              "model":"Health Connect MQTT Android16"
            }
        """.trimIndent()
    }

    private fun publishNullable(client: MqttClient, suffix: String, value: Number?) {
        val payload = when (value) {
            null -> "unknown"
            is Double -> String.format(Locale.US, "%.2f", value)
            is Float -> String.format(Locale.US, "%.2f", value)
            else -> value.toString()
        }
        publish(client, "${settings.baseTopic}/$suffix", payload, retained = true)
    }

    private fun publish(client: MqttClient, topic: String, payload: String, retained: Boolean) {
        val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
            qos = 1
            isRetained = retained
        }
        client.publish(topic, message)
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
