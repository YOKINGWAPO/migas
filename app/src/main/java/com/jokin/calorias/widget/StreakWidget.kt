package com.jokin.calorias.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jokin.calorias.CaloriasApp
import com.jokin.calorias.MainActivity
import com.jokin.calorias.R
import com.jokin.calorias.domain.RewardPicker
import com.jokin.calorias.domain.StreakCalculator
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.mascot.mascotBitmap
import com.jokin.calorias.ui.mascot.poseFor
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Home-screen widget: current streak and a one-tap shortcut to the scanner. */
class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as CaloriasApp).container
        val today = LocalDate.now().toEpochDay()
        val days = c.log.loggedDays()
        val streak = StreakCalculator.compute(days.toSet(), today)
        val reward = RewardPicker.forDay(streak.current, today)
        val mood = StreakCalculator.petMood(days.filter { it <= today }.maxOrNull(), today, LocalTime.now().hour)
        val px = (MASCOT_DP * context.resources.displayMetrics.density).roundToInt()
        val mascot = mascotBitmap(c.settings.current().mascot, poseFor(mood, reward, cheering = false), reward, px)
        val scan = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SCAN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val mascotDescription = context.getString(reward?.labelRes() ?: R.string.today_mascot)
        val streakLabel = context.resources.getQuantityString(
            if (streak.loggedToday) R.plurals.today_streak_days else R.plurals.today_widget_log_today,
            streak.current,
        )
        val scanLabel = context.getString(R.string.today_scan)

        provideContent {
            GlanceTheme {
                Row(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        ImageProvider(mascot), contentDescription = mascotDescription,
                        modifier = GlanceModifier.size(MASCOT_DP.dp).clickable(actionStartActivity(open)),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(open))) {
                        Text(
                            "${streak.current}",
                            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                        )
                        Text(
                            streakLabel,
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        scanLabel,
                        modifier = GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(16.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp).clickable(actionStartActivity(scan)),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onPrimary),
                    )
                }
            }
        }
    }
}

private const val MASCOT_DP = 52

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
