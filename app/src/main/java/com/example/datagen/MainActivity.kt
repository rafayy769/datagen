package com.example.datagen

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.*
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.media.MediaPlayer
import android.os.*
import android.os.PowerManager.WakeLock
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlin.system.exitProcess

interface CommService {
    @GET
    fun downloadFile(@Url fileUrl: String): retrofit2.Call<ResponseBody>
    @POST("uploadSensorData")
    fun uploadSensorData(@Body data: SensorDataUpload): retrofit2.Call<Void>
}

data class SensorDataUpload(
    val location: String,
    val accelerometerData: List<String>,
    val gyroscopeData: List<String>
)

class MainActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager

    private lateinit var progressBar: ProgressBar

    //    listeners
    private lateinit var accelerometer: Sensor
    private lateinit var gyroscope: Sensor

    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: WakeLock

    private var audioQueue: MutableList<String> = mutableListOf()
    private var currentlyPlaying = false

    //    arrays that will store the data in memory, received from sensors
    private val accelerometerData = mutableListOf<String>()
    private val gyroscopeData = mutableListOf<String>()

    private var mediaPlayer: MediaPlayer? = null
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.0.101:8000/") // Change this to your laptop's IP and port
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val retrofit_service = retrofit.create(CommService::class.java)

    // separate listeners for the three sensors
    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensorType = event.sensor.type
            val values = event.values
            when (sensorType) {
                Sensor.TYPE_ACCELEROMETER -> accelerometerData.add("${event.timestamp},${values[0]},${values[1]},${values[2]}")
                else -> null
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    private val gyroscopeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensorType = event.sensor.type
            val values = event.values
            when (sensorType) {
                Sensor.TYPE_GYROSCOPE -> gyroscopeData.add("${event.timestamp},${values[0]},${values[1]},${values[2]}")
                else -> null
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE
    )

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerSensors(delay: Int) {
        sensorManager.registerListener(accelerometerListener, accelerometer, delay)
        sensorManager.registerListener(gyroscopeListener, gyroscope, delay)
    }
    private fun unregisterSensors() {
        sensorManager.unregisterListener(accelerometerListener)
        sensorManager.unregisterListener(gyroscopeListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        progressBar = findViewById(R.id.progressBar)

        if (allPermissionsGranted()) {
            processCsvFile()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun processCsvFile() {
        val csvFile = File(Environment.getExternalStorageDirectory(), "metadata.csv")
        val lines = csvFile.readLines()
        lines.forEach { line ->
            val (_, _, _, _, audioLocation) = line.split(",")
            audioQueue.add(audioLocation)
        }
        progressBar.max = lines.size
        progressBar.progress = 0
        playNextAudioFile()
    }

    private fun playNextAudioFile() {
        if (audioQueue.isNotEmpty() && !currentlyPlaying) {
            progressBar.progress += 1
            Log.d("sync", "clearing out acc and gyro before receiving the next one")
            accelerometerData.clear()
            gyroscopeData.clear()
            val location = audioQueue.removeAt(0)
            currentlyPlaying = true
            fetchAndPlayAudio(location)
        }
    }

    private fun fetchAndPlayAudio(location: String) {
        val call = retrofit_service.downloadFile(location)
        call.enqueue(object : retrofit2.Callback<ResponseBody> {
            override fun onResponse(call: retrofit2.Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {
                response.body()?.let { responseBody ->
                    val tempFile = createTempAudioFile(responseBody)
                    playAudio(tempFile, location)
                }
            }

            override fun onFailure(call: retrofit2.Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
                currentlyPlaying = false
                playNextAudioFile()
            }
        })
    }
    private fun createTempAudioFile(responseBody: ResponseBody): File {
        val tempFile = File.createTempFile("audio", null, cacheDir)
        tempFile.outputStream().use { fileOutputStream ->
            responseBody.byteStream().use { inputStream ->
                inputStream.copyTo(fileOutputStream)
            }
        }
        return tempFile
    }
    private fun playAudio(file: File, location: String) {
        registerSensors(2500)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.path)
            prepare()
            start()
        }
        mediaPlayer?.setOnCompletionListener {
            unregisterSensors()
            postSensorData(location)
            file.delete()
        }
    }
    private fun postSensorData(location: String) {
        Log.d("sync", "Creating the data object from current acc and gyro states")
        val dataUpload = SensorDataUpload(location, accelerometerData, gyroscopeData)
        val call = retrofit_service.uploadSensorData(dataUpload)
        call.enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("POST", "Sensor traces posted succesfully")
                }
                currentlyPlaying = false
                if (audioQueue.isEmpty()) {
                    finishAffinity()
                    exitProcess(0)
                }
                playNextAudioFile()
            }

            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                t.printStackTrace()
                currentlyPlaying = false
                if (audioQueue.isEmpty()) {
                    finishAffinity()
                    exitProcess(0)
                }
                playNextAudioFile()
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == this.REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                processCsvFile()
            } else {
                Toast.makeText(
                    this,
                    "Permissions are required for the app to function properly.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

}