package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema

data class ScanEntry (
    val rssi: Int,
    val ssid: String,   // network name
    val bssid: String,  // address of AP
    val frequency: Int, // The primary 20 MHz frequency (in MHz) of the channel over which the client is communicating with the access point.
    val timestamp: Long // timestamp in microseconds (since boot) when this result was last seen.
)