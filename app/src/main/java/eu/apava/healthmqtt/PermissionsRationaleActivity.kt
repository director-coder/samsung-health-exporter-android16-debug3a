package eu.apava.healthmqtt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Health MQTT permissions",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "This app reads your step count from Health Connect and sends it to your own Home Assistant MQTT broker."
                        )
                        Text(
                            text = "It requests read access only for Steps. No health data is sent anywhere except the MQTT broker you configure."
                        )
                        Button(onClick = { finish() }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
