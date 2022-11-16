package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.schema

import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema.ScanReport

data class TotalScanReport(val requestStates: RequestStates, val beaconId: String?, val wifiReport: ScanReport?) {
}
