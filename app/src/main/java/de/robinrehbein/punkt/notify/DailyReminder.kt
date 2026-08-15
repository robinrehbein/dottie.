package de.robinrehbein.punkt.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.robinrehbein.punkt.MainActivity
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.ui.data.AndroidKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Tägliche Daily-Challenge-Erinnerung — komplett lokal, ohne jeden
 * Server-Kontakt: Ein WorkManager-Job feuert einmal am Tag gegen 18 Uhr
 * und zeigt nur dann eine Notification, wenn die heutige Daily noch
 * nicht gespielt wurde. Opt-in über den Schalter auf dem Startscreen;
 * ab Android 13 zusätzlich hinter der Notification-Permission.
 */
object DailyReminder {

    private const val WORK_NAME = "daily-reminder"
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    /** Uhrzeit der Erinnerung — abends, wenn der Tag noch zu retten ist. */
    private val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)

    /** Ab Android 13 ist POST_NOTIFICATIONS eine Runtime-Permission. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    /** Plant die tägliche Prüfung; idempotent (KEEP bei bestehendem Job). */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(REMINDER_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    internal fun show(context: Context, streak: Int) {
        if (needsPermission(context)) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when {
            streak == 1 -> context.getString(R.string.notif_text_streak_one)
            streak > 1 -> context.getString(R.string.notif_text_streak, streak)
            else -> context.getString(R.string.notif_text)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission zwischenzeitlich entzogen — dann eben still.
        }
    }
}

/** Prüft einmal täglich, ob eine Erinnerung fällig ist. */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val store = GameStore(AndroidKeyValueStore(applicationContext))
        if (!store.reminderEnabled) return Result.success()

        val today = LocalDate.now().toEpochDay()
        // Heute schon gespielt? Dann gibt es nichts zu erinnern.
        if (store.dailyDay == today) return Result.success()

        DailyReminder.show(applicationContext, store.dailyStreakPreviewFor(today))
        return Result.success()
    }
}
