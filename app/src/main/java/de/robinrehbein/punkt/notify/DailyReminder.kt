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
import androidx.work.workDataOf
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
 * nicht gespielt wurde. Opt-in über den Schalter in den Einstellungen
 * (und seit v2.30 über die einmalige Frage nach dem ersten Tageslauf);
 * ab Android 13 zusätzlich hinter der Notification-Permission.
 *
 * Seit v2.30 gibt es einen zweiten Blick um 21 Uhr: Steht dann eine
 * Serie von mindestens [RISK_MIN_STREAK] Tagen auf dem Spiel und ist die
 * Daily immer noch offen, kommt „SERIE IN GEFAHR". Der späte Termin ist
 * Absicht — um 18 Uhr ist der Tag noch zu retten, ohne dass es drängt; um
 * 21 Uhr ist es die letzte Gelegenheit. Beide landen unter derselben
 * Notification-ID: Die zweite ersetzt die erste, statt sich zu stapeln.
 */
object DailyReminder {

    private const val WORK_NAME = "daily-reminder"
    private const val RISK_WORK_NAME = "daily-reminder-risk"
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    /** Uhrzeit der Erinnerung — abends, wenn der Tag noch zu retten ist. */
    private val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)

    /** Die letzte Gelegenheit des Tages (ab v2.30). */
    private val RISK_TIME: LocalTime = LocalTime.of(21, 0)

    /** Ab dieser Serie lohnt die zweite Erinnerung. */
    const val RISK_MIN_STREAK = 3

    /** Worker-Eingabe: welche der beiden Erinnerungen. */
    internal const val KEY_KIND = "kind"
    internal const val KIND_DAILY = "daily"
    internal const val KIND_RISK = "risk"

    /**
     * Intent-Extra der geöffneten App: über welche Erinnerung sie kam.
     * Liest MainActivity und reicht es für die Messung weiter.
     */
    const val EXTRA_FROM_REMINDER = "from_reminder"

    /** Ab Android 13 ist POST_NOTIFICATIONS eine Runtime-Permission. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

    /** Plant beide täglichen Prüfungen; idempotent (KEEP bei bestehendem Job). */
    fun schedule(context: Context) {
        enqueue(context, WORK_NAME, REMINDER_TIME, KIND_DAILY)
        enqueue(context, RISK_WORK_NAME, RISK_TIME, KIND_RISK)
    }

    private fun enqueue(context: Context, name: String, time: LocalTime, kind: String) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_KIND to kind))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            name, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(WORK_NAME)
        work.cancelUniqueWork(RISK_WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    internal fun show(context: Context, streak: Int, kind: String) {
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
            // Je Art ein eigener Request-Code: Sonst überschriebe der
            // zweite PendingIntent das Extra des ersten.
            if (kind == KIND_RISK) 1 else 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FROM_REMINDER, kind)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String
        if (kind == KIND_RISK) {
            title = context.getString(R.string.notif_risk_title)
            text = context.getString(R.string.notif_risk_text, streak)
        } else {
            title = context.getString(R.string.notif_title)
            text = when {
                streak == 1 -> context.getString(R.string.notif_text_streak_one)
                streak > 1 -> context.getString(R.string.notif_text_streak, streak)
                else -> context.getString(R.string.notif_text)
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
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

    /**
     * Soll heute erinnert werden, und womit? null = nein. Reine
     * Entscheidung, damit sie ohne WorkManager prüfbar bleibt.
     *
     * - Daily heute schon gespielt: nie.
     * - 18 Uhr ([KIND_DAILY]): immer, mit der Serie im Text.
     * - 21 Uhr ([KIND_RISK]): nur, wenn eine Serie ab [RISK_MIN_STREAK]
     *   Tagen heute noch lebt — für eine Serie von einem Tag wäre „IN
     *   GEFAHR" ein großes Wort, und eine schon gerissene ist nicht mehr
     *   in Gefahr.
     */
    fun decide(kind: String, playedToday: Boolean, streakAlive: Int): String? = when {
        playedToday -> null
        kind == KIND_RISK -> if (streakAlive >= RISK_MIN_STREAK) KIND_RISK else null
        else -> KIND_DAILY
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
        val kind = inputData.getString(DailyReminder.KEY_KIND) ?: DailyReminder.KIND_DAILY
        val streak = store.dailyStreakPreviewFor(today)
        val show = DailyReminder.decide(
            kind = kind,
            playedToday = store.dailyDay == today,
            streakAlive = streak
        ) ?: return Result.success()
        DailyReminder.show(applicationContext, streak, show)
        return Result.success()
    }
}
