package app.ontelevis.core.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.ontelevis.core.data.db.ReminderDao
import app.ontelevis.core.data.db.ReminderEntity
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.parseChannelName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A programme to be reminded of, as the guide and the notification need it. */
data class Reminder(
    val channelId: String,
    val start: Long,
    val title: String,
    val channelName: String,
)

/**
 * Programme reminders: saved in the database, and an alarm a minute before each start (inexact by up to
 * a minute: exact alarms need a special permission). Alarms are lost at reboot; [scheduleAll] runs
 * again at boot and at each app start.
 */
@Singleton
class ReminderRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: ReminderDao,
    ) {
        /** Programme starts with a reminder, by channel id. */
        fun observe(): Flow<Map<String, Set<Long>>> =
            dao.observeAll().map { list -> list.groupBy({ it.channelId }, { it.start }).mapValues { it.value.toSet() } }

        /** Sets or removes the reminder; true when it is now set. */
        suspend fun toggle(
            channel: Channel,
            programme: Programme,
        ): Boolean {
            if (dao.get(channel.id, programme.startMillis) != null) {
                dao.delete(channel.id, programme.startMillis)
                cancel(channel.id, programme.startMillis)
                return false
            }
            val entity =
                ReminderEntity(
                    channelId = channel.id,
                    start = programme.startMillis,
                    stop = programme.stopMillis,
                    title = programme.title,
                    channelName = parseChannelName(channel.name).title,
                )
            dao.put(entity)
            schedule(entity)
            return true
        }

        suspend fun scheduleAll(now: Long = System.currentTimeMillis()) {
            dao.deleteOlderThan(now - STALE_AFTER_MS)
            dao.upcoming(now - LEAD_MS).forEach(::schedule)
        }

        /** The reminder went off: it is forgotten (the guide's bell goes). */
        suspend fun fired(
            channelId: String,
            start: Long,
        ) = dao.delete(channelId, start)

        private fun schedule(reminder: ReminderEntity) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val now = System.currentTimeMillis()
            val at = (reminder.start - LEAD_MS).coerceAtLeast(now)
            val intent = pendingIntent(reminder.toModel())
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                    alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, intent)
                }

                alarms.canScheduleExactAlarms() -> {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                }

                // Android 12+ without exact alarms: windows last at least 10 minutes. The window
                // ends a minute before the start, so the reminder is never late (it can be early: it
                // tells the start time).
                else -> {
                    val from = (at - MIN_WINDOW_MS).coerceAtLeast(now)
                    alarms.setWindow(AlarmManager.RTC_WAKEUP, from, (at - from).coerceAtLeast(WINDOW_MS), intent)
                }
            }
        }

        private fun cancel(
            channelId: String,
            start: Long,
        ) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.cancel(pendingIntent(Reminder(channelId, start, "", "")))
        }

        private fun pendingIntent(reminder: Reminder): PendingIntent {
            val intent =
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION)
                    // The data makes each reminder's alarm distinct (extras are not compared).
                    .setData(Uri.parse("ontelevis-reminder://${reminder.start}/${reminder.channelId.hashCode()}"))
                    .putExtra(ReminderReceiver.EXTRA_CHANNEL, reminder.channelId)
                    .putExtra(ReminderReceiver.EXTRA_START, reminder.start)
                    .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
                    .putExtra(ReminderReceiver.EXTRA_CHANNEL_NAME, reminder.channelName)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ReminderEntity.toModel() = Reminder(channelId, start, title, channelName)

        private companion object {
            /** The reminder comes a minute before the start, so the channel is on in time. */
            const val LEAD_MS = 60_000L
            const val WINDOW_MS = 60_000L
            const val MIN_WINDOW_MS = 10 * 60_000L
            const val STALE_AFTER_MS = 6 * 60 * 60_000L
        }
    }
