package com.example.watchsensors

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.data.HealthTrackerType

class MainActivity : Activity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var statusText: TextView
    private lateinit var settingsButton: Button
    private var healthTrackingService: HealthTrackingService? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TAG = "WatchSensors"
    }

    // Connection listener for Samsung Health Service
    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.d(TAG, "Samsung Health Service connected")
            checkAllSensors()
        }

        override fun onConnectionEnded() {
            Log.d(TAG, "Samsung Health Service connection ended")
        }

        override fun onConnectionFailed(error: HealthTrackerException) {
            Log.e(TAG, "Samsung Health Service connection failed: ${error.errorCode}")
            val allSensors = mutableListOf<String>()

            // Still get hardware sensors even if Samsung Health fails
            val hardwareSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            allSensors.addAll(hardwareSensors.map { getSensorTypeName(it.type) })

            when (error.errorCode) {
                HealthTrackerException.OLD_PLATFORM_VERSION -> {
                    allSensors.add("Samsung Health: Platform outdated")
                }
                HealthTrackerException.PACKAGE_NOT_INSTALLED -> {
                    allSensors.add("Samsung Health: Not installed")
                }
                else -> {
                    allSensors.add("Samsung Health: Error ${error.errorCode}")
                }
            }

            // Try to resolve if possible
            if (error.hasResolution()) {
                error.resolve(this@MainActivity)
            }

            updateAndSendSensorList(allSensors)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val scrollView = ScrollView(this)

        statusText = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            text = "Starting..."
        }

        settingsButton = Button(this).apply {
            text = "Open Settings"
            visibility = android.view.View.GONE
            setOnClickListener {
                openAppSettings()
            }
        }

        scrollView.addView(statusText)
        container.addView(scrollView)
        container.addView(settingsButton)
        setContentView(container)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Check permissions
        if (!hasRequiredPermissions()) {
            requestPermissions()
        } else {
            initializeSensors()
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings: ${e.message}")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        val activityRecognition = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "BODY_SENSORS permission: $bodySensors")
        Log.d(TAG, "ACTIVITY_RECOGNITION permission: $activityRecognition")

        return bodySensors && activityRecognition
    }

    private fun requestPermissions() {
        Log.d(TAG, "Requesting permissions...")

        // Request permissions one at a time to ensure both dialogs appear
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
            Log.d(TAG, "Need to request BODY_SENSORS")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            Log.d(TAG, "Need to request ACTIVITY_RECOGNITION")
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting ${permissionsToRequest.size} permissions: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "All permissions already granted")
            initializeSensors()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d(TAG, "Permission results received:")
            permissions.forEachIndexed { index, permission ->
                val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "$permission: $granted")
            }

            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "All permissions granted, initializing sensors")
                settingsButton.visibility = android.view.View.GONE
                initializeSensors()
            } else {
                val deniedPermissions = mutableListOf<String>()
                permissions.forEachIndexed { index, permission ->
                    if (grantResults.getOrNull(index) != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(when(permission) {
                            Manifest.permission.BODY_SENSORS -> "Body Sensors (vital signs)"
                            Manifest.permission.ACTIVITY_RECOGNITION -> "Physical Activity"
                            else -> permission
                        })
                    }
                }

                val message = "Missing permissions:\n\n${deniedPermissions.joinToString("\n")}\n\n" +
                        "Please tap 'Open Settings' below,\n" +
                        "then enable:\n" +
                        "• Physical activity\n" +
                        "• Access sensor data about\n  your vital signs\n\n" +
                        "Then restart the app."

                Log.e(TAG, "Denied: $deniedPermissions")
                statusText.text = message
                settingsButton.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recheck permissions when returning from settings
        if (hasRequiredPermissions() && statusText.text.contains("Missing permissions")) {
            statusText.text = "Permissions granted!\nRestarting..."
            settingsButton.visibility = android.view.View.GONE
            initializeSensors()
        }
    }

    private fun initializeSensors() {
        try {
            // Initialize Samsung Health Tracking Service
            healthTrackingService = HealthTrackingService(connectionListener, this)
            healthTrackingService?.connectService()

            statusText.text = "Connecting to\nSamsung Health Service..."

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Samsung Health Service: ${e.message}")

            // If Samsung Health fails, still show hardware sensors
            val hardwareSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            val sensorList = hardwareSensors.map { getSensorTypeName(it.type) }.toMutableList()
            sensorList.add("Samsung Health SDK: ${e.message}")

            updateAndSendSensorList(sensorList)
        }
    }

    private fun checkAllSensors() {
        val allSensors = mutableListOf<String>()

        // Get standard hardware sensors
        val hardwareSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d(TAG, "Found ${hardwareSensors.size} hardware sensors")
        allSensors.addAll(hardwareSensors.map { getSensorTypeName(it.type) })

        // Check Samsung Health sensors
        healthTrackingService?.let { service ->
            try {
                val trackingCapability = service.trackingCapability
                val availableTrackers = trackingCapability.supportHealthTrackerTypes

                Log.d(TAG, "Available Samsung Health trackers: ${availableTrackers.size}")

                // Check each common tracker type
                checkTrackerType(HealthTrackerType.HEART_RATE_CONTINUOUS, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.SPO2_ON_DEMAND, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.ECG_ON_DEMAND, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.BIA_ON_DEMAND, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.ACCELEROMETER_CONTINUOUS, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.PPG_CONTINUOUS, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.PPG_ON_DEMAND, availableTrackers, allSensors)
                checkTrackerType(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND, availableTrackers, allSensors)

                // Try newer tracker types (may not be in SDK 1.4.1)
                try {
                    checkTrackerType(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS, availableTrackers, allSensors)
                } catch (e: Exception) {
                    Log.d(TAG, "SKIN_TEMPERATURE_CONTINUOUS not available in this SDK version")
                }

                try {
                    checkTrackerType(HealthTrackerType.SWEAT_LOSS, availableTrackers, allSensors)
                } catch (e: Exception) {
                    Log.d(TAG, "SWEAT_LOSS not available in this SDK version")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking Samsung Health sensors: ${e.message}")
                allSensors.add("Samsung Health: Error checking sensors")
            }
        }

        updateAndSendSensorList(allSensors)
    }

    private fun checkTrackerType(
        type: HealthTrackerType,
        availableTrackers: List<HealthTrackerType>,
        sensorList: MutableList<String>
    ) {
        if (availableTrackers.contains(type)) {
            val trackerName = getTrackerTypeName(type)
            sensorList.add("$trackerName (Samsung Health)")
            Log.d(TAG, "Supported: $trackerName")
        }
    }

    private fun updateAndSendSensorList(sensors: List<String>) {
        runOnUiThread {
            statusText.text = "Found ${sensors.size} sensors\n\nSending to phone..."
            sendSensorListToPhone(sensors)
        }
    }

    private fun sendSensorListToPhone(sensors: List<String>) {
        val sensorArray = sensors.toTypedArray()

        Log.d(TAG, "Preparing to send ${sensorArray.size} sensor names")

        val dataClient = Wearable.getDataClient(this)

        val request = PutDataMapRequest.create("/sensor_list").apply {
            dataMap.putStringArray("available_sensors", sensorArray)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnSuccessListener {
                Log.d(TAG, "✓ Data sent successfully!")
                statusText.text = "✓ Sent ${sensorArray.size} sensors\nto phone"
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "✗ Failed to send data: ${e.message}")
                statusText.text = "✗ Failed to send:\n${e.message}"
            }
    }

    private fun getTrackerTypeName(type: HealthTrackerType): String {
        return when (type) {
            HealthTrackerType.HEART_RATE_CONTINUOUS -> "Heart Rate (Continuous)"
            HealthTrackerType.SPO2_ON_DEMAND -> "Blood Oxygen SpO2"
            HealthTrackerType.ECG_ON_DEMAND -> "ECG (Electrocardiogram)"
            HealthTrackerType.BIA_ON_DEMAND -> "BIA (Body Composition)"
            HealthTrackerType.ACCELEROMETER_CONTINUOUS -> "Accelerometer (Samsung)"
            HealthTrackerType.PPG_CONTINUOUS -> "PPG (Photoplethysmogram) Continuous"
            HealthTrackerType.PPG_ON_DEMAND -> "PPG (Photoplethysmogram) On-Demand"
            HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS -> "Skin Temperature (Continuous)"
            HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND -> "Skin Temperature (On-Demand)"
            HealthTrackerType.SWEAT_LOSS -> "Sweat Loss"
            else -> "Unknown Tracker: ${type.name}"
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
            Sensor.TYPE_HEART_RATE -> "Heart Rate (Hardware)"
            Sensor.TYPE_HEART_BEAT -> "Heart Beat"
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Off-Body Detect"
            else -> "Sensor Type $type"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        healthTrackingService?.disconnectService()
    }
}