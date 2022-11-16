package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.AppConfig
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.MainActivity
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.R
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.annoyance.AnnoyanceForecaster
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.audio.RecordManager
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.Config
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.FtpDownloader
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.FtpUploader
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.WifiScanner
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.schema.ScanReport
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ForecastingWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams), RecordManager.RecordListener {

    private val recordManager = RecordManager(this)
    private val context = appContext
    private var workManager = WorkManager.getInstance(applicationContext)
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
    private val ftp = FtpUploader(
        Config.FTP_HOSTNAME,
        Config.FTP_PORT,
        Config.FTP_USERNAME,
        Config.FTP_PASSWORD
    )

    /* [start_doze_mode_check] */
//    private val dateTime: String =
//        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS")
//            .format(Date(Date().time + MainActivity.timediff))
    /* [end_doze_mode_check]*/

    override suspend fun doWork(): Result {

        App.addForecastingWorkCount()
        val workCount = App.forecastingWorkCount

        setForeground(createForegroundInfo("[work $workCount] start new forecasting work..."))

        /* [start_doze_mode_check] */
//        val success = ftp.createNewDirectory("checkpoint_${workCount}_${dateTime}")
//        Timber.d("successfully create directory? -> $success")
        /* [end_doze_mode_check] */

        Timber.d("Enforcing Garbage Collecting ... ")
        Runtime.getRuntime().gc()

        /* Beacon Scanning */
        App.beaconListener.startScan()

        /* Wifi Scanning */
        Timber.d("[work $workCount] start wifi scanning ...")

        val scanner = WifiScanner(applicationContext)
        scanner.startScan()

        var wifiReport: ScanReport? = null

        var subscriptionJob: Job? = null
        subscriptionJob = CoroutineScope(Dispatchers.Main).launch {
            val reportFlow = scanner.subscribeScanReport()
            reportFlow.collect { report->
                wifiReport = report
                if (report != null) {
                    Timber.d("[work $workCount] Wifi 스캔 성공.")
                } else {
                    Timber.d("[work $workCount] Wifi 스캔 실패. (예상 사유: 속도 한도초과)")
                }
                subscriptionJob?.cancel()
            }
        }

        /* Background noise recording */
        Timber.d("[work $workCount] background noise Recording ...")

        if (recordManager.startRecording()) {
            App.onRecord.set(true)

            delay(AppConfig.INDOOR_WORK_RECORDING_PERIOD_MS.toLong())
            recordManager.stopRecording()

            App.onRecord.set(false)
        } else {
            Timber.e("[work $workCount] Failed to initialize audioRecord object")
        }
        Timber.d("[work $workCount] background noise Recording ... Done")

        /* end Beacon Scanning */
        val beaconId = App.beaconListener.getScanResult()

        /* [start_annoyance_forecast] */
//        annoyanceForecaster.annoyanceForecasting()
        if (App.prevRecordBuffer != null) {
            val annoyanceForecaster = AnnoyanceForecaster(context)
            val diffResult = annoyanceForecaster.annoyanceForecasting(App.prevRecordBuffer!!, beaconId, wifiReport)

            Timber.d("diff Result: $diffResult")
        }

        /* [end_annoyance_forecast] */

        Timber.i("[work $workCount] Successfully done.")

        return Result.success()
    }

    override fun didRecordAudio(buffer: ByteArray) {
        if (buffer.isNotEmpty()) {
            App.prevRecordBuffer = buffer
            Timber.d("Successfully done Background noise recording")
        } else {
            Timber.d("Background noise buffer is empty ... ")
        }
    }

    /* notification settings for Foreground service  */
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val id = context.getString(R.string.indoor_notification_channel_id)
        val title = context.getString(R.string.indoor_notification_title)
        val cancel = context.getString(R.string.indoor_cancel_work)

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
            .setPriority(Notification.PRIORITY_LOW)
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
            NotificationManager.IMPORTANCE_LOW
        )

        notificationManager.createNotificationChannel(notificationChannel)
    }


}