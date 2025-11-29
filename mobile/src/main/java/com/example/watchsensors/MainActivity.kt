package com.example.watchsensors.phone

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.NodeClient
import android.util.Log
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {
    private lateinit var dataClient: DataClient
    private lateinit var nodeClient: NodeClient
    private lateinit var sensorDataText: TextView
    private val sensorReadings = mutableMapOf<String, SensorReading>()
    private var lastUpdate = 0L

    data class SensorReading(
        val name: String,
        val values: String,
        val timestamp: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0a0a0a"))
        }

        sensorDataText = TextView(this).apply {
            setPadding(40, 40, 40, 40)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(android.graphics.Color.parseColor("#00ff00"))
            text = "Initializing...\n\nWaiting for sensor data from watch..."
        }

        scrollView.addView(sensorDataText)
        setContentView(scrollView)

        dataClient = Wearable.getDataClient(this)
        nodeClient = Wearable.getNodeClient(this)

        Log.d("PhoneSensorMonitor", "Phone app started")
        checkWatchConnection()
    }

    private fun checkWatchConnection() {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            runOnUiThread {
                if (nodes.isNotEmpty()) {
                    val nodeNames = nodes.joinToString(", ") { it.displayName }
                    sensorDataText.text = "╔═══════════════════════════════════════╗\n" +
                            "║  SAMSUNG WATCH7 LIVE SENSOR MONITOR  ║\n" +
                            "╚═══════════════════════════════════════╝\n\n" +
                            "✓ Connected to: $nodeNames\n" +
                            "⏳ Waiting for sensor data...\n\n" +
                            "Make sure watch app is running!"
                } else {
                    sensorDataText.text = "✗ No watch connected\n\n" +
                            "Please pair your watch first!"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
        checkWatchConnection()
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: continue

                if (path == "/sensor_data") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val timestamp = dataMap.getLong("timestamp", 0)

                    // Get all sensor readings from the dataMap
                    val keySet = dataMap.keySet()

                    keySet.forEach { key ->
                        if (key != "timestamp") {
                            val values = dataMap.getString(key, "")
                            if (values.isNotEmpty()) {
                                sensorReadings[key] = SensorReading(key, values, timestamp)
                            }
                        }
                    }

                    lastUpdate = timestamp

                    runOnUiThread {
                        updateDisplay()
                    }
                }
            }
        }
    }

    private fun parseSensorData(dataStr: String, timestamp: Long) {
        // This method is no longer needed but kept for compatibility
    }

    private fun updateDisplay() {
        if (sensorReadings.isEmpty()) {
            return
        }

        val displayText = StringBuilder()

        // Header
        displayText.append("╔═══════════════════════════════════════╗\n")
        displayText.append("║  SAMSUNG WATCH7 LIVE SENSOR MONITOR  ║\n")
        displayText.append("╚═══════════════════════════════════════╝\n\n")

        // Status
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val updateTime = dateFormat.format(Date(lastUpdate))
        displayText.append("✓ STREAMING LIVE DATA\n")
        displayText.append("Last Update: $updateTime\n")
        displayText.append("Active Sensors: ${sensorReadings.size}\n\n")

        displayText.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

        // Group sensors by category
        val motionSensors = listOf("Accelerometer", "Gyroscope", "Gyro (Uncal)",
            "Linear Accel", "Gravity")
        val orientationSensors = listOf("Magnetometer", "Mag (Uncal)", "Rotation",
            "Game Rotation")
        val environmentalSensors = listOf("Light", "Barometer", "Temperature", "Humidity")
        val positionSensors = listOf("Step Detect", "Step Count", "Off-Body")
        val healthSensors = listOf("Heart Rate", "Heart Beat")

        // Motion Sensors
        displayText.append("┌─ MOTION SENSORS ─────────────────────┐\n")
        motionSensors.forEach { sensor ->
            sensorReadings[sensor]?.let { reading ->
                displayText.append("│ ${sensor.padEnd(18)} ${formatValues(reading.values)}\n")
            }
        }
        displayText.append("└──────────────────────────────────────┘\n\n")

        // Orientation Sensors
        displayText.append("┌─ ORIENTATION SENSORS ────────────────┐\n")
        orientationSensors.forEach { sensor ->
            sensorReadings[sensor]?.let { reading ->
                displayText.append("│ ${sensor.padEnd(18)} ${formatValues(reading.values)}\n")
            }
        }
        displayText.append("└──────────────────────────────────────┘\n\n")

        // Environmental Sensors
        if (environmentalSensors.any { sensorReadings.containsKey(it) }) {
            displayText.append("┌─ ENVIRONMENTAL SENSORS ──────────────┐\n")
            environmentalSensors.forEach { sensor ->
                sensorReadings[sensor]?.let { reading ->
                    displayText.append("│ ${sensor.padEnd(18)} ${formatValues(reading.values)}\n")
                }
            }
            displayText.append("└──────────────────────────────────────┘\n\n")
        }

        // Position Sensors
        displayText.append("┌─ POSITION SENSORS ───────────────────┐\n")
        positionSensors.forEach { sensor ->
            sensorReadings[sensor]?.let { reading ->
                displayText.append("│ ${sensor.padEnd(18)} ${formatValues(reading.values)}\n")
            }
        }
        displayText.append("└──────────────────────────────────────┘\n\n")

        // Health Sensors
        displayText.append("┌─ HEALTH SENSORS ─────────────────────┐\n")
        healthSensors.forEach { sensor ->
            sensorReadings[sensor]?.let { reading ->
                displayText.append("│ ${sensor.padEnd(18)} ${formatValues(reading.values)}\n")
            }
        }
        displayText.append("└──────────────────────────────────────┘\n\n")

        displayText.append("═══════════════════════════════════════\n")
        displayText.append("💡 Move watch to see sensors update!\n")

        sensorDataText.text = displayText.toString()
    }

    private fun formatValues(values: String): String {
        // Truncate long value strings for display
        return if (values.length > 20) {
            values.substring(0, 17) + "..."
        } else {
            values.padEnd(20)
        }
    }
}