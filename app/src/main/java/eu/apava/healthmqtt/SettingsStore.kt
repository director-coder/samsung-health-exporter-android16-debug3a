package eu.apava.healthmqtt

import android.content.Context

data class AppSettings(
    val brokerUri: String = "tcp://192.168.1.10:1883",
    val username: String = "",
    val password: String = "",
    val baseTopic: String = "home/health/pavel",
    val deviceName: String = "Pavel Phone",
    val syncIntervalMinutes: Long = 60L
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("health_mqtt_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            brokerUri = prefs.getString("brokerUri", null) ?: "tcp://192.168.1.10:1883",
            username = prefs.getString("username", null) ?: "",
            password = prefs.getString("password", null) ?: "",
            baseTopic = prefs.getString("baseTopic", null) ?: "home/health/pavel",
            deviceName = prefs.getString("deviceName", null) ?: "Pavel Phone",
            syncIntervalMinutes = prefs.getLong("syncIntervalMinutes", 60L).coerceAtLeast(15L)
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("brokerUri", settings.brokerUri.trim())
            .putString("username", settings.username.trim())
            .putString("password", settings.password)
            .putString("baseTopic", settings.baseTopic.trim().trim('/'))
            .putString("deviceName", settings.deviceName.trim())
            .putLong("syncIntervalMinutes", settings.syncIntervalMinutes.coerceAtLeast(15L))
            .apply()
    }
}
