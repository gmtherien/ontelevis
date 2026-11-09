package app.ontelevis.core.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.ontelevis.core.data.LaunchRequests
import app.ontelevis.core.data.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Reminders that go off while the app is on screen: shown inside the app (TVs hide most notifications). */
@Singleton
class ReminderCenter
    @Inject
    constructor() {
        /** Set by the activity: true between onStart and onStop. */
        @Volatile var foreground: Boolean = false

        private val _due = MutableSharedFlow<Reminder>(extraBufferCapacity = 4)
        val due: SharedFlow<Reminder> = _due.asSharedFlow()

        internal fun show(reminder: Reminder): Boolean = foreground && _due.tryEmit(reminder)
    }

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var center: ReminderCenter

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        val reminder =
            Reminder(
                channelId = intent.getStringExtra(EXTRA_CHANNEL) ?: return,
                start = intent.getLongExtra(EXTRA_START, 0),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty(),
            )
        val pending = goAsync()
        scope.launch {
            try {
                reminders.fired(reminder.channelId, reminder.start)
                if (!center.show(reminder)) notify(context, reminder)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(
        context: Context,
        reminder: Reminder,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val open =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                data = LaunchRequests.channelUri(reminder.channelId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } ?: return
        val watch =
            PendingIntent.getActivity(
                context,
                reminder.channelId.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(reminder.title)
                .setContentText(
                    context.getString(
                        R.string.reminder_text,
                        DateFormat.getTimeFormat(context).format(Date(reminder.start)),
                        reminder.channelName,
                    ),
                ).setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(watch)
                .addAction(0, context.getString(R.string.reminder_watch), watch)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()
        @Suppress("MissingPermission") // Checked by areNotificationsEnabled (Android 13+: the permission).
        manager.notify(reminder.channelId.hashCode() xor reminder.start.hashCode(), notification)
    }

    companion object {
        const val ACTION = "app.ontelevis.REMINDER"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_START = "start"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val CHANNEL_ID = "reminders"
        private const val TIMEOUT_MS = 30 * 60_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Alarms do not survive a reboot: the reminders are scheduled again. */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: ReminderRepository

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reminders.scheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
