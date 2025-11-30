package com.example.watchsensors

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

class MainActivity : Activity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var statusText: TextView
    private lateinit var buttonContainer: LinearLayout
    private var healthTrackingService: HealthTrackingService? = null
    private var activeTracker: HealthTracker? = null
    private var activeTrackerType: HealthTrackerType? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TAG = "WatchSensors"
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.d(TAG, "Samsung Health Service connected")
            runOnUiThread {
                statusText.text = "Connected!\nSelect a sensor below:"
                createSensorButtons()
            }
        }

        override fun onConnectionEnded() {
            Log.d(TAG, "Samsung Health Service connection ended")
        }

        override fun onConnectionFailed(error: HealthTrackerException) {
            Log.e(TAG, "Connection failed: ${error.errorCode}")
            runOnUiThread {
                statusText.text = "Connection failed\nError: ${error.errorCode}"
                if (error.hasResolution()) {
                    error.resolve(this@MainActivity)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusText = TextView(this).apply {
            setPadding(16, 16, 16, 16)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            text = "Starting..."
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        scrollView.addView(buttonContainer)
        mainContainer.addView(statusText)
        mainContainer.addView(scrollView)
        setContentView(mainContainer)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        if (!hasRequiredPermissions()) {
            requestPermissions()
        } else {
            initializeSensors()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        val activityRecognition = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        return bodySensors && activityRecognition
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeSensors()
            } else {
                statusText.text = "Permissions denied.\nGo to Settings to enable."
                addSettingsButton()
            }
        }
    }

    private fun addSettingsButton() {
        val settingsButton = Button(this).apply {
            text = "Open Settings"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
        }
        buttonContainer.addView(settingsButton)
    }

    private fun initializeSensors() {
        try {
            healthTrackingService = HealthTrackingService(connectionListener, this)
            healthTrackingService?.connectService()
            statusText.text = "Connecting..."
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun createSensorButtons() {
        buttonContainer.removeAllViews()

        val trackingCapability = healthTrackingService?.trackingCapability
        val availableTrackers = trackingCapability?.supportHealthTrackerTypes ?: emptyList()

        // Add Stop button at the top
        val stopButton = Button(this).apply {
            text = "⏹ STOP ALL"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopCurrentTracker()
            }
        }
        buttonContainer.addView(stopButton)

        // Add divider
        addDivider()

        // Create buttons for each available sensor
        val sensorButtons = mapOf(
            HealthTrackerType.HEART_RATE_CONTINUOUS to "❤️ Heart Rate",
            HealthTrackerType.SPO2_ON_DEMAND to "🫁 Blood Oxygen (SpO2)",
            HealthTrackerType.ECG_ON_DEMAND to "📈 ECG",
            HealthTrackerType.BIA_ON_DEMAND to "⚖️ Body Composition (BIA)",
            HealthTrackerType.PPG_CONTINUOUS to "🔴 PPG (Raw)",
            HealthTrackerType.ACCELEROMETER_CONTINUOUS to "📊 Accelerometer",
            HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND to "🌡️ Skin Temperature"
        )

        sensorButtons.forEach { (trackerType, label) ->
            if (availableTrackers.contains(trackerType)) {
                val button = Button(this).apply {
                    text = label
                    setOnClickListener {
                        startTracker(trackerType, label)
                    }
                }
                buttonContainer.addView(button)
            }
        }

        // Show unavailable sensors
        addDivider()
        val unavailableText = TextView(this).apply {
            text = "Unavailable sensors:"
            textSize = 10f
            setTextColor(Color.GRAY)
            setPadding(8, 8, 8, 4)
        }
        buttonContainer.addView(unavailableText)

        sensorButtons.forEach { (trackerType, label) ->
            if (!availableTrackers.contains(trackerType)) {
                val unavailableButton = Button(this).apply {
                    text = "$label (Not Available)"
                    isEnabled = false
                    setTextColor(Color.GRAY)
                }
                buttonContainer.addView(unavailableButton)
            }
        }
    }

    private fun addDivider() {
        val divider = TextView(this).apply {
            text = "─────────────────"
            textSize = 10f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        buttonContainer.addView(divider)
    }

    private fun startTracker(trackerType: HealthTrackerType, label: String) {
        // Stop any currently running tracker
        stopCurrentTracker()

        try {
            val tracker = healthTrackingService?.getHealthTracker(trackerType)

            if (tracker == null) {
                statusText.text = "Failed to get $label tracker"
                return
            }

            val trackerEventListener = object : HealthTracker.TrackerEventListener {
                override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                    Log.d(TAG, "$label: Received ${dataPoints.size} data points")

                    dataPoints.forEach { dataPoint ->
                        val data = extractDataFromPoint(dataPoint, trackerType)
                        sendDataToPhone(label, data)

                        runOnUiThread {
                            statusText.text = "📡 $label\n\n$data"
                        }
                    }
                }

                override fun onFlushCompleted() {
                    Log.d(TAG, "$label: Flush completed")
                }

                override fun onError(error: HealthTracker.TrackerError) {
                    Log.e(TAG, "$label error: ${error.name}")
                    runOnUiThread {
                        statusText.text = "Error: ${error.name}"
                    }
                }
            }

            tracker.setEventListener(trackerEventListener)
            activeTracker = tracker
            activeTrackerType = trackerType

            statusText.text = "Starting $label..."
            Log.d(TAG, "Starting tracker: $label")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracker: ${e.message}")
            statusText.text = "Error: ${e.message}"
        }
    }

    private fun stopCurrentTracker() {
        activeTracker?.unsetEventListener()
        activeTracker = null
        activeTrackerType = null
        statusText.text = "Stopped.\nSelect a sensor below:"
        Log.d(TAG, "Tracker stopped")
    }

    private fun extractDataFromPoint(dataPoint: DataPoint, trackerType: HealthTrackerType): String {
        val sb = StringBuilder()

        when (trackerType) {
            HealthTrackerType.HEART_RATE_CONTINUOUS -> {
                val hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                val status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                sb.append("Heart Rate: $hr bpm\n")
                sb.append("Status: $status")
            }

            HealthTrackerType.SPO2_ON_DEMAND -> {
                val spo2 = dataPoint.getValue(ValueKey.SpO2Set.SPO2)
                val status = dataPoint.getValue(ValueKey.SpO2Set.STATUS)
                sb.append("SpO2: $spo2%\n")
                sb.append("Status: $status")
            }

            HealthTrackerType.ECG_ON_DEMAND -> {
                val ppg = dataPoint.getValue(ValueKey.EcgSet.PPG_GREEN)
                val leadOff = dataPoint.getValue(ValueKey.EcgSet.LEAD_OFF)
                sb.append("PPG Green: $ppg\n")
                sb.append("Lead Off: $leadOff")
            }

            HealthTrackerType.BIA_ON_DEMAND -> {
                val impedance = dataPoint.getValue(ValueKey.BiaSet.STATUS)
                sb.append("Impedance: $impedance Ω")
            }

            HealthTrackerType.PPG_CONTINUOUS -> {
                val green = dataPoint.getValue(ValueKey.PpgGreenSet.PPG_GREEN)
                sb.append("PPG Green: $green")
            }

            HealthTrackerType.ACCELEROMETER_CONTINUOUS -> {
                val x = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X)
                val y = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y)
                val z = dataPoint.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z)
                sb.append("X: $x\nY: $y\nZ: $z")
            }

            HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND -> {
                val temp = dataPoint.getValue(ValueKey.SkinTemperatureSet.STATUS)
                val status = dataPoint.getValue(ValueKey.SkinTemperatureSet.STATUS)
                sb.append("Temp: $temp°C\n")
                sb.append("Status: $status")
            }

            else -> {
                sb.append("Data received")
            }
        }

        return sb.toString()
    }

    private fun sendDataToPhone(sensorName: String, data: String) {
        val dataClient = Wearable.getDataClient(this)

        val request = PutDataMapRequest.create("/sensor_data").apply {
            dataMap.putString("sensor_name", sensorName)
            dataMap.putString("sensor_data", data)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnSuccessListener {
                Log.d(TAG, "Data sent to phone: $sensorName")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send data: ${e.message}")
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentTracker()
        healthTrackingService?.disconnectService()
    }
}