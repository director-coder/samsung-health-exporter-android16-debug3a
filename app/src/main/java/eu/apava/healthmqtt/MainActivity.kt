package eu.apava.healthmqtt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val context = this
        val store = remember { SettingsStore(context) }
        val reader = remember { HealthConnectReader(context) }
        var settings by remember { mutableStateOf(store.load()) }
        var status by remember { mutableStateOf("Ready") }
        var lastSummary by remember { mutableStateOf("not synced yet") }
        val scope = rememberCoroutineScope()

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            status = "Health Connect permissions: ${granted.intersect(HealthConnectReader.requiredPermissions).size}/${HealthConnectReader.requiredPermissions.size} granted"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Health Connect → MQTT", style = MaterialTheme.typography.headlineSmall)
            Text("Android 16 build. Reads Health Connect vitals and publishes them to Home Assistant via MQTT.")

            OutlinedTextField(
                value = settings.brokerUri,
                onValueChange = { settings = settings.copy(brokerUri = it) },
                label = { Text("MQTT broker URI") },
                placeholder = { Text("tcp://192.168.1.10:1883") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.username,
                onValueChange = { settings = settings.copy(username = it) },
                label = { Text("MQTT username") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.password,
                onValueChange = { settings = settings.copy(password = it) },
                label = { Text("MQTT password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.baseTopic,
                onValueChange = { settings = settings.copy(baseTopic = it) },
                label = { Text("Base topic") },
                placeholder = { Text("home/health/pavel") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.deviceName,
                onValueChange = { settings = settings.copy(deviceName = it) },
                label = { Text("Device name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.syncIntervalMinutes.toString(),
                onValueChange = { value ->
                    val minutes = value.toLongOrNull() ?: 60L
                    settings = settings.copy(syncIntervalMinutes = minutes.coerceAtLeast(15L))
                },
                label = { Text("Sync interval minutes, minimum 15") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    store.save(settings)
                    status = "Settings saved"
                }) {
                    Text("Save")
                }
                Button(onClick = {
                    try {
                        val sdkStatus = reader.sdkStatus()
                        if (!reader.isAvailable()) {
                            status = "Health Connect is not available. SDK status=$sdkStatus"
                        } else {
                            status = "Opening Health Connect permission screen..."
                            permissionLauncher.launch(HealthConnectReader.requiredPermissions)
                        }
                    } catch (e: Throwable) {
                        status = "Permission launch error: ${e.message ?: e.javaClass.simpleName}"
                    }
                }) {
                    Text("Grant permission")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    store.save(settings)
                    scope.launch {
                        status = "Syncing..."
                        val result = runSyncNow(context, settings)
                        status = result.message
                        if (result.summary != null) lastSummary = result.summary
                    }
                }) {
                    Text("Sync now")
                }
                Button(onClick = {
                    store.save(settings)
                    SyncScheduler.schedule(context, settings.syncIntervalMinutes)
                    status = "Scheduled every ${settings.syncIntervalMinutes.coerceAtLeast(15L)} minutes"
                }) {
                    Text("Schedule")
                }
                Button(onClick = {
                    store.save(settings)
                    scope.launch {
                        status = "Publishing test MQTT payload..."
                        status = withContext(Dispatchers.IO) {
                            try {
                                MqttPublisher(settings).publishTest()
                                "Published test MQTT payload"
                            } catch (e: Throwable) {
                                "MQTT test error: ${e.message ?: e.javaClass.simpleName}"
                            }
                        }
                    }
                }) {
                    Text("Test MQTT")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Last sync summary: $lastSummary")
            Text("Status: $status")
        }
    }

    data class SyncResult(val message: String, val summary: String? = null)

    private suspend fun runSyncNow(context: android.content.Context, settings: AppSettings): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val reader = HealthConnectReader(context)
                val sdkStatus = reader.sdkStatus()
                if (!reader.isAvailable()) {
                    return@withContext SyncResult("Health Connect is not available. SDK status=$sdkStatus")
                }
                val snapshot = reader.readSnapshot()
                MqttPublisher(settings).publishSnapshot(snapshot)
                val stepsText = snapshot.stepsToday?.toString() ?: "unknown"
                val activeCaloriesText = snapshot.activeCaloriesKcalToday?.let { "%.2f".format(java.util.Locale.US, it) } ?: "unknown"
                val heartRateText = snapshot.heartRateAvgBpm?.toString() ?: "unknown"
                val sleepText = snapshot.sleepMinutesLastNight?.toString() ?: "unknown"
                val rrText = snapshot.latestRespiratoryRateBpm?.let { "%.2f".format(java.util.Locale.US, it) } ?: "unknown"
                val spo2Text = snapshot.latestOxygenSaturationPercent?.let { "%.2f".format(java.util.Locale.US, it) } ?: "unknown"
                val stepsSource = snapshot.debug["steps_source"] ?: "none"
                val activeSource = snapshot.debug["active_calories_source"] ?: "none"
                val debugErrors = snapshot.debug.filterKeys { it.endsWith("_error") }.entries.joinToString("; ") { "${it.key}=${it.value}" }
                val errorText = if (debugErrors.isBlank()) "no reader errors" else debugErrors.take(260)
                val summary = "steps=$stepsText ($stepsSource), active_kcal=$activeCaloriesText ($activeSource), hr=$heartRateText, rr=$rrText, spo2=$spo2Text%, sleep=$sleepText min, permissions=${snapshot.grantedPermissions}/${snapshot.requestedPermissions}, $errorText"
                SyncResult("Published Health Connect snapshot", summary)
            } catch (e: Throwable) {
                SyncResult("Error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
