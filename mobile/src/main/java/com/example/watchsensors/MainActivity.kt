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
import android.util.Log

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {
    private lateinit var dataClient: DataClient
    private lateinit var sensorDataText: TextView
    private val availableSensors = mutableSetOf<String>()

    // Comprehensive list of all possible sensors
    private val allKnownSensors = listOf(
        // Standard Hardware Sensors
        "Accelerometer",
        "Magnetometer",
        "Gyroscope",
        "Light Sensor",
        "Barometer",
        "Proximity",
        "Gravity",
        "Linear Acceleration",
        "Rotation Vector",
        "Humidity",
        "Temperature",
        "Magnetometer (Uncal)",
        "Game Rotation",
        "Gyroscope (Uncal)",
        "Step Detector",
        "Step Counter",
        "Heart Rate (Hardware)",
        "Heart Beat",
        "Off-Body Detect",
        "Significant Motion",
        "Stationary Detect",
        "Motion Detect",

        // Samsung Health SDK Sensors
        "Heart Rate (Continuous) (Samsung Health)",
        "Blood Oxygen SpO2 (Samsung Health)",
        "ECG (Electrocardiogram) (Samsung Health)",
        "BIA (Body Composition) (Samsung Health)",
        "Accelerometer (Samsung) (Samsung Health)",
        "PPG (Photoplethysmogram) Continuous (Samsung Health)",
        "PPG (Photoplethysmogram) On-Demand (Samsung Health)",
        "Skin Temperature (On-Demand) (Samsung Health)",
        "Skin Temperature (Continuous) (Samsung Health)",
        "Sweat Loss (Samsung Health)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        sensorDataText = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "Waiting for watch data..."
        }
        scrollView.addView(sensorDataText)
        setContentView(scrollView)

        dataClient = Wearable.getDataClient(this)
        Log.d("PhoneSensors", "Phone app started, waiting for data...")
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("PhoneSensors", "Data received from watch!")

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: continue
                Log.d("PhoneSensors", "Path: $path")

                when (path) {
                    "/sensor_list" -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val sensors = dataMap.getStringArray("available_sensors")

                        Log.d("PhoneSensors", "Received ${sensors?.size} sensors")

                        if (sensors != null) {
                            availableSensors.clear()
                            availableSensors.addAll(sensors)

                            runOnUiThread {
                                updateDisplay()
                            }
                        }
                    }

                    "/sensor_data" -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val sensorName = dataMap.getString("sensor_name")
                        val sensorData = dataMap.getString("sensor_data")
                        val timestamp = dataMap.getLong("timestamp")

                        Log.d("PhoneSensors", "Live data from $sensorName: $sensorData")

                        runOnUiThread {
                            displayLiveSensorData(sensorName, sensorData, timestamp)
                        }
                    }
                }
            }
        }
    }

    private fun displayLiveSensorData(sensorName: String?, data: String?, timestamp: Long) {
        if (sensorName == null || data == null) return

        val displayText = StringBuilder()
        displayText.append("╔════════════════════════════════════════════╗\n")
        displayText.append("║         LIVE SENSOR DATA                  ║\n")
        displayText.append("╚════════════════════════════════════════════╝\n\n")

        displayText.append("Sensor: $sensorName\n\n")
        displayText.append("─────────────────────────────────────\n\n")
        displayText.append(data)
        displayText.append("\n\n─────────────────────────────────────\n")

        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
        displayText.append("\nLast Update: $timeStr")

        sensorDataText.text = displayText.toString()
    }

    private fun updateDisplay() {
        val displayText = StringBuilder()

        // Header
        displayText.append("╔════════════════════════════════════════════╗\n")
        displayText.append("║   SAMSUNG WATCH7 SENSOR AVAILABILITY      ║\n")
        displayText.append("╚════════════════════════════════════════════╝\n\n")

        displayText.append("Total Sensors Found: ${availableSensors.size}\n\n")

        // Categorize sensors
        val hardwareSensors = mutableListOf<String>()
        val samsungHealthSensors = mutableListOf<String>()
        val errorMessages = mutableListOf<String>()

        availableSensors.forEach { sensor ->
            when {
                sensor.contains("Samsung Health") -> samsungHealthSensors.add(sensor)
                sensor.contains("Error") || sensor.contains("Failed") ||
                        sensor.contains("outdated") || sensor.contains("Not installed") -> {
                    errorMessages.add(sensor)
                }
                else -> hardwareSensors.add(sensor)
            }
        }

        // Display Hardware Sensors
        if (hardwareSensors.isNotEmpty()) {
            displayText.append("┌──────────────────────────────────────┐\n")
            displayText.append("│      HARDWARE SENSORS (Android)      │\n")
            displayText.append("└──────────────────────────────────────┘\n\n")

            hardwareSensors.sorted().forEach { sensor ->
                displayText.append("  ✓ $sensor\n")
            }
            displayText.append("\n")
        }

        // Display Samsung Health Sensors
        if (samsungHealthSensors.isNotEmpty()) {
            displayText.append("┌──────────────────────────────────────┐\n")
            displayText.append("│     SAMSUNG HEALTH SDK SENSORS       │\n")
            displayText.append("└──────────────────────────────────────┘\n\n")

            samsungHealthSensors.sorted().forEach { sensor ->
                // Remove the "(Samsung Health)" suffix for cleaner display
                val cleanName = sensor.replace(" (Samsung Health)", "")
                displayText.append("  ⚕ $cleanName\n")
            }
            displayText.append("\n")
        }

        // Display Errors/Warnings
        if (errorMessages.isNotEmpty()) {
            displayText.append("┌──────────────────────────────────────┐\n")
            displayText.append("│         WARNINGS / ERRORS            │\n")
            displayText.append("└──────────────────────────────────────┘\n\n")

            errorMessages.forEach { error ->
                displayText.append("  ⚠ $error\n")
            }
            displayText.append("\n")
        }

        // Summary
        displayText.append("═══════════════════════════════════════\n")
        displayText.append("Summary:\n")
        displayText.append("  Hardware: ${hardwareSensors.size}\n")
        displayText.append("  Samsung Health: ${samsungHealthSensors.size}\n")
        if (errorMessages.isNotEmpty()) {
            displayText.append("  Warnings: ${errorMessages.size}\n")
        }
        displayText.append("\n")
        displayText.append("Legend:\n")
        displayText.append("  ✓ = Hardware sensor\n")
        displayText.append("  ⚕ = Samsung Health SDK sensor\n")

        sensorDataText.text = displayText.toString()
    }
}