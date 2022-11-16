package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.beacon

import android.app.Application
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.R
import org.altbeacon.beacon.*
import org.altbeacon.bluetooth.BluetoothMedic
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeaconListener @Inject constructor(@ApplicationContext private val context: Application): RangeNotifier {

    /* SocioBuilding Beacon */
    data class BeaconObservation(val id: Int, val timestamp: Long) {
        val beaconDetected: Boolean = id != -1

        companion object {
            fun fromBeacon(beacon: Beacon, seenAt: Long) = BeaconObservation(
                beacon.id2.toInt() * (2 shl 8) + beacon.id3.toInt(), seenAt
            )
        }
    }

    /* StateFlow emitting beacon observations */
    private val _beaconScanResult: MutableStateFlow<BeaconObservation> =
        MutableStateFlow(BeaconObservation(-1, 0))
    val beaconScanResult: StateFlow<BeaconObservation> = _beaconScanResult

    /* Variables for beacon monitoring */
    private val beaconManager = BeaconManager.getInstanceForApplication(context)
    private val region =
        Region(
            "sociobuilding",
            Identifier.parse(context.getString(R.string.beacon_uuid)),
            null,
            null
        )

    /* ranging distance average */
    private lateinit var distanceAccumulateMap: MutableMap<String, Pair<Double, Int>>
    private var currentBeaconId: String? = "-1"

    fun startScan() {

        currentBeaconId = "-1"
        distanceAccumulateMap = mutableMapOf()

        Timber.d("Configuring beacon monitoring ...")
        BeaconManager.setRegionExitPeriod(
            context.resources.getInteger(R.integer.beacon_region_exit_period).toLong()
        )

        beaconManager.foregroundScanPeriod =
            context.resources.getInteger(R.integer.beacon_scan_period_foreground).toLong()
        beaconManager.foregroundBetweenScanPeriod =
            context.resources.getInteger(R.integer.beacon_between_scan_period_foreground).toLong()

        /* background scanning for every 15 minutes */
        beaconManager.backgroundScanPeriod =
            context.resources.getInteger(R.integer.beacon_scan_period_background).toLong()
        beaconManager.backgroundBetweenScanPeriod =
            context.resources.getInteger(R.integer.beacon_between_scan_period_background).toLong()
        beaconManager.beaconParsers
            .add(BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")) // iBeacon Layout
        Timber.d("Configuring beacon monitoring ... Done")

        Timber.d("Enabling background beacon ScanJobs ...")
        beaconManager.addRangeNotifier(this)
        beaconManager.startRangingBeacons(region)
        Timber.d("Enabling background beacon ScanJobs ... Done")

        /* Fix the rare case of: can't Register GATT client, MAX client reached: 32 */
        /* This might mean that 32 apps on the phone have already used all the bluetooth clients,
           but more likely it is caused by an OS-level bug that has exhausted all the client slots prematurely,
           potentially by leaving some orphaned.
           See [Github Issue](https://github.com/AltBeacon/android-beacon-library/issues/694) for more details*/
        val medic = BluetoothMedic.getInstance()
        medic.enablePowerCycleOnFailures(context)
    }

    fun stopScan() {
        Timber.d("Cancelling background beacon ScanJobs ...")
        beaconManager.stopRangingBeacons(region)
        Timber.d("Cancelling background beacon ScanJobs ... Done")
    }

    fun getScanResult(): String? {
        stopScan()
        currentBeaconId = distanceAccumulateMap.minByOrNull { (it.value.first)/(it.value.second) }?.key

        if (currentBeaconId == null) {
            Timber.d("Cannot find any beacon")
            return "-1"
        }

        Timber.d("beacon scanning result: $distanceAccumulateMap")
        Timber.d( "!!!!! Detected beacon id --- $currentBeaconId !!!!!")

        return currentBeaconId
    }

    override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, region: Region?) {

        if (beacons == null) return

        Timber.d("Scanning BLE beacon in region...")

        Timber.d("ranging: num_beacons_in_region=${beacons.size}")
        val timestamp = System.currentTimeMillis()
        if (beacons.isEmpty()) {
            _beaconScanResult.value = BeaconObservation(-1, timestamp)
        } else {
            val rangedBeaconsSorted = beacons.sortedBy { it.distance }
            _beaconScanResult.value =
                BeaconObservation.fromBeacon(rangedBeaconsSorted.first(), timestamp)

            val distanceMap = rangedBeaconsSorted.associateBy ({it.id3.toString()}, {Pair(it.distance, 1)})
            distanceAccumulateMap = (distanceAccumulateMap.asSequence() + distanceMap.asSequence())
                .distinct()
                .groupBy ({it.key}, {it.value})
                .mapValues { (_, values) ->
                    values.reduce { t, p -> Pair(p.first + t.first, p.second + t.second)}
                } as MutableMap<String, Pair<Double, Int>>

            for ((idx, beacon) in rangedBeaconsSorted.withIndex()) {
                Timber.d("($idx) major=${beacon.id2} minor=${beacon.id3} dist=${"%.2f".format(beacon.distance)}m")
            }
        }
    }
}
