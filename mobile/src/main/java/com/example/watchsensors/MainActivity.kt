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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {
    private lateinit var dataClient: DataClient
    private lateinit var sensorDataText: TextView
    private val batchHistory = mutableListOf<String>()
    private val maxHistorySize = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        sensorDataText = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "Waiting for PPG batch data from watch..."
        }
        scrollView.addView(sensorDataText)
        setContentView(scrollView)

        dataClient = Wearable.getDataClient(this)
        Log.d("PhonePPG", "Phone app started, waiting for PPG batch data...")
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
        Log.d("PhonePPG", "Data received from watch!")

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: continue
                Log.d("PhonePPG", "Path: $path")

                when (path) {
                    "/ppg_batch_data" -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                        val wavelengthName = dataMap.getString("wavelength_name")
                        val wavelengthNm = dataMap.getInt("wavelength_nm")
                        val valuesList = dataMap.getIntegerArrayList("values")
                        val values = valuesList?.toIntArray()
                        val timestamps = dataMap.getLongArray("timestamps")
                        val startTime = dataMap.getLong("start_time")
                        val endTime = dataMap.getLong("end_time")
                        val count = dataMap.getInt("count")
                        val mean = dataMap.getDouble("mean")
                        val stdDev = dataMap.getDouble("std_dev")
                        val min = dataMap.getInt("min")
                        val max = dataMap.getInt("max")
                        val batchTimestamp = dataMap.getLong("batch_timestamp")

                        Log.d("PhonePPG", "Batch received: $wavelengthName - $count samples")

                        runOnUiThread {
                            displayPPGBatchData(
                                wavelengthName, wavelengthNm, values, timestamps,
                                startTime, endTime, count, mean, stdDev, min, max, batchTimestamp
                            )
                        }
                    }
                }
            }
        }
    }

    private fun displayPPGBatchData(
        wavelengthName: String?,
        wavelengthNm: Int,
        values: IntArray?,
        timestamps: LongArray?,
        startTime: Long,
        endTime: Long,
        count: Int,
        mean: Double,
        stdDev: Double,
        min: Int,
        max: Int,
        batchTimestamp: Long
    ) {
        if (wavelengthName == null || values == null || timestamps == null) return

        val displayText = StringBuilder()

        // Header
        displayText.append("╔════════════════════════════════════════════╗\n")
        displayText.append("║       PPG BATCH DATA RECEIVED             ║\n")
        displayText.append("╚════════════════════════════════════════════╝\n\n")

        // Wavelength info
        displayText.append("🔴 Wavelength: $wavelengthName ($wavelengthNm nm)\n")
        displayText.append("─────────────────────────────────────\n\n")

        // Batch summary
        displayText.append("📊 BATCH SUMMARY\n")
        displayText.append("─────────────────────────────────────\n")
        displayText.append("Sample Count:    $count\n")
        displayText.append("Duration:        ${(endTime - startTime) / 1000.0} sec\n")
        displayText.append("Sampling Rate:   ${count / ((endTime - startTime) / 1000.0)} Hz\n")
        displayText.append("\n")

        // Statistics
        displayText.append("📈 STATISTICS\n")
        displayText.append("─────────────────────────────────────\n")
        displayText.append("Mean:            ${String.format("%.2f", mean)}\n")
        displayText.append("Std Dev:         ${String.format("%.2f", stdDev)}\n")
        displayText.append("Min:             $min\n")
        displayText.append("Max:             $max\n")
        displayText.append("Range:           ${max - min}\n")
        displayText.append("\n")

        // Time info
        displayText.append("⏰ TIMING\n")
        displayText.append("─────────────────────────────────────\n")
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        displayText.append("Start:           ${dateFormat.format(Date(startTime))}\n")
        displayText.append("End:             ${dateFormat.format(Date(endTime))}\n")
        displayText.append("Received:        ${dateFormat.format(Date(batchTimestamp))}\n")
        displayText.append("\n")

        // Sample preview (first 10 and last 10 values)
        displayText.append("📋 SAMPLE DATA PREVIEW\n")
        displayText.append("─────────────────────────────────────\n")
        displayText.append("First 10 samples:\n")
        values.take(10).forEachIndexed { index, value ->
            displayText.append("  [$index] $value\n")
        }

        if (values.size > 20) {
            displayText.append("  ...\n")
            displayText.append("Last 10 samples:\n")
            values.takeLast(10).forEachIndexed { index, value ->
                val actualIndex = values.size - 10 + index
                displayText.append("  [$actualIndex] $value\n")
            }
        } else if (values.size > 10) {
            values.drop(10).forEachIndexed { index, value ->
                val actualIndex = 10 + index
                displayText.append("  [$actualIndex] $value\n")
            }
        }

        displayText.append("\n")

        // Derived metrics
        displayText.append("🧮 DERIVED METRICS\n")
        displayText.append("─────────────────────────────────────\n")

        // Calculate AC and DC components
        val acComponent = stdDev
        val dcComponent = mean
        val acDcRatio = if (dcComponent != 0.0) acComponent / dcComponent else 0.0

        displayText.append("AC Component:    ${String.format("%.2f", acComponent)}\n")
        displayText.append("DC Component:    ${String.format("%.2f", dcComponent)}\n")
        displayText.append("AC/DC Ratio:     ${String.format("%.4f", acDcRatio)}\n")

        // Perfusion Index estimation (simplified)
        val perfusionIndex = (acDcRatio * 100)
        displayText.append("Perfusion Index: ${String.format("%.2f", perfusionIndex)}%\n")

        displayText.append("\n")

        // Add to history
        val historyEntry = "[$wavelengthName] ${dateFormat.format(Date(batchTimestamp))} - $count samples"
        batchHistory.add(0, historyEntry)
        if (batchHistory.size > maxHistorySize) {
            batchHistory.removeAt(batchHistory.lastIndex)
        }

        // Display history
        displayText.append("📜 RECENT BATCHES\n")
        displayText.append("─────────────────────────────────────\n")
        batchHistory.forEach { entry ->
            displayText.append("  $entry\n")
        }

        displayText.append("\n")
        displayText.append("═══════════════════════════════════════\n")
        displayText.append("Total batches received: ${batchHistory.size}\n")

        sensorDataText.text = displayText.toString()

        // Log for debugging
        Log.d("PhonePPG", "Displayed batch: $wavelengthName with $count samples")
    }
}