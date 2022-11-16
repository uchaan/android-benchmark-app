package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema

data class ScanReport (val timestamp: Long, val entries: List<ScanEntry>) {
    // in Millis
}
