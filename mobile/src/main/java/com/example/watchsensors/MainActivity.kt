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
    private val allKnownSensors = listOf(
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
        "Heart Rate",
        "Heart Beat",
        "Off-Body Detect",
        "Significant Motion",
        "Stationary Detect",
        "Motion Detect"
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

                if (path == "/sensor_list") {
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
            }
        }
    }

    private fun updateDisplay() {
        val displayText = StringBuilder()
        displayText.append("╔════════════════════════════════════════════╗\n")
        displayText.append("║   SAMSUNG WATCH7 SENSOR AVAILABILITY      ║\n")
        displayText.append("╚════════════════════════════════════════════╝\n\n")

        displayText.append("Total Sensors Found: ${availableSensors.size}\n\n")

        displayText.append("┌──────────────────────────────────────┐\n")
        displayText.append("│          AVAILABLE SENSORS           │\n")
        displayText.append("└──────────────────────────────────────┘\n\n")

        allKnownSensors.forEach { sensorName ->
            val status = if (availableSensors.contains(sensorName)) {
                "✓ PRESENT"
            } else {
                "✗ NOT PRESENT"
            }

            displayText.append("${status.padEnd(15)} │ $sensorName\n")
        }

        displayText.append("\n")
        displayText.append("═══════════════════════════════════════\n")
        displayText.append("Legend: ✓ = Available  ✗ = Not Available\n")

        sensorDataText.text = displayText.toString()
    }
}