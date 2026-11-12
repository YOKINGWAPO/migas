package com.jokin.calorias

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jokin.calorias.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CaloriasApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; used for work that must not die with a screen. */
    val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL, getString(R.string.today_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = getString(R.string.today_reminder_channel_description)
            }
        )
        appScope.launch {
            container.seeder.ensureSeeded()
            ReminderScheduler.apply(this@CaloriasApp, container.settings.current())
        }
    }

    companion object {
        const val REMINDER_CHANNEL = "reminders"
    }
}
