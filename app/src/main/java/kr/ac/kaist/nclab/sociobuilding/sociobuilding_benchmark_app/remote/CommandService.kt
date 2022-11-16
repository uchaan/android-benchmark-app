package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.remote

import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.alarm.AlarmReceiver
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.EvidenceCollectWorker
import timber.log.Timber

class CommandService : FirebaseMessagingService() {

    private val workManager = WorkManager.getInstance(this)

    override fun onMessageReceived(msg: RemoteMessage) {
        Timber.d("Message from: ${msg.from}")

        // Check if message contains a data payload
        if (msg.data.isNotEmpty()) {
            Timber.d("Message data payload: ${msg.data}")

            // actuator 동작메시지 -> 수집
            if (msg.data["command"] == "start") {
                if (msg.data["duration_ms"] != "30000") {
                    App.duration_ms = msg.data["duration_ms"]?.toInt()
                }
                Timber.d("cancelling existing Works (i.e. IndoorWorker) ...")

                if (App.onRecord.get())
                    return

                if (msg.data["timestamp"] == null)
                    Timber.e("wrong firebase data payload. missing timestamp")

                if (msg.data["actuator"] == null)
                    Timber.e("wrong firebase data payload. missing actuator")

                if (msg.data["doorstate_1"] == null)
                    Timber.e("wrong firebase data payload. missing doorstate-1")

                if (msg.data["doorstate_2"] == null)
                    Timber.e("wrong firebase data payload. missing doorstate-2")

                App.beaconListener.stopScan()
                AlarmReceiver.cancelAlarm(applicationContext)
                workManager.cancelAllWork() // cancel all existing works

                Timber.d("start Evidence Collect Work ...")
                startWork(msg.data["timestamp"].toString(), msg.data["actuator"].toString(), msg.data["doorstate_1"].toBoolean(), msg.data["doorstate_2"].toBoolean())
                // restart periodic indoor scanning job
            }

            if (msg.data["command"] == "stop") {
                Timber.d("cancelling existing Works ...")
                workManager.cancelAllWork() // cancel all existing works.
                App.beaconListener.stopScan()
            }
        }

        // Check if message contains a notification payload.
        msg.notification?.let {
            Timber.d("Message Notification Body: ${it.body}")
        }
    }

//    Firebase

    private fun startWork(timestamp: String, actuator: String, doorState1: Boolean, doorState2: Boolean) {
        val requestStatesData: Data = workDataOf(
            "TIMESTAMP" to timestamp,
            "ACTUATOR" to actuator,
            "DOORSTATE_1" to doorState1,
            "DOORSTATE_2" to doorState2
        )

        /* create a WorkRequest */
        val workRequest = OneTimeWorkRequestBuilder<EvidenceCollectWorker>()
            .setInputData(requestStatesData)
            .build()

        /* before the end of doWork(), it will register itself again with Initial Delay*/
        Timber.d("start initial indoor work...")
        workManager
            .enqueue(workRequest)
    }

    override fun onNewToken(token: String) {
        App.token = token
        Timber.d("Refreshed token: $token")
        // if app server exist, send the updated token to app server
    }
}