package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema.ScanEntry
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema.ScanReport
import timber.log.Timber

class WifiScanner (private val context: Context) {
    companion object {

    }

    private val wifiManager:WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager


    fun startScan() {
        Timber.d("start scanning....")
        wifiManager.startScan()
        Timber.d("start scanning.... Done.")
    }

    @ExperimentalCoroutinesApi
    fun subscribeScanReport(): Flow<ScanReport?> = callbackFlow {
        val handler =  object: BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    val report = retrieveScanReport()
                    offer(report)
                } else {
                    //onScanFailure()
                    offer(null)
                    //throw IllegalStateException("WiFi scan not updated")
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(handler, intentFilter)

        awaitClose {
            Timber.d("awaitClose(): context.unregisterReceiver(handler)")
            context.unregisterReceiver(handler)
        }
    }

    private fun retrieveScanReport(): ScanReport {
        Timber.d("onScanSuccess()")
        val results = wifiManager.scanResults

        val entries = results.map { result ->
            ScanEntry(
                rssi = result.level,
                bssid = result.BSSID,
                ssid = result.SSID,
                frequency = result.frequency,
                timestamp = result.timestamp
            )
        }

        return ScanReport(timestamp = System.currentTimeMillis(), entries = entries)
    }

    private fun onScanFailure() {
        Timber.d("Scan Failed!!!")
    }


}