package com.example.watchsensors

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        statusText = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            text = "Starting..."
        }

        scrollView.addView(statusText)
        setContentView(scrollView)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Get all sensors
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d("WatchSensors", "Found ${sensors.size} sensors")

        statusText.text = "Found ${sensors.size} sensors\n\nSending to phone..."

        // Send sensor list to phone
        sendSensorListToPhone(sensors)
    }

    private fun sendSensorListToPhone(sensors: List<Sensor>) {
        val sensorNames = sensors.map { getSensorTypeName(it.type) }.toTypedArray()

        Log.d("WatchSensors", "Preparing to send ${sensorNames.size} sensor names")

        val dataClient = Wearable.getDataClient(this)

        val request = PutDataMapRequest.create("/sensor_list").apply {
            dataMap.putStringArray("available_sensors", sensorNames)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnSuccessListener {
                Log.d("WatchSensors", "✓ Data sent successfully!")
                statusText.text = "✓ Sent ${sensorNames.size} sensors\nto phone"
            }
            .addOnFailureListener { e ->
                Log.e("WatchSensors", "✗ Failed to send data: ${e.message}")
                statusText.text = "✗ Failed to send:\n${e.message}"
            }
    }

    private fun getSensorTypeName(type: Int): String {
        return when (type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_LIGHT -> "Light Sensor"
            Sensor.TYPE_PRESSURE -> "Barometer"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_GRAVITY -> "Gravity"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (Uncal)"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (Uncal)"
            Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
            Sensor.TYPE_STEP_COUNTER -> "Step Counter"
            Sensor.TYPE_HEART_RATE -> "Heart Rate"
            Sensor.TYPE_HEART_BEAT -> "Heart Beat"
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Off-Body Detect"
            else -> "Sensor Type $type"
        }
    }
}