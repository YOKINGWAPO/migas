package com.jokin.calorias.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jokin.calorias.CaloriasApp
import com.jokin.calorias.MainActivity
import com.jokin.calorias.R
import com.jokin.calorias.data.settings.AppSettings
import com.jokin.calorias.widget.StreakWidget
import androidx.glance.appwidget.updateAll
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Daily check at the configured time: a gentle notification if nothing was logged today. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CaloriasApp
        val c = app.container
        StreakWidget().updateAll(applicationContext)
        if (!c.settings.current().remindersEnabled) return Result.success()
        if (c.log.hasEntries(LocalDate.now().toEpochDay())) return Result.success()
        if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CaloriasApp.REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.today_reminder_title))
            .setContentText(applicationContext.getString(R.string.today_reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "daily_reminder"

    fun apply(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.remindersEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = delayUntil(LocalDateTime.now(), LocalTime.of(settings.reminderHour, settings.reminderMinute))
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun delayUntil(now: LocalDateTime, at: LocalTime): Duration {
        var next = now.toLocalDate().atTime(at)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
