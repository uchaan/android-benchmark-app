package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.AppConfig
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.MainActivity
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.R
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.MainViewModel
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.alarm.AlarmReceiver
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.audio.RecordManager
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.Config
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.FtpUploader
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.WifiScanner
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema.ScanReport
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.schema.RequestStates
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.schema.TotalScanReport
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Singleton
class EvidenceCollectWorker(private val context: Context, workerParams: WorkerParameters):
    CoroutineWorker(context, workerParams), RecordManager.RecordListener {

    private val recordManager = RecordManager(this)
    private val ftp = FtpUploader(
        Config.FTP_HOSTNAME,
        Config.FTP_PORT,
        Config.FTP_USERNAME,
        Config.FTP_PASSWORD
    )
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
    private val sensorManager =
        applicationContext.getSystemService(Context.SENSOR_SERVICE)
                as SensorManager
    private lateinit var accelWriter: FileWriter
    private lateinit var proximityWriter: FileWriter

    private val dateTime: String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS")
            .format(Date(Date().time + MainActivity.timediff))

    private val gson = Gson()

    private var requestStates: RequestStates? = null

    override suspend fun doWork(): Result {

        Timber.d("Enforcing Garbage Collecting ... ")
        Runtime.getRuntime().gc()

        setForeground(createForegroundInfo("[Evidence Collector] start foreground service..."))

        requestStates = RequestStates(
            inputData.getString("TIMESTAMP").toString(),
            inputData.getString("ACTUATOR").toString(),
            inputData.getBoolean("DOORSTATE_1", false),
            inputData.getBoolean("DOORSTATE_2", false)
        )

        ftp.createNewDirectory(dateTime)

        val accelFileName = "accel_${dateTime}.tsv"
        val accelFile = File(applicationContext.filesDir, accelFileName)
//        val accelFile = File(applicationContext.getExternalFilesDir())
        accelWriter = FileWriter(accelFile, true)
        accelWriter.write("timestamp\tx\ty\tz\n")

        val proximityFileName = "proximity_${dateTime}.tsv"
        val proximityFile = File(applicationContext.filesDir, proximityFileName)
        proximityWriter = FileWriter(proximityFile, true)
        proximityWriter.write("timestamp\tdistance\n")

        /* Beacon Scanning */
        App.beaconListener.startScan()

        /* Wifi Scanning */
        Timber.d("[Evidence Collector] wifi scanning ...")

        val scanner = WifiScanner(applicationContext)
        scanner.startScan()

        var wifiReport:ScanReport? = null

        var subscriptionJob: Job? = null
        subscriptionJob = CoroutineScope(Dispatchers.Main).launch {
            val reportFlow = scanner.subscribeScanReport()
            reportFlow.collect { report->
                wifiReport = report
                if (report != null) {
                    Timber.d("[Evidence Collector] Wifi 스캔 성공.")
                } else {
                    Timber.d("[Evidence Collector] Wifi 스캔 실패. (예상 사유: 속도 한도초과)")
                }
                subscriptionJob?.cancel()
            }
        }


        /* Accelerometer monitoring */
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(accelerometerEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        /* Proximity monitoring */
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        sensorManager.registerListener(proximitySensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)

        /* Actuator sound Recording */
        Timber.d("[Evidence Collector] audio Recording for ${App.duration_ms} ms ...")

        if (recordManager.startRecording()) {
            App.onRecord.set(true)

            App.duration_ms?.toLong()?.let { delay(it) }
            recordManager.stopRecording()

            App.onRecord.set(false)
        } else {
            Timber.e("[Evidence Collector] Failed to initialize audioRecord object")
        }

        sensorManager.unregisterListener(accelerometerEventListener)
        sensorManager.unregisterListener(proximitySensorEventListener)

        val beaconId = App.beaconListener.getScanResult()

        accelWriter.close()
        var success = ftp.upload(accelFile, dateTime)
        Timber.d("accelerometer value file upload successful? -> $success")
        accelFile.delete()

        proximityWriter.close()
        success = ftp.upload(proximityFile, dateTime)
        Timber.d("proximity value file upload successful? -> $success")
        proximityFile.delete()

        success = uploadScanReport(requestStates!!, beaconId, wifiReport)
        Timber.d("wifi report file upload successful? -> $success")

//        /* start again indoor periodic scanning */
//        /* create a OneTime WorkRequest */
//        val indoorWorkRequest =
//            OneTimeWorkRequestBuilder<IndoorWorker>()
//                .setInitialDelay(
//                    context.resources.getInteger(R.integer.indoor_work_initial_delay).toLong(),
//                    TimeUnit.SECONDS
//                )
//                .build()
//
//        /* before the end of doWork(), it will register itself again with Initial Delay*/
//        Timber.d("start again indoor work...")
//        WorkManager.getInstance(context)
//            .enqueueUniqueWork(
//                MainViewModel.INDOOR_WORK_NAME,
//                ExistingWorkPolicy.REPLACE,
//                indoorWorkRequest
//            )

        // start annoyance forecasting task
        AlarmReceiver.scheduleNext(applicationContext, AppConfig.INDOOR_WORK_INITIAL_DELAY_MS)

        return Result.success()
    }

    /* 30초짜리 actuator sound & 5초짜리 background noise sound */
    override fun didRecordAudio(buffer: ByteArray) {
        val actuatorFileName = "actuator_${dateTime}.pcm"
        val backgroundNoiseFileName = "background_${dateTime}.pcm"

        CoroutineScope(Dispatchers.IO).launch {
            // FTP upload
            // 1. 30s actuator sound
            var success = ftp.upload(buffer.inputStream(), actuatorFileName, dateTime)
            Timber.d("actuator sound record upload successful? -> $success")
            //            Timber.d("audio recording successful -> true")

            // 2. 5s background noise
            try {
                success = ftp.upload(App.prevRecordBuffer!!.inputStream(), backgroundNoiseFileName, dateTime)
                Timber.d("background noise record (5s) upload successful? -> $success")
            } catch (e: NullPointerException) {
                e.printStackTrace()
                Timber.d("there is no recent background noise record!!")
            }
        }
    }

    private fun uploadScanReport(requestStates: RequestStates, beaconId: String?, report: ScanReport?):Boolean {
        val totalReport = TotalScanReport(requestStates, beaconId, report)
        val json = gson.toJson(totalReport, TotalScanReport::class.java)
        return ftp.upload(json, "scan_report_${dateTime}.json", dateTime)
    }

    private var accelerometerEventListener = object: SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val ts = event.timestamp

//            Timber.d("Accel value = x: $x y: $y z: $z timestamp: $ts")

            accelWriter.write("${ts}\t${x}\t${y}\t${z}\n")
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }
    }

    private var proximitySensorEventListener = object: SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val distance = event.values[0]
            val ts = event.timestamp

//            Timber.d("Proximity value = distance : $distance timestamp: $ts")

            proximityWriter.write("${ts}\t${distance}\n")
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        }
    }

    /* notification settings for Foreground service  */
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val id = context.getString(R.string.evidence_collect_notification_channel_id)
        val title = context.getString(R.string.evidence_collect_notification_title)
        val cancel = context.getString(R.string.evidence_collect_cancel_work)

        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(id)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(R.drawable.notification_icon_background)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MAX)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return ForegroundInfo(id.toInt(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(id: String) {
        // Create a Notification channel
        val notificationChannel = NotificationChannel(
            id,
            "worker_notification",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager.createNotificationChannel(notificationChannel)
    }
}