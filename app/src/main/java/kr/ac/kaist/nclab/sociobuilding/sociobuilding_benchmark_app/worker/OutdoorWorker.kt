package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.WifiScanner
import timber.log.Timber

class OutdoorWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        /* Beacon scanning */
        App.beaconListener.startScan()

        val scanner = WifiScanner(applicationContext)
        scanner.startScan()

        /* Wifi scanning */
        var subscriptionJob:Job? = null
        subscriptionJob = CoroutineScope(Dispatchers.Main).launch {
            val reportFlow = scanner.subscribeScanReport()
            reportFlow.collect { report->
                if (report != null) {
                    Timber.d("Scan Success")
                    App.addWifiScanCount()
                    Timber.d ("Wifi Scanning count: ${App.wifiScanCount}")
                    Timber.d("report: $report")
                } else {
                    Timber.d("스캔 실패. (예상 사유: 속도 한도초과)")
//                    Toast.makeText(applicationContext, "스캔 실패. (예상 사유: 속도 한도초과)", Toast.LENGTH_SHORT).show()
                }
                subscriptionJob?.cancel()
            }
        }

        App.beaconListener.getScanResult()

        return Result.success()
    }

    companion object {
    }
}
