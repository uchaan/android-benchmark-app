package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.AppConfig
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.MainActivity
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.R
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.app.App
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.audio.RecordManager
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.Config
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.ftp.FtpUploader
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.wifi.WifiScanner
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class IndoorWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams), RecordManager.RecordListener {

    private val recordManager = RecordManager(this)
    private val context = appContext
    private var workManager = WorkManager.getInstance(applicationContext)
    private val ftp = FtpUploader(
        Config.FTP_HOSTNAME,
        Config.FTP_PORT,
        Config.FTP_USERNAME,
        Config.FTP_PASSWORD
    )
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    /* [start_doze_mode_check] */
    private val dateTime: String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS")
            .format(Date(Date().time + MainActivity.timediff))
    /* [end_doze_mode_check]*/

    override suspend fun doWork(): Result {

        App.addIndoorWorkCount()
        val workCount = App.indoorWorkCount

        setForeground(createForegroundInfo("[work $workCount] start new indoor work..."))

        /* [start_doze_mode_check] */
        val success = ftp.createNewDirectory("checkpoint_${workCount}_${dateTime}")
        Timber.d("successfully create directory? -> $success")
        /* [end_doze_mode_check] */

        Timber.d("Enforcing Garbage Collecting ... ")
        Runtime.getRuntime().gc()

        /* Beacon Scanning */
        App.beaconListener.startScan()

        /* Wifi Scanning */
        Timber.d("[work $workCount] wifi scanning ...")

        val scanner = WifiScanner(applicationContext)
        scanner.startScan()

        var subscriptionJob: Job? = null
         subscriptionJob = CoroutineScope(Dispatchers.Main).launch {
            val reportFlow = scanner.subscribeScanReport()
            reportFlow.collect { report->
                if (report != null) {
                    Timber.d("[work $workCount] Wifi 스캔 성공.")
                } else {
                    Timber.d("[work $workCount] Wifi 스캔 실패. (예상 사유: 속도 한도초과)")
                }
                subscriptionJob?.cancel()
            }
        }

        /* Background sound Recording */
        Timber.d("[work $workCount] audio Recording ...")

        if (recordManager.startRecording()) {
            App.onRecord.set(true)

            delay(AppConfig.INDOOR_WORK_RECORDING_PERIOD_MS.toLong())
            recordManager.stopRecording()

            App.onRecord.set(false)
        } else {
            Timber.e("[work $workCount] Failed to initialize audioRecord object")
        }

        App.beaconListener.getScanResult()

        Timber.d("!!!!!!!! [work $workCount] building another work request ... !!!!!!!!")

        val nextRequest =
            OneTimeWorkRequestBuilder<IndoorWorker>()
                .setInitialDelay(
                    AppConfig.INDOOR_WORK_PERIOD.toLong(),
                    TimeUnit.SECONDS
                )
                .build()

        workManager
            .enqueue(nextRequest)

        return Result.success()
    }

    /* 5초짜리 background noise sound */
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
            .setPriority(Notification.PRIORITY_MAX)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()

        return ForegroundInfo(id.toInt(), notification, FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(id: String) {
        // Create a Notification channel
        val notificationChannel = NotificationChannel(
            id,
            "worker_notification",
            IMPORTANCE_HIGH
        )

        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        private const val ERROR_RECORDER_NULL = -11
    }
}