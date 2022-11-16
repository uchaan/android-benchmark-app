package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.alarm.AlarmReceiver
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App.Companion.beaconListener
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.IndoorWorker
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.OutdoorWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : ViewModel() {

    private var workManager = WorkManager.getInstance(application)
    private val app = application

    companion object {
        const val OUTDOOR_WORK_NAME = "ScanWork - 15min"
        const val INDOOR_WORK_NAME = "IndoorWork - 1min"
    }

    fun onStartOutdoorScanWork() {

        /* create a Periodic WorkRequest */
        val wifiScanWorkRequest =
            PeriodicWorkRequestBuilder<OutdoorWorker>(
                AppConfig.OUTDOOR_WORK_PERIOD.toLong(),
                TimeUnit.MINUTES
            ) // minimum: 15minutes
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        Timber.d("start periodic work...")
        workManager
            .enqueueUniquePeriodicWork( // guarantees one instance of work.
                OUTDOOR_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                wifiScanWorkRequest
            )
    }

    fun onStopOutdoorScanWork() {
        Timber.d("cancel all periodic work")
        workManager.cancelAllWork()
//        workManager.cancelUniqueWork(OUTDOOR_WORK_NAME)
    }

    fun onStartOutdoorBleScanWork() {
        beaconListener.startScan()
    }

    fun onStopBleScanWork() {
        beaconListener.stopScan()
    }

    fun onStartIndoorMode() {

        /* create a OneTime WorkRequest */
        val indoorWorkRequest =
            OneTimeWorkRequestBuilder<IndoorWorker>()
            .setInitialDelay(
                AppConfig.INDOOR_WORK_INITIAL_DELAY.toLong(),
                TimeUnit.SECONDS
            )
            .build()

        /* before the end of doWork(), it will register itself again with Initial Delay*/
        Timber.d("!!!!!!!! start initial indoor work... !!!!!!!!")
        workManager
            .enqueueUniqueWork(
                INDOOR_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                indoorWorkRequest
            )
    }

    fun onStopIndoorMode() {
//        workManager.cancelUniqueWork(INDOOR_WORK_NAME)
        beaconListener.stopScan()
        workManager.cancelAllWork()
    }

    fun onStartForecastingMode() {
        AlarmReceiver.scheduleNext(app, AppConfig.INDOOR_WORK_INITIAL_DELAY_MS)
    }

    /*
    Get current registration token
    The registration token may change when:

    - The app is restored on a new device
    - The user uninstalls/reinstall the app
    - The user clears app data.

     */

    fun onRetrieveToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.w("Fetching FCM registration token failed")
                return@OnCompleteListener
            }
            // Get new FCM registration token
            App.token = task.result.toString()
            Timber.d("current token: ${App.token}")
        })
    }

    fun onStopButton() {
        Timber.d("cancel all existing works ...")
        try {
            beaconListener.stopScan()
            AlarmReceiver.cancelAlarm(app)
            workManager.cancelAllWork()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            Timber.d("cancel all existing works .... Done")
        }
    }
}


class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            MainViewModel(application) as T
        } else {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
