package com.spn.secquraiseassignment

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AlignmentSpan
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.spn.secquraiseassignment.Room.Database
import com.spn.secquraiseassignment.Room.UploadData
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    lateinit var refresh: Button
    lateinit var numberPicker: NumberPicker
    lateinit var date: TextView
    lateinit var image: ImageView
    lateinit var captureCount: TextView
    lateinit var frequency: TextView
    lateinit var connectivity: TextView
    lateinit var batteryCharging: TextView
    lateinit var batteryCharge: TextView
    lateinit var location: TextView

    lateinit var imageUri: Uri

    var frequencyValue = 15

    private lateinit var cameraExecutor: ExecutorService

    val db = Firebase.firestore

    var data = mutableMapOf<String, String>()

    lateinit var database: Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        initialize()

        requestPermissions()

        startPeriodicDataFetching()

        frequency.setOnClickListener {
            showNumberPickerDialog()
        }

        refresh.setOnClickListener {
            getData()

            uploadData()
        }

        CoroutineScope(Dispatchers.Main).launch {
            val dataDao = database.dataDao()

            val _data = dataDao.getAllData()

            Log.d("TAG334", _data.size.toString())

            if (connectivity.text == "ON") {
                _data.forEach {
                    retryUploadData(it)
                }
            }
        }
    }

    private fun initialize() {
        refresh = findViewById(R.id.getData)
        date = findViewById(R.id.date)
        captureCount = findViewById(R.id.capture_count)
        frequency = findViewById(R.id.frequency)
        connectivity = findViewById(R.id.connectivity)
        batteryCharging = findViewById(R.id.battery_charging)
        batteryCharge = findViewById(R.id.battery_charge)
        location = findViewById(R.id.location)

        image = findViewById(R.id.image)
        cameraExecutor = Executors.newSingleThreadExecutor()

        database = Room.databaseBuilder(this@MainActivity, Database::class.java, "data").build()
    }

    private fun startPeriodicDataFetching() {
        val handler = Handler()
        val runnable = object : Runnable {
            override fun run() {
                getData()

                uploadData()
//                handler.postDelayed(this, 1000 * frequencyValue.toLong())
                handler.postDelayed(this, 60 * 1000 * frequencyValue.toLong())
            }
        }

        //Start the execution after delayMillis passed after opening the app
        handler.postDelayed(runnable, 1000)
    }

    private fun getData() {
        getDate()

        getCaptureCount()

        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            takePhoto()
        } else {
            requestPermissions()
        }

        getConnectivity()
        getBatteryChargingStatus()
        getBatteryPercentage()

        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        } else {
            requestPermissions()
        }
    }

    private fun getDate() {
        val currentDate = Date()

        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        val formattedDate = dateFormat.format(currentDate)

        date.text = formattedDate.toString()

        data["Date"] = formattedDate.toString()
    }

    private fun takePhoto() {
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageCapture
                )

                val outputFile =
                    File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")

                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            imageUri = outputFileResults.savedUri!!

                            Picasso.get().load(imageUri).into(image)

                            uploadImage(imageUri)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getConnectivity() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkInfo = connectivityManager.activeNetworkInfo
        val isConnected = networkInfo != null && networkInfo.isConnected

        if (isConnected) {
            connectivity.text = "ON"
        } else {
            connectivity.text = "OFF"
        }

        data["Connectivity"] = connectivity.text.toString()
    }

    private fun getCaptureCount() {
        var sharedPreferences =
            getSharedPreferences(java.lang.String.valueOf(R.string.app_name), MODE_PRIVATE)
        var captureCountValue = sharedPreferences.getInt("CaptureCount", 0)

        captureCount.text = captureCountValue.toString()
    }

    private fun getBatteryChargingStatus() {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                if (isCharging) {
                    batteryCharging.text = "ON"
                } else {
                    batteryCharging.text = "OFF"
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)


        data["BatteryCharging"] = batteryCharging.text.toString()
    }

    private fun getBatteryPercentage() {
        val batteryManager =
            this@MainActivity.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        batteryCharge.text =
            "${batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"

        data["BatteryCharge"] = batteryCharge.text.toString()
    }

    private fun getLocation() {
        val locationListener: LocationListener = object : LocationListener {
            @SuppressLint("SetTextI18n")
            override fun onLocationChanged(_location: Location) {
                // Handle the new location here
                var latitude = _location.latitude.toString()
                var longitude = _location.longitude.toString()

                if (latitude.length >= 9) latitude = _location.latitude.toString().substring(0, 9)
                if (longitude.length >= 9) longitude =
                    _location.longitude.toString().substring(0, 9)

                location.text = "$latitude, $longitude"
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val minTime: Long = 1000 // Minimum time interval between location updates (in milliseconds)
        val minDistance: Float = 10f // Minimum distance between location updates (in meters)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            minTime,
            minDistance,
            locationListener
        )

        data["Location"] = location.text.toString()
    }

    private fun uploadData() {
        Log.d("TAG334", "Called")

        var sharedPreferences =
            getSharedPreferences(java.lang.String.valueOf(R.string.app_name), MODE_PRIVATE)
        var editor = sharedPreferences.edit()

        var captureCountValue = sharedPreferences.getInt("CaptureCount", 0) + 1
        editor.putInt("CaptureCount", captureCountValue)
        editor.apply()

        db.collection("Data")
            .add(data)
            .addOnSuccessListener { _ ->
                Toast.makeText(applicationContext, "Data Uploaded Successfully", Toast.LENGTH_SHORT)
                    .show()
            }
            .addOnFailureListener {
                CoroutineScope(Dispatchers.Main).launch {
                    if (connectivity.text == "OFF") {
                        val dataDao = database.dataDao()

                        dataDao.insertData(UploadData(data = data, image = imageUri))
                    } else {
                        delay(5000)

                        uploadData()
                    }
                }
            }
    }

    private fun uploadImage(imageUri: Uri) {
        val storage = FirebaseStorage.getInstance()

        val fileName = UUID.randomUUID().toString()
        val storageRef = storage.reference.child("Images/$fileName.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                val downloadUrl = taskSnapshot.storage.downloadUrl

                Toast.makeText(
                    applicationContext,
                    "Image Uploaded Successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val toast = "There was an error adding the information. Trying Again"
                    val centeredText = SpannableString(toast)
                    centeredText.setSpan(
                        AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0,
                        toast.length - 1,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    Toast.makeText(applicationContext, centeredText, Toast.LENGTH_SHORT).show()

                    delay(5000)

                    uploadImage(imageUri)
                }
            }
    }

    private fun retryUploadData(uploadData: UploadData) {
        data = uploadData.data as MutableMap<String, String>

        uploadData()
        uploadImage(uploadData.image)

        CoroutineScope(Dispatchers.Main).launch {
            val dataDao = database.dataDao()

            dataDao.deleteData(uploadData)
        }
    }

    private fun requestPermissions() {
        val permissionArray = ArrayList<String>()

        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionArray.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionArray.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionArray.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionArray.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissionArray.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionArray.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPeriodicDataFetching()
            } else if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {

            }
        }
    }

    private fun showNumberPickerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_number_picker, null)
        numberPicker = dialogView.findViewById(R.id.numberPicker)

        // Set NumberPicker properties
        numberPicker.minValue = 1
        numberPicker.maxValue = 100

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Select the Frequency (min)")
            .setPositiveButton("Select") { _, _ ->
                // Handle number selection
                val selectedNumber = numberPicker.value

                frequency.text = selectedNumber.toString()
                frequencyValue = selectedNumber
            }
            .setNegativeButton("Cancel", null)
            .create()

        alertDialog.show()
    }
}