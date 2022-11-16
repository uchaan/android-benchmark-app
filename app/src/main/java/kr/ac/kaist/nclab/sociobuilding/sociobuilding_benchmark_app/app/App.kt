package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.AppConfig
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.beacon.BeaconListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@HiltAndroidApp
class App:Application() {

    override fun onCreate() {
        Timber.plant(Timber.DebugTree())
        super.onCreate()
    }

    companion object {

        lateinit var beaconListener: BeaconListener

        var onRecord: AtomicBoolean = AtomicBoolean(false)

        /* device name for experiment */
        var deviceName = ""

        /* for debugging */
        var wifiScanCount = 0
        var indoorWorkCount = 0
        var forecastingWorkCount = 0
        fun addWifiScanCount() { wifiScanCount ++ }
        fun addIndoorWorkCount() { indoorWorkCount ++ }
        fun addForecastingWorkCount() { forecastingWorkCount ++ }

        /* global buffer that holds a recent background noise record*/
        var prevRecordBuffer: ByteArray? = null

        /* firebase messaging token */
        var token = "unknown"

        /* FCM default recording duration */
        var duration_ms: Int? = AppConfig.EVIDENCE_COLLECT_DEFAULT_RECORDING_PERIOD_MS
    }
}