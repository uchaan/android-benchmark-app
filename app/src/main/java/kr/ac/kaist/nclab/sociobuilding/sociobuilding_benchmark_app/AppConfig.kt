package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app

object AppConfig {
    var CACHED = true
    const val INDOOR_WORK_PERIOD = 65 // 60s
    const val INDOOR_WORK_PERIOD_MS = 65000 // 65s
    const val INDOOR_WORK_INITIAL_DELAY = 10 // 10s
    const val INDOOR_WORK_INITIAL_DELAY_MS = 10000 // 10s
    const val INDOOR_WORK_RECORDING_PERIOD_MS = 5000 // 5s
    const val OUTDOOR_WORK_PERIOD = 15 // 15min
    const val EVIDENCE_COLLECT_DEFAULT_RECORDING_PERIOD_MS = 30000 // 30s
}