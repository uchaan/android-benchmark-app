package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.schema

data class RequestStates(val timestamp: String?, val actuator: String, val doorState1: Boolean, val doorState2: Boolean) {
}
