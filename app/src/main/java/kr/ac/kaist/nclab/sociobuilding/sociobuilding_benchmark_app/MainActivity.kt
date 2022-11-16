package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.instacart.library.truetime.TrueTime
import kotlinx.android.synthetic.main.activity_main.*
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.beacon.BeaconListener
import timber.log.Timber
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }
    private lateinit var sharedPreference: SharedPreferences

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /* Keep Screen On */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        App.beaconListener = BeaconListener(application)

        sharedPreference = getSharedPreferences("preference", MODE_PRIVATE)

        App.deviceName = sharedPreference.getString(KEY_DEVICE_NAME, "device").toString()

        text_device_name.text = App.deviceName

        /* Verify Location permissions for Wifi Scanning */
        Timber.d("Verifying location permissions ...")
        verifyLocationPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            verifyBackgroundLocationPermission()
        Timber.d("Verifying location permissions ... Done.")

        /* Outdoor Scanning Button. */
        btn_outdoor_start.setOnClickListener {
            Timber.d("btn_outdoor_start clicked!")
            viewModel.onStartOutdoorScanWork()
        }

        btn_outdoor_stop.setOnClickListener {
            Timber.d("btn_outdoor_stop clicked!")
            viewModel.onStopOutdoorScanWork()
        }

        btn_indoor_start.setOnClickListener {
            Timber.d("btn_indoor_start clicked!")
            viewModel.onStartIndoorMode()
        }

        btn_indoor_stop.setOnClickListener {
            Timber.d("btn_indoor_stop clicked!")
            viewModel.onStopIndoorMode()
        }

        btn_token.setOnClickListener {
            viewModel.onRetrieveToken()
        }

        btn_forecasting_start.setOnClickListener {
            viewModel.onStartForecastingMode()
        }

        btn_stop.setOnClickListener {
            viewModel.onStopButton()
        }

        btn_cache_mode.text = "CACHED = ${AppConfig.CACHED}"
        btn_cache_mode.setOnClickListener {
            AppConfig.CACHED = AppConfig.CACHED.not()
            btn_cache_mode.text = "CACHED = ${AppConfig.CACHED}"
            Timber.d("cache mode is now ${AppConfig.CACHED}")
        }

        /* for experiment: set device name (= uploading directory name) */
        btn_ok.setOnClickListener {
            val preferenceEditor: SharedPreferences.Editor = sharedPreference.edit()
            preferenceEditor.putString(KEY_DEVICE_NAME, editText.text.toString())
            preferenceEditor.commit()

            Toast.makeText(this, "device name: ${editText.text}", Toast.LENGTH_SHORT).show()
            Timber.d("device name: ${editText.text}")
            App.deviceName = editText.text.toString()
            text_device_name.text = App.deviceName
        }

        initTrueTime()
    }
    private fun initTrueTime() {
        InitTrueTimeAsyncTask().execute()
    }

    override fun onResume() {
        super.onResume()
        Timber.d("Check again the permissions...")
        checkPermission()
        Timber.d("Check again the permissions... Done.")
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val permissionGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (permissionGranted)
            Timber.d("[Permission] User granted {${permissions.joinToString()}}.")
        else {
            val deniedPermissions = permissions.zip(grantResults.asIterable())
                    .filter { (_, res) -> res == PackageManager.PERMISSION_DENIED }
                    .map { (permission, _) -> permission }
            Timber.d("[Permission] User denied {${deniedPermissions.joinToString()}}.")
        }

        when (requestCode) {
            PERMISSION_REQUEST_FINE_LOCATION ->
                if (permissionGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        verifyBackgroundLocationPermission()
                } else
                    showLocationPermissionAlert()

            PERMISSION_REQUEST_BACKGROUND_LOCATION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    verifyBackgroundLocationPermission()

            PERMISSION_REQUEST_REST ->
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "애플리케이션에 권한을 부여해주세요.", Toast.LENGTH_LONG).show()
                        Timber.d("Application do not have permission")
                    }
                }
        }
    }

    private fun isPermissionGranted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(
                    applicationContext, permission
            ) == PackageManager.PERMISSION_GRANTED

    private fun showLocationPermissionAlert() {
        Timber.d("[Permission] showLocationPermissionAlert")
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_permission_location_title))
                .setMessage(getString(R.string.dialog_permission_location_message))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .setData(Uri.fromParts("package", packageName, null))
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun verifyLocationPermissions() {
        when {
            /* Case 01: Permission Granted */
            isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ->
                Timber.d("[Permission] ACCESS_FINE_LOCATION is already granted.")

            /* Case 02: Should show request permission rationale */
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                showLocationPermissionAlert()

            /* Case 03: Ask for permission directly */
            else ->
                requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSION_REQUEST_FINE_LOCATION
                )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun verifyBackgroundLocationPermission() {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        when {
            /* Case 01: Permission Granted */
            isPermissionGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
                Timber.d("[Permission] ACCESS_BACKGROUND_LOCATION is already granted.")

            /* Case 02: Ask for permission directly */
            else ->
                AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_permission_background_location_title))
                        .setMessage(getString(R.string.dialog_permission_background_location_message))
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            requestPermissions(
                                    arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    ),
                                    PERMISSION_REQUEST_BACKGROUND_LOCATION
                            )
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
        }
    }

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
            permissions.all {
                ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

    private fun checkPermission() {
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_REST)
        }
    }

    companion object {

        const val KEY_DEVICE_NAME = "device_name"

        private val REQUIRED_PERMISSIONS:Array<String> = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.INTERNET,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
//            Manifest.permission.FOREGROUND_SERVICE
        )
        private const val PERMISSION_REQUEST_FINE_LOCATION = 1
        private const val PERMISSION_REQUEST_BACKGROUND_LOCATION = 2
        private const val PERMISSION_REQUEST_REST = 3
        var timediff:Long = 0 // Time sync를 위해서 Truetime 과 system time의 diff를 저장
    }
}

// a little part of me died, having to use this
private class InitTrueTimeAsyncTask :
    AsyncTask<Void?, Void?, Void?>() {
    override fun doInBackground(vararg params: Void?): Void? {
        try {
            TrueTime.build() //.withSharedPreferences(SampleActivity.this)
                .withNtpHost("time.google.com")
                .withLoggingEnabled(false)
//                .withSharedPreferencesCache(this)
                .withConnectionTimeout(31428)
                .initialize()
            MainActivity.timediff = TrueTime.now().time - Date().time // Time sync를 위해서 Truetime 과 system time의 diff를 저장
        } catch (e: IOException) {
            e.printStackTrace()
            Timber.e("something went wrong when trying to initialize TrueTime")
        }
        return null
    }
}
