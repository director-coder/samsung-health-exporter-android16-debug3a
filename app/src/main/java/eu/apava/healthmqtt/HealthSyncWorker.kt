package eu.apava.healthmqtt

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HealthSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val settings = SettingsStore(applicationContext).load()
            val reader = HealthConnectReader(applicationContext)

            if (!reader.isAvailable()) {
                return Result.failure()
            }

            val snapshot = reader.readSnapshot()
            MqttPublisher(settings).publishSnapshot(snapshot)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
