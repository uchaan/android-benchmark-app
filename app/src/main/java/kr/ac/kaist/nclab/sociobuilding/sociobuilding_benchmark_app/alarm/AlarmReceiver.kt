package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.AppConfig
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.MainViewModel
import kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.worker.ForecastingWorker
import timber.log.Timber

class AlarmReceiver: BroadcastReceiver() {

    companion object {
        fun scheduleNext(context: Context, triggerAtMillis: Int) {
            Timber.d("Next task will start after ${triggerAtMillis/1000} seconds ...")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = getPendingIntent(context)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + triggerAtMillis, pendingIntent)
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = getPendingIntent(context)
            alarmManager.cancel(pendingIntent)
        }

        private fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        }
    }

    lateinit var alarmManager: AlarmManager
    lateinit var workManager: WorkManager
    lateinit var powerManager: PowerManager

    override fun onReceive(context: Context, intent: Intent?) {
        scheduleNext(context, AppConfig.INDOOR_WORK_PERIOD_MS)
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        workManager = WorkManager.getInstance(context)

        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SocioButton:benchmark")
        wakeLock.acquire(2 * 60 * 1000L)

        Timber.d("!!!!!! start new alarm task ... ${System.currentTimeMillis()} !!!!!!")

        val forecastingWorkRequest =
            OneTimeWorkRequestBuilder<ForecastingWorker>()
                .build()

        /* before the end of doWork(), it will register itself again with Initial Delay*/
        Timber.d("!!!!!!!! start Forecasting work... !!!!!!!!")
        workManager
            .enqueueUniqueWork(
                MainViewModel.INDOOR_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                forecastingWorkRequest
            )

        wakeLock.release()
    }

}