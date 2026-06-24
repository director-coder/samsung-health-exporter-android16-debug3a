package eu.apava.healthmqtt

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    fun schedule(context: Context, intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            intervalMinutes.coerceAtLeast(15L),
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "health_connect_mqtt_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
